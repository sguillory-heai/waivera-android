package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.repository.TaskFileRepository
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TaskFileCacheDirectory

/**
 * Task files for the read-only viewer. Each open gets its own file under a random name in the
 * app's private cache; closing the viewer deletes it and sign-out empties the directory. Like chat
 * attachments, a download that finishes after sign-out is thrown away, never kept for the next
 * account. Files over [TaskAttachment.MAX_VIEW_BYTES] stop downloading at the limit.
 */
@Singleton
class LiveTaskFileRepository @Inject constructor(
    private val api: TaskApi,
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
        val started = generation
        withContext(io) {
            val job = coroutineContext.job
            running += job
            try {
                fetch(taskId, file, started)
            } finally {
                running -= job
            }
        }
    }

    // Each throw is a distinct failure the viewer explains differently.
    @Suppress("ThrowsCount")
    private suspend fun fetch(taskId: Int, file: TaskAttachment, started: Long): File {
        directory.mkdirs()
        val response = if (file.employeeFile) {
            api.downloadEmployeeTaskFile(taskId, file.fileId)
        } else {
            api.downloadCaseFile(file.fileId)
        }
        if (!response.isSuccessful) throw response.toWaiveraError()
        val target = File(directory, UUID.randomUUID().toString())
        val partial = File(directory, "${target.name}.part")
        try {
            response.body()?.byteStream()?.use { copyCapped(it, partial) } ?: throw WaiveraError.Unexpected()
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

    private fun copyCapped(input: InputStream, out: File) = out.outputStream().use { sink ->
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > TaskAttachment.MAX_VIEW_BYTES) throw WaiveraError.Unexpected()
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
