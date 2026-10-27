package com.waivera.waivera.feature.chat.message

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.waivera.waivera.core.data.model.AttachmentSource
import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.catchingWaivera
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** An image picked for sending, before it is queued. */
data class StagedImage(val uri: String, val fileName: String?, val contentType: String, val size: Long?)

/** Content-resolver work the screens need, behind an interface so ViewModel tests use a fake. */
interface AttachmentIo {
    /** Reads name, type and size for a picked image; null when it cannot be opened. */
    suspend fun describe(uri: String): StagedImage?

    /** A re-readable source for the outbox (it copies the bytes once at send time). */
    fun source(image: StagedImage): AttachmentSource

    /** Copies [file] into a document the person created with the system picker (SAF, 11 §45). */
    suspend fun saveTo(file: File, destinationUri: String): Result<Unit>

    /** A `content://` uri another app can read, for the share sheet. */
    suspend fun shareableUri(file: File, fileName: String): Result<String>
}

class AndroidAttachmentIo @Inject constructor(@ApplicationContext private val context: Context) : AttachmentIo {
    override suspend fun describe(uri: String): StagedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val parsed = uri.toUri()
            var name: String? = null
            var size: Long? = null
            context.contentResolver.query(parsed, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                        name =
                            cursor.getString(it)
                    }
                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let { size = cursor.getLong(it) }
                }
            }
            StagedImage(uri, name, context.contentResolver.getType(parsed) ?: DEFAULT_IMAGE_TYPE, size)
        }.getOrNull()
    }

    override fun source(image: StagedImage): AttachmentSource = AttachmentSource(
        fileName = image.fileName ?: DEFAULT_IMAGE_NAME,
        contentType = image.contentType,
        size = image.size,
        open = {
            context.contentResolver.openInputStream(image.uri.toUri()) ?: throw WaiveraError.AttachmentExpired()
        },
    )

    override suspend fun saveTo(file: File, destinationUri: String): Result<Unit> = catchingWaivera {
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(destinationUri.toUri())
                ?: throw WaiveraError.Unexpected()
            // Streamed, never read into memory as base64 (11 §34).
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
        }
    }

    override suspend fun shareableUri(file: File, fileName: String): Result<String> = catchingWaivera {
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
            directory.listFiles()?.forEach(File::delete)
            val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().take(MAX_NAME).ifEmpty {
                "attachment"
            }
            val target = File(directory, safeName)
            file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", target).toString()
        }
    }

    companion object {
        const val SHARE_DIRECTORY = "feature_chat_share"
        const val AUTHORITY_SUFFIX = ".feature.chat.files"
        private const val DEFAULT_IMAGE_TYPE = "image/jpeg"
        private const val DEFAULT_IMAGE_NAME = "image.jpg"
        private const val MAX_NAME = 120
    }
}

/**
 * Attachment downloads for Save and Share, with concurrent requests for the same file shared
 * (03 §8) and one retry, as in the reference client.
 */
@Singleton
class AttachmentDownloads @Inject constructor(private val repository: AttachmentRepository) {
    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<File>>>()

    suspend fun download(messageId: Long, fileName: String?): Result<File> {
        val key = "$messageId:${fileName.orEmpty()}"
        val (deferred, owner) = mutex.withLock {
            inFlight[key]?.let { it to false }
                ?: CompletableDeferred<Result<File>>().also { inFlight[key] = it }.let { it to true }
        }
        if (!owner) return deferred.await()
        val result = repository.download(messageId, fileName).let { first ->
            val error = first.exceptionOrNull() as? WaiveraError
            if (first.isFailure &&
                error !is WaiveraError.AttachmentRejected
            ) {
                repository.download(messageId, fileName)
            } else {
                first
            }
        }
        mutex.withLock { inFlight.remove(key) }
        deferred.complete(result)
        return result
    }
}

/** Where timelines are built (parsing rich text and grouping); tests pass their own dispatcher. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatComputeDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatFeatureModule {
    @Binds
    abstract fun attachmentIo(impl: AndroidAttachmentIo): AttachmentIo

    companion object {
        /** Chat clocks follow the device zone (03 §3); read on every use so a zone change applies at once. */
        @Provides
        fun chatClock(clock: Clock): ChatClock = ChatClock(clock) { ZoneId.systemDefault() }

        @Provides
        @ChatComputeDispatcher
        fun computeDispatcher(): CoroutineDispatcher = Dispatchers.Default
    }
}
