package com.waivera.waivera.feature.tasks.files

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.feature.tasks.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FileAction { Save, Share }

/** The sheet a tapped file opens: what it is, and which action is running. */
@Immutable
data class FileSheet(
    val attachmentId: Int,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val viewable: Boolean,
    val busy: FileAction? = null,
)

sealed interface FileActionEffect {
    /** Show it: the viewer when the phone can draw it, otherwise the task on the web. */
    data class Open(val attachmentId: Int) : FileActionEffect

    /** Ask the system file picker where to save [fileName]; the answer comes back to [TaskFileActionsViewModel.saveTo]. */
    data class PickSaveLocation(val fileName: String) : FileActionEffect

    data class Share(val uri: String, val mimeType: String, val fileName: String) : FileActionEffect

    data class Message(@param:StringRes val text: Int) : FileActionEffect
}

/**
 * Tapping a task file. With the web's Download permission for that kind of file (`case_file:download`,
 * or `employee_file:download` for employee files) it opens a sheet to view, save or share it, so
 * nobody has to open a PDF just to keep a copy; without it, the file simply opens.
 */
@HiltViewModel(assistedFactory = TaskFileActionsViewModel.Factory::class)
class TaskFileActionsViewModel @AssistedInject constructor(
    @Assisted private val taskId: Int,
    private val tasks: TaskRepository,
    private val requests: RequestRepository,
    private val files: TaskFileRepository,
    private val export: TaskFileExport,
) : ViewModel() {
    private val state = MutableStateFlow<FileSheet?>(null)
    val sheet: StateFlow<FileSheet?> = state.asStateFlow()

    private val effectChannel = Channel<FileActionEffect>(Channel.BUFFERED)
    val effects: Flow<FileActionEffect> = effectChannel.receiveAsFlow()

    /** Downloaded and waiting for the person to pick where it goes. */
    private var pendingSave: File? = null

    fun choose(attachmentId: Int) {
        viewModelScope.launch {
            val file = attachment(attachmentId) ?: return@launch
            // Access normally loads at sign-in; a tap that beats it waits rather than guessing.
            requests.ensureAccess()
            if (canDownload(file)) {
                state.value = FileSheet(file.attachmentId, file.name, file.kind, file.sizeBytes, file.viewable)
            } else {
                effectChannel.send(FileActionEffect.Open(attachmentId))
            }
        }
    }

    fun dismiss() {
        if (state.value?.busy == null) state.value = null
    }

    fun open() {
        val current = state.value ?: return
        state.value = null
        viewModelScope.launch { effectChannel.send(FileActionEffect.Open(current.attachmentId)) }
    }

    fun save() = run(FileAction.Save) { file, downloaded ->
        pendingSave = downloaded
        effectChannel.send(FileActionEffect.PickSaveLocation(file.name))
    }

    /** Where the picker said to save, or null when the person backed out of it. */
    fun saveTo(destinationUri: String?) {
        val downloaded = pendingSave ?: return
        pendingSave = null
        viewModelScope.launch {
            if (destinationUri != null) {
                val saved = export.saveTo(downloaded, destinationUri).isSuccess
                val message = if (saved) R.string.feature_tasks_file_saved else R.string.feature_tasks_file_save_failed
                effectChannel.send(FileActionEffect.Message(message))
            }
            files.close(downloaded)
            finish()
        }
    }

    fun share() = run(FileAction.Share) { file, downloaded ->
        val uri = export.shareableUri(downloaded, file.name)
        files.close(downloaded)
        uri.fold(
            onSuccess = { effectChannel.send(FileActionEffect.Share(it, export.mimeType(file.name), file.name)) },
            onFailure = { effectChannel.send(FileActionEffect.Message(R.string.feature_tasks_file_share_failed)) },
        )
        finish()
    }

    /** Downloads the sheet's file once, then hands it to [then]; one action at a time. */
    private fun run(action: FileAction, then: suspend (TaskAttachment, File) -> Unit) {
        val current = state.value ?: return
        if (current.busy != null) return
        state.update { it?.copy(busy = action) }
        viewModelScope.launch {
            val file = attachment(current.attachmentId)
            val downloaded = file?.let { files.export(taskId, it).getOrNull() }
            if (file == null || downloaded == null) {
                state.update { it?.copy(busy = null) }
                effectChannel.send(FileActionEffect.Message(R.string.feature_tasks_file_download_failed))
                return@launch
            }
            then(file, downloaded)
        }
    }

    /** The sheet closes once the copy has gone where it was sent. */
    private fun finish() {
        state.value = null
    }

    private suspend fun attachment(attachmentId: Int): TaskAttachment? =
        tasks.attachments(taskId).getOrNull()?.firstOrNull { it.attachmentId == attachmentId }

    private fun canDownload(file: TaskAttachment): Boolean {
        val access = requests.access.value
        return if (file.employeeFile) access.canDownloadEmployeeFiles else access.canDownloadCaseFiles
    }

    override fun onCleared() {
        pendingSave?.let(files::close)
        pendingSave = null
    }

    @AssistedFactory
    interface Factory {
        fun create(taskId: Int): TaskFileActionsViewModel
    }
}
