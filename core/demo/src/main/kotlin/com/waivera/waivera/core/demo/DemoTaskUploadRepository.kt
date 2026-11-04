package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.PreparedFile
import com.waivera.waivera.core.data.model.RequiredFile
import com.waivera.waivera.core.data.model.TaskUpload
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.data.repository.TaskUploadRepository
import com.waivera.waivera.core.network.di.ApplicationScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Uploads that climb to done on the phone, and one required file that an upload fills. */
@Singleton
class DemoTaskUploadRepository @Inject constructor(@ApplicationScope private val scope: CoroutineScope) :
    TaskUploadRepository {
    private val all = MutableStateFlow<List<TaskUpload>>(emptyList())
    private val filled = MutableStateFlow<Set<Int>>(emptySet())

    override fun uploads(taskId: Int): Flow<List<TaskUpload>> = all.map { list -> list.filter { it.taskId == taskId } }

    override suspend fun requiredFiles(taskId: Int): Result<List<RequiredFile>> = Result.success(
        listOf(
            RequiredFile(REQUIRED_ID, "Signed service plan", typeId = 2, formName = null, removalPending = false),
            RequiredFile(FORM_ID, "Risk screen", typeId = 3, formName = "Risk screen", removalPending = false),
        ).filterNot { it.id in filled.value },
    )

    override suspend fun categories(): Result<List<DocumentCategory>> = Result.success(
        listOf(
            DocumentCategory(CERTIFICATIONS, "Certifications"),
            DocumentCategory(TRAINING, "Training"),
            DocumentCategory(HR_FORMS, "HR forms"),
        ),
    )

    override suspend fun add(taskId: Int, uris: List<String>, target: UploadTarget) {
        uris.forEachIndexed { index, uri ->
            val name = target.requirementName ?: uri.substringAfterLast('/').ifEmpty { "Upload ${index + 1}.jpg" }
            val upload = TaskUpload(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                fileName = name,
                kind = FileKind.of(name, null),
                sizeBytes = DEMO_SIZE,
                requirementId = target.requirementId,
                state = UploadState.Uploading,
                progress = 0f,
            )
            all.update { it + upload }
            scope.launch { climb(upload) }
        }
    }

    private suspend fun climb(upload: TaskUpload) {
        for (step in 1..STEPS) {
            delay(STEP_MILLIS)
            change(upload.id) { it.copy(progress = step / STEPS.toFloat()) }
        }
        upload.requirementId?.let { id -> filled.update { it + id } }
        change(upload.id) { it.copy(state = UploadState.Done, progress = null, savedName = it.fileName) }
    }

    override fun retry(id: String) {
        val upload = all.value.firstOrNull { it.id == id } ?: return
        change(id) { it.copy(state = UploadState.Uploading, problem = null, progress = 0f) }
        scope.launch { climb(upload) }
    }

    override fun dismiss(id: String) = all.update { list -> list.filterNot { it.id == id } }

    override suspend fun prepare(uri: String): Result<PreparedFile> {
        val name = uri.substringAfterLast('/').ifEmpty { "Photo.jpg" }
        return Result.success(
            PreparedFile(uri, name, if (name.endsWith(".pdf")) "application/pdf" else "image/jpeg", DEMO_SIZE),
        )
    }

    override suspend fun sendNow(taskId: Int, file: PreparedFile, template: FileTemplate?): Result<String?> {
        delay(STEP_MILLIS * STEPS)
        return Result.success(template?.name ?: file.name)
    }

    override suspend fun discard(file: PreparedFile) = Unit

    private fun change(id: String, edit: (TaskUpload) -> TaskUpload) =
        all.update { list -> list.map { if (it.id == id) edit(it) else it } }

    private companion object {
        const val CERTIFICATIONS = 1
        const val TRAINING = 2
        const val HR_FORMS = 3
        const val REQUIRED_ID = 901
        const val FORM_ID = 902
        const val DEMO_SIZE = 640_000L
        const val STEPS = 5
        const val STEP_MILLIS = 300L
    }
}
