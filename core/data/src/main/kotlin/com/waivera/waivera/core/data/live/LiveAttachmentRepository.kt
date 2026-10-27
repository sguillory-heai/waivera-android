package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.di.IoDispatcher
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.toWaiveraError
import java.io.File
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
annotation class AttachmentCacheDirectory

/**
 * Attachment downloads (G-15): the endpoint needs the auth header, `attachmentUrl` is relative,
 * and a scan rejection returns 423. Files land in a bounded cache directory that sign-out wipes.
 * The wipe cancels downloads still running and bumps a generation, so a download that finishes
 * after it can never put the previous account's file back into the cache (IS-08).
 */
@Singleton
class LiveAttachmentRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val config: NetworkConfig,
    @AttachmentCacheDirectory private val directory: File,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AttachmentRepository,
    SignOutCleaner {
    private val publishLock = Mutex()
    private val running: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var generation = 0L

    override suspend fun download(messageId: Long, fileName: String?): Result<File> = catchingWaivera {
        val started = generation
        withContext(io) {
            val job = coroutineContext.job
            running += job
            try {
                fetch(messageId, fileName, started)
            } finally {
                running -= job
            }
        }
    }

    private suspend fun fetch(messageId: Long, fileName: String?, started: Long): File {
        directory.mkdirs()
        val target = File(directory, "$messageId-${safeName(fileName)}")
        if (target.exists() && target.length() > 0) return target
        val response = chatApi.downloadAttachment(messageId)
        if (!response.isSuccessful) throw response.toWaiveraError()
        val partial = File(directory, "${target.name}.part")
        response.body()?.byteStream()?.use { input -> partial.outputStream().use { input.copyTo(it) } }
            ?: throw WaiveraError.Unexpected()
        publish(partial, target, started)
        trim()
        return target
    }

    private suspend fun publish(partial: File, target: File, started: Long) = publishLock.withLock {
        // Signed out meanwhile: the file belongs to whoever was signed in before.
        val renamed = generation == started && partial.renameTo(target)
        if (!renamed) {
            partial.delete()
            throw if (generation == started) WaiveraError.Unexpected() else WaiveraError.NotSignedIn()
        }
    }

    override fun resolve(attachmentUrl: String): String? =
        if (attachmentUrl.startsWith("https://")) attachmentUrl else config.resolveApiUrl(attachmentUrl)?.toString()

    /** Keeps the newest files under [MAX_CACHE_BYTES]. */
    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= MAX_CACHE_BYTES) return
            total -= file.length()
            file.delete()
        }
    }

    private fun safeName(name: String?): String =
        name?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.take(MAX_NAME)?.ifBlank { null } ?: "attachment"

    override suspend fun clear() {
        running.forEach { it.cancel() }
        publishLock.withLock {
            generation++
            withContext(io) { directory.listFiles()?.forEach(File::delete) }
        }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 100L * 1024 * 1024
        const val MAX_NAME = 80
    }
}
