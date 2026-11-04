package com.waivera.waivera.feature.tasks.files

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.network.di.IoDispatcher
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.catchingWaivera
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Puts a downloaded task file where the person chose, or hands it to another app. */
interface TaskFileExport {
    /** Copies [file] into the document the system file picker created at [destinationUri]. */
    suspend fun saveTo(file: File, destinationUri: String): Result<Unit>

    /**
     * A read-only content uri for [file] under its real [fileName], for the share sheet. [file]
     * moves into the shared folder when it can, so a large file isn't written twice.
     */
    suspend fun shareableUri(file: File, fileName: String): Result<String>

    /** The type another app should treat [fileName] as. */
    fun mimeType(fileName: String): String
}

class AndroidTaskFileExport @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TaskFileExport {
    override suspend fun saveTo(file: File, destinationUri: String): Result<Unit> = catchingWaivera {
        withContext(io) {
            val output = context.contentResolver.openOutputStream(destinationUri.toUri())
                ?: throw WaiveraError.Unexpected()
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
        }
    }

    override suspend fun shareableUri(file: File, fileName: String): Result<String> = catchingWaivera {
        withContext(io) {
            val root = File(context.cacheDir, SHARE_DIRECTORY)
            // Each share gets its own folder, because the receiving app may read it much later.
            // Folders from earlier shares go once they are a day old; sign-out removes them all.
            val cutoff = System.currentTimeMillis() - KEEP_SHARED_MILLIS
            root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
            val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            val target = File(directory, safeName(fileName))
            if (!file.renameTo(target)) {
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", target).toString()
        }
    }

    override fun mimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: DEFAULT_TYPE
    }

    companion object {
        /** Under `cacheDir`; must match `feature_tasks_file_paths.xml`. */
        const val SHARE_DIRECTORY = "feature_tasks_share"
        const val AUTHORITY_SUFFIX = ".feature.tasks.files"
        private const val DEFAULT_TYPE = "application/octet-stream"
        private const val MAX_NAME = 120
        private const val KEEP_SHARED_MILLIS = 24L * 60 * 60 * 1000

        /** A file name any app accepts: no path characters, never empty. */
        fun safeName(fileName: String): String =
            fileName.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "-").trim().take(MAX_NAME)
                .takeUnless { it.isEmpty() || it.all { c -> c == '.' } } ?: "file"
    }
}

/** The last shared task file belongs to whoever was signed in, so every session end removes it. */
class TaskShareFolderCleaner(private val cacheDir: File, private val io: CoroutineDispatcher) : SignOutCleaner {
    override suspend fun clear() {
        withContext(io) { File(cacheDir, AndroidTaskFileExport.SHARE_DIRECTORY).deleteRecursively() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskFileExportModule {
    @Binds
    abstract fun export(impl: AndroidTaskFileExport): TaskFileExport

    companion object {
        @Provides
        @IntoSet
        fun shareFolderCleaner(
            @ApplicationContext context: Context,
            @IoDispatcher io: CoroutineDispatcher,
        ): SignOutCleaner = TaskShareFolderCleaner(context.cacheDir, io)
    }
}
