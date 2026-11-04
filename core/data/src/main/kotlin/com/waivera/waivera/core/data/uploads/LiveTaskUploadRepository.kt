package com.waivera.waivera.core.data.uploads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.PreparedFile
import com.waivera.waivera.core.data.model.RequiredFile
import com.waivera.waivera.core.data.model.TaskUpload
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.data.repository.PickPurpose
import com.waivera.waivera.core.data.repository.TaskUploadRepository
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.di.IoDispatcher
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response

/** Runs [TaskUploadProcessor]; dependencies come through an entry point, like the outbox worker. */
class TaskUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun taskUploadProcessor(): TaskUploadProcessor
    }

    override suspend fun doWork(): Result {
        val processor = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .taskUploadProcessor()
        return if (processor.process()) Result.retry() else Result.success()
    }
}

fun interface TaskUploadScheduler {
    fun schedule()

    /** Sign-out: nothing queued may go up under the next account. */
    fun cancel() = Unit
}

@Singleton
class WorkManagerTaskUploadScheduler @Inject constructor(@ApplicationContext private val context: Context) :
    TaskUploadScheduler {
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<TaskUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // Appending keeps a run that starts after the current one, so a file queued mid-run is sent.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "waivera-task-uploads"
        const val BACKOFF_SECONDS = 10L
    }
}

