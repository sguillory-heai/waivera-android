package com.waivera.waivera.core.data.uploads

import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.outbox.ProgressRequestBody
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody

/**
 * Sends queued uploads one at a time with the web's form fields. A dropped connection leaves the
 * rest queued for the next run; anything the server refuses is marked failed with the app's reason.
 */
@Singleton
class TaskUploadProcessor @Inject constructor(private val api: TaskApi, private val store: TaskUploadStore) {
    private val mutex = Mutex()

    /** Returns true when something is left for a later run (the connection went away). */
    suspend fun process(): Boolean = mutex.withLock {
        var next = nextQueued()
        var interrupted = false
        while (next != null && !interrupted) {
            interrupted = !send(next)
            next = nextQueued()
        }
        interrupted
    }

    private suspend fun nextQueued(): UploadEntry? = store.snapshot().firstOrNull { it.state == UploadState.Queued }

    /** False when the connection failed and the entry went back in line. */
    private suspend fun send(entry: UploadEntry): Boolean {
        val file = entry.localPath?.let(::File)?.takeIf(File::exists)
        if (file == null) {
            fail(entry, UploadProblem.Unreadable)
            return true
        }
        store.update(entry.id) { it.copy(state = UploadState.Uploading, problem = null) }
        store.setProgress(entry.id, 0f)
        return try {
            val saved = apiCall { api.uploadTaskFile(parts(entry, file)) }
            store.update(entry.id) {
                it.copy(state = UploadState.Done, savedName = saved.fileName?.takeIf(String::isNotBlank))
            }
            store.setProgress(entry.id, null)
            file.delete()
            true
        } catch (error: WaiveraError) {
            store.setProgress(entry.id, null)
            settleFailure(entry, error)
        } catch (cancelled: CancellationException) {
            // The worker was stopped mid-send (network lost, system limits): back in line for its next run.
            withContext(NonCancellable) {
                store.setProgress(entry.id, null)
                store.update(entry.id) { it.copy(state = UploadState.Queued) }
            }
            throw cancelled
        }
    }

    /** A dropped connection puts the file back in line (false); anything else fails it with a reason. */
    private suspend fun settleFailure(entry: UploadEntry, error: WaiveraError): Boolean {
        val offline = error is WaiveraError.Network || error is WaiveraError.Timeout
        val problem = problem(error)
        when {
            offline -> store.update(entry.id) {
                // A timeout can come after the server saved the file; the resend then says so.
                it.copy(state = UploadState.Queued, mayHaveLanded = it.mayHaveLanded || error is WaiveraError.Timeout)
            }
            problem == UploadProblem.AlreadyFilled && entry.mayHaveLanded ->
                store.update(entry.id) { it.copy(state = UploadState.Done) }
            else -> fail(entry, problem)
        }
        return !offline
    }

    private suspend fun fail(entry: UploadEntry, problem: UploadProblem) {
        store.update(entry.id) { it.copy(state = UploadState.Failed, problem = problem) }
    }

    private fun parts(entry: UploadEntry, file: File): List<MultipartBody.Part> {
        val body = ProgressRequestBody(file, entry.mimeType.toMediaTypeOrNull()) { store.setProgress(entry.id, it) }
        return UploadForm.parts(body, entry.fileName, entry.target, entry.taskId, entry.mimeType, file.length())
    }

    companion object {
        /** sprout_api `TaskFileUploadErrorCodes`, and the 409s for a locked task or a filled requirement. */
        fun problem(error: WaiveraError): UploadProblem = when (error.errorCode) {
            "PDF_ENCRYPTED", "PDF_PASSWORD_REQUIRED" -> UploadProblem.PdfLocked
            "PDF_UNREADABLE", "PDF_NO_PAGES" -> UploadProblem.PdfUnreadable
            "FILE_EMPTY" -> UploadProblem.Empty
            "FILE_TOO_LARGE" -> UploadProblem.TooLarge
            "FILE_TYPE_UNSUPPORTED" -> UploadProblem.Unsupported
            "FILE_EXTENSION_MISMATCH" -> UploadProblem.Mismatch
            "FILE_ALREADY_UPLOADED" -> UploadProblem.AlreadyFilled
            else -> when (error) {
                is WaiveraError.Conflict -> UploadProblem.TaskLocked
                is WaiveraError.NotFound, is WaiveraError.Forbidden -> UploadProblem.NoAccess
                is WaiveraError.AttachmentTooLarge -> UploadProblem.TooLarge
                else -> UploadProblem.Failed
            }
        }
    }
}
