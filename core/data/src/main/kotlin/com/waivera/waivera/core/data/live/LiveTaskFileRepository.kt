package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.network.api.RequestApi
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.network.di.IoDispatcher
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.toWaiveraError
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TaskFileCacheDirectory

/**
 * Task files for the viewer, and for saving or sharing. Each open gets its own file under a random name in the
 * app's private cache; closing the viewer deletes it and sign-out empties the directory. Like chat
 * attachments, a download that finishes after sign-out is thrown away, never kept for the next
 * account. Viewed files stop downloading at [TaskAttachment.MAX_VIEW_BYTES], exported ones at
 * [TaskAttachment.MAX_EXPORT_BYTES].
 */
@Singleton
class LiveTaskFileRepository @Inject constructor(
    private val api: TaskApi,
    private val requests: RequestApi,
    @TaskFileCacheDirectory private val directory: File,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TaskFileRepository,
    SignOutCleaner {
    private val lock = Mutex()
    private val running: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var generation = 0L

    override suspend fun open(taskId: Int, file: TaskAttachment): Result<File> = catchingWaivera {
        if (!file.viewable) throw WaiveraError.Unexpected()
        download(taskId, file, TaskAttachment.MAX_VIEW_BYTES)
    }

    override suspend fun export(taskId: Int, file: TaskAttachment): Result<File> = catchingWaivera {
        download(taskId, file, TaskAttachment.MAX_EXPORT_BYTES)
    }

    override suspend fun certificate(requestId: Int): Result<File> = catchingWaivera {
        download(TaskAttachment.MAX_EXPORT_BYTES) { requests.trainingCertificate(requestId) }
    }

    private suspend fun download(taskId: Int, file: TaskAttachment, limit: Long): File = download(limit) {
        if (file.employeeFile) api.downloadEmployeeTaskFile(taskId, file.fileId) else api.downloadCaseFile(file.fileId)
    }

    private suspend fun download(limit: Long, call: suspend () -> Response<ResponseBody>): File {
        val started = generation
        return withContext(io) {
            val job = coroutineContext.job
            running += job
            try {
                fetch(call, started, limit)
            } finally {
                running -= job
            }
        }
    }

    // Each throw is a distinct failure the viewer explains differently.
    @Suppress("ThrowsCount")
    private suspend fun fetch(call: suspend () -> Response<ResponseBody>, started: Long, limit: Long): File {
        directory.mkdirs()
        val response = call()
        if (!response.isSuccessful) throw response.toWaiveraError()
        val target = File(directory, UUID.randomUUID().toString())
        val partial = File(directory, "${target.name}.part")
        try {
            response.body()?.byteStream()?.use { copyCapped(it, partial, limit) } ?: throw WaiveraError.Unexpected()
            lock.withLock {
                // Signed out meanwhile: the file belongs to whoever was signed in before.
                if (generation != started) throw WaiveraError.NotSignedIn()
                if (!partial.renameTo(target)) throw WaiveraError.Unexpected()
            }
        } finally {
            partial.delete()
        }
        return target
    }

    private fun copyCapped(input: InputStream, out: File, limit: Long) = out.outputStream().use { sink ->
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw WaiveraError.Unexpected()
            sink.write(buffer, 0, read)
        }
    }

    override fun close(file: File) {
        if (file.parentFile == directory) file.delete()
    }

    override suspend fun clear() {
        running.forEach { it.cancel() }
        lock.withLock {
            generation++
            withContext(io) { directory.listFiles()?.forEach(File::delete) }
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
