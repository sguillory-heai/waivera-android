package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.di.IoDispatcher
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.toWaiveraError
import java.io.File
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AttachmentCacheDirectory

/**
 * Attachment downloads (G-15): the endpoint needs the auth header, `attachmentUrl` is relative,
 * and a scan rejection returns 423. Files land in a bounded cache directory that sign-out wipes.
 */
@Singleton
class LiveAttachmentRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val config: NetworkConfig,
    @AttachmentCacheDirectory private val directory: File,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AttachmentRepository, SignOutCleaner {
    override suspend fun download(messageId: Long, fileName: String?): Result<File> = catchingWaivera {
        withContext(io) {
            directory.mkdirs()
            val target = File(directory, "$messageId-${safeName(fileName)}")
            if (target.exists() && target.length() > 0) return@withContext target
            val response = chatApi.downloadAttachment(messageId)
            if (!response.isSuccessful) throw response.toWaiveraError()
            val body = response.body() ?: throw WaiveraError.Unexpected()
            val partial = File(directory, "${target.name}.part")
            body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            if (!partial.renameTo(target)) throw WaiveraError.Unexpected()
            trim()
            target
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
        withContext(io) { directory.listFiles()?.forEach(File::delete) }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 100L * 1024 * 1024
        const val MAX_NAME = 80
    }
}