@Singleton
class LiveTaskUploadRepository @Inject constructor(
    private val api: TaskApi,
    private val store: TaskUploadStore,
    private val stager: UploadStager,
    private val scheduler: TaskUploadScheduler,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) : TaskUploadRepository,
    SignOutCleaner {
    @Volatile
    private var resumed = false
    private val generation = AtomicInteger()

    override fun uploads(taskId: Int): Flow<List<TaskUpload>> = flow {
        resumeOnce()
        emitAll(
            combine(store.all().filterNotNull(), store.progress) { entries, progress ->
                entries.filter { it.taskId == taskId }
                    .sortedBy { it.createdAtMillis }
                    .map { it.toUpload(progress[it.id]) }
            },
        )
    }.distinctUntilChanged().flowOn(io)

    override suspend fun requiredFiles(taskId: Int): Result<List<RequiredFile>> = catchingWaivera {
        apiCall { api.getRequiredAttachments(taskId) }.mapNotNull { dto ->
            val template = dto.templateFile ?: return@mapNotNull null
            val name = template.fileName?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            RequiredFile(
                id = dto.id,
                name = name,
                typeId = template.type?.id ?: UploadTarget.PLAIN_TYPE_ID,
                formName = template.form?.let { form -> form.name?.trim()?.takeIf(String::isNotEmpty) ?: name },
                removalPending = dto.removalRequestPending,
            )
        }
    }

    override suspend fun categories(): Result<List<DocumentCategory>> = catchingWaivera {
        apiCall { api.getDocumentCategories() }.requireData().mapNotNull { dto ->
            dto.documentCategoryName?.trim()?.takeIf(String::isNotEmpty)?.let { DocumentCategory(dto.id, it) }
        }
    }

    /**
     * Copies and queues on the app scope, so leaving the screen mid-copy still queues every file.
     * A sign-out meanwhile ([clear]) moves the generation on and the rest are dropped, not queued.
     */
    override suspend fun add(taskId: Int, uris: List<String>, target: UploadTarget) {
        val started = generation.get()
        scope.launch(io) {
            stageAll(taskId, uris, target, started)
            if (generation.get() == started) scheduler.schedule()
        }.join()
    }

    private suspend fun stageAll(taskId: Int, uris: List<String>, target: UploadTarget, started: Int) {
        uris.take(UploadChecks.MAX_FILES).forEach { uri ->
            val id = UUID.randomUUID().toString()
            val now = clock.millis()
            val entry = stager.stage(uri, store.stagingDirectory).fold(
                onSuccess = { staged ->
                    UploadEntry(
                        id = id,
                        taskId = taskId,
                        localPath = staged.file.path,
                        fileName = staged.fileName,
                        mimeType = staged.mimeType,
                        sizeBytes = staged.file.length(),
                        target = target,
                        createdAtMillis = now,
                    )
                },
                onFailure = { error ->
                    UploadEntry(
                        id = id,
                        taskId = taskId,
                        fileName = (error as? UploadRefused)?.pickedName?.substringAfterLast('/')
                            ?: target.requirementName ?: FALLBACK_NAME,
                        mimeType = "",
                        target = target,
                        state = UploadState.Failed,
                        problem = (error as? UploadRefused)?.problem ?: UploadProblem.Unreadable,
                        createdAtMillis = now,
                    )
                },
            )
            if (!store.putUnless(entry) { generation.get() != started }) {
                entry.localPath?.let { File(it).delete() }
                return
            }
        }
    }

    override fun retry(id: String) {
        scope.launch(io) {
            store.update(id) {
                if (it.state == UploadState.Failed && it.problem?.canRetry == true) {
                    it.copy(state = UploadState.Queued, problem = null)
                } else {
                    it
                }
            }
            scheduler.schedule()
        }
    }

    override fun dismiss(id: String) {
        scope.launch(io) { store.remove(id) }
    }

    override suspend fun prepare(uri: String, purpose: PickPurpose): Result<PreparedFile> = stageOne(
        uri,
        when (purpose) {
            PickPurpose.Activity -> store.activityDirectory
            PickPurpose.Evidence -> store.evidenceDirectory
        },
    )

    /** One sheet of each kind is open at a time; an earlier pick it never sent goes. */
    private suspend fun stageOne(uri: String, directory: File): Result<PreparedFile> = withContext(io) {
        directory.deleteRecursively()
        stager.stage(uri, directory).map {
            PreparedFile(it.file.path, it.fileName, it.mimeType, it.file.length())
        }
    }

    override suspend fun sendNow(taskId: Int, file: PreparedFile, template: FileTemplate?): Result<String?> {
        val name = template?.let { UploadChecks.templateName(it.name, file.mimeType) } ?: file.name
        val target =
            UploadTarget(typeId = template?.typeId ?: UploadTarget.PLAIN_TYPE_ID, categoryId = template?.categoryId)
        return sendStaged(
            file,
            send = { body, size ->
                api.uploadTaskFile(UploadForm.parts(body, name, target, taskId, file.mimeType, size))
            },
            read = { it.fileName?.takeIf(String::isNotBlank) },
        )
    }

    override suspend fun sendEvidence(taskId: Int, file: PreparedFile): Result<Int> = sendStaged(
        file,
        send = { body, size ->
            api.uploadEvidence(UploadForm.evidenceParts(body, file.name, taskId, file.mimeType, size))
        },
        read = { it.attachmentId },
    )

    /** Sends a prepared copy now; the copy goes once the server has it and stays for a retry otherwise. */
    private suspend fun <R : Any, T> sendStaged(
        file: PreparedFile,
        send: suspend (RequestBody, Long) -> Response<R>,
        read: (R) -> T,
    ): Result<T> = withContext(io) {
        val copy = File(file.path).takeIf(File::exists)
            ?: return@withContext Result.failure(UploadRefused(UploadProblem.Unreadable))
        val body = copy.asRequestBody(file.mimeType.toMediaTypeOrNull())
        catchingWaivera { read(apiCall { send(body, copy.length()) }) }.fold(
            onSuccess = { value ->
                copy.delete()
                Result.success(value)
            },
            onFailure = { error ->
                val problem = (error as? WaiveraError)?.let(TaskUploadProcessor::problem) ?: UploadProblem.Failed
                Result.failure(UploadRefused(problem))
            },
        )
    }

    override suspend fun discard(file: PreparedFile) {
        withContext(io) { File(file.path).delete() }
    }

    override suspend fun clear() {
        generation.incrementAndGet()
        scheduler.cancel()
        withContext(io) { store.clear() }
    }

    /** Files queued before the app was closed go up once it is open again. */
    private suspend fun resumeOnce() {
        if (resumed) return
        resumed = true
        if (store.snapshot().any { it.state == UploadState.Queued }) scheduler.schedule()
    }

    private fun UploadEntry.toUpload(fraction: Float?) = TaskUpload(
        id = id,
        taskId = taskId,
        fileName = savedName ?: fileName,
        kind = FileKind.of(fileName, mimeType),
        sizeBytes = sizeBytes,
        requirementId = target.requirementId,
        state = state,
        progress = fraction.takeIf { state == UploadState.Uploading },
        problem = problem,
        savedName = savedName,
    )

    private companion object {
        /** No usable name: the row shows the app's own word for it. */
        const val FALLBACK_NAME = ""
    }
}
