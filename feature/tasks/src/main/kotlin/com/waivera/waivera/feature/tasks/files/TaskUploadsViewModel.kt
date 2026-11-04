package com.waivera.waivera.feature.tasks.files

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.RequiredFile
import com.waivera.waivera.core.data.model.TaskUpload
import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.data.repository.TaskUploadRepository
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.feature.tasks.Loadable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One file on its way up, as its row reads. */
@Immutable
data class UploadRow(
    val id: String,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val state: UploadState,
    val progress: Float?,
    val problem: UploadProblem?,
)

/** A required file still missing; [upload] is the file going up for it, if any. */
@Immutable
data class RequiredRow(
    val id: Int,
    val name: String,
    val isForm: Boolean,
    val removalPending: Boolean,
    val upload: UploadRow?,
)

/**
 * The Add a file sheet. An extra file on an employee task is filed under a category first, as
 * the web's upload modal asks; a required file takes one file, extra files up to ten.
 */
@Immutable
data class UploadSheet(
    val target: UploadTarget,
    val title: String?,
    val needsCategory: Boolean,
    val categories: Loadable<List<DocumentCategory>> = Loadable.Loading,
    val single: Boolean,
) {
    val ready: Boolean get() = !needsCategory || target.categoryId != null
}

@Immutable
data class TaskUploadsUiState(
    val required: Loadable<List<RequiredRow>> = Loadable.Loading,
    /** Extra files going up, and any whose requirement is no longer listed. */
    val uploads: List<UploadRow> = emptyList(),
    val sheet: UploadSheet? = null,
)

sealed interface TaskUploadsEffect {
    /** A file landed on the server: the task's file list should load again. */
    data object FilesChanged : TaskUploadsEffect
}

/**
 * Required files and uploads for one task (sprout_web `TaskAttachmentSection`). Uploads go to
 * [TaskUploadRepository], which sends them in the background; a finished one shows as uploaded
 * for a moment, then the task's lists load again and it leaves, as the web's checkmark does.
 */
@HiltViewModel(assistedFactory = TaskUploadsViewModel.Factory::class)
@Suppress("TooManyFunctions") // One small handler per sheet and row action.
class TaskUploadsViewModel @AssistedInject constructor(
    @Assisted private val taskId: Int,
    private val repository: TaskUploadRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val required = MutableStateFlow<Loadable<List<RequiredFile>>>(Loadable.Loading)
    private val sheet = MutableStateFlow<UploadSheet?>(null)
    private val settling = mutableSetOf<String>()
    private val failuresSeen = mutableSetOf<String>()
    private val effectChannel = Channel<TaskUploadsEffect>(Channel.BUFFERED)
    val effects: Flow<TaskUploadsEffect> = effectChannel.receiveAsFlow()

    private val uploads: StateFlow<List<TaskUpload>> = repository.uploads(taskId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<TaskUploadsUiState> = combine(required, uploads, sheet) { required, uploads, sheet ->
        val rows = uploads.map(::row)
        val byRequirement = uploads.filter { it.requirementId != null }.associateBy({ it.requirementId }, ::row)
        TaskUploadsUiState(
            required = when (required) {
                Loadable.Loading -> Loadable.Loading
                Loadable.Failed -> Loadable.Failed
                is Loadable.Ready -> Loadable.Ready(
                    required.value.map {
                        RequiredRow(it.id, it.name, it.isForm, it.removalPending, byRequirement[it.id])
                    },
                )
            },
            // A required file's upload shows on its row; once that requirement is gone (filled or
            // dropped meanwhile) it moves here, so a failure can still be read and dismissed.
            uploads = rows.filterIndexed { index, _ ->
                val requirement = uploads[index].requirementId
                requirement == null ||
                    (required as? Loadable.Ready)?.value?.none { it.id == requirement } == true
            },
            sheet = sheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TaskUploadsUiState())

    init {
        loadRequired()
        viewModelScope.launch { uploads.collect(::settle) }
    }

    fun loadRequired() {
        viewModelScope.launch {
            val result = repository.requiredFiles(taskId)
            required.update { current ->
                result.fold({ Loadable.Ready(it) }, { if (current is Loadable.Ready) current else Loadable.Failed })
            }
        }
    }

    fun startRequired(id: Int) {
        val file = (required.value as? Loadable.Ready)?.value?.firstOrNull { it.id == id } ?: return
        if (file.isForm) return
        sheet.value = UploadSheet(
            target = UploadTarget(requirementId = file.id, requirementName = file.name, typeId = file.typeId),
            title = file.name,
            needsCategory = false,
            categories = Loadable.Ready(emptyList()),
            single = true,
        )
    }

    /** An extra file on an employee task is filed under a category first. */
    fun startExtra(employeeTask: Boolean) {
        sheet.value = UploadSheet(target = UploadTarget(), title = null, needsCategory = employeeTask, single = false)
        if (employeeTask) loadCategories()
    }

    fun loadCategories() {
        sheet.update { it?.copy(categories = Loadable.Loading) }
        viewModelScope.launch {
            val result = repository.categories()
            sheet.update { current ->
                current?.copy(categories = result.fold({ Loadable.Ready(it) }, { Loadable.Failed }))
            }
        }
    }

    fun pickCategory(id: Int) = sheet.update { it?.copy(target = it.target.copy(categoryId = id)) }

    fun closeSheet() {
        sheet.value = null
    }

    /**
     * About to open a picker or the camera: remember where its files go, since the app can be
     * stopped while the camera or picker is in front and the sheet's state is gone on return.
     */
    fun launching() {
        val current = sheet.value ?: return
        saved[PENDING_TARGET] = WaiveraJson.encodeToString(UploadTarget.serializer(), current.target)
        saved[PENDING_SINGLE] = current.single
    }

    /** The picker's result; an empty list means the person backed out, and the sheet stays. */
    fun picked(uris: List<String>) {
        val current = sheet.value
        val target = current?.target ?: pendingTarget() ?: return
        val single = current?.single ?: saved.get<Boolean>(PENDING_SINGLE) ?: true
        if (uris.isEmpty() || current?.ready == false) return
        sheet.value = null
        saved.remove<String>(PENDING_TARGET)
        val chosen = if (single) uris.take(1) else uris
        viewModelScope.launch { repository.add(taskId, chosen, target) }
    }

    private fun pendingTarget(): UploadTarget? = saved.get<String>(PENDING_TARGET)?.let {
        runCatching { WaiveraJson.decodeFromString(UploadTarget.serializer(), it) }.getOrNull()
    }

    fun retry(id: String) = repository.retry(id)

    fun dismiss(id: String) = repository.dismiss(id)

    private fun settle(list: List<TaskUpload>) {
        list.filter { it.state == UploadState.Done && settling.add(it.id) }.forEach { done ->
            viewModelScope.launch {
                delay(SETTLE_MILLIS)
                effectChannel.send(TaskUploadsEffect.FilesChanged)
                if (done.requirementId != null) loadRequired()
                repository.dismiss(done.id)
            }
        }
        // A requirement someone else filled meanwhile leaves the list; the row says why it failed.
        val filledElsewhere = list.any {
            it.problem == UploadProblem.AlreadyFilled && it.requirementId != null && failuresSeen.add(it.id)
        }
        if (filledElsewhere) loadRequired()
    }

    private fun row(upload: TaskUpload) = UploadRow(
        id = upload.id,
        name = upload.fileName,
        kind = upload.kind,
        sizeBytes = upload.sizeBytes,
        state = upload.state,
        progress = upload.progress,
        problem = upload.problem,
    )

    @AssistedFactory
    interface Factory {
        fun create(taskId: Int): TaskUploadsViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val PENDING_TARGET = "upload_pending_target"
        const val PENDING_SINGLE = "upload_pending_single"

        /** sprout_web shows the checkmark, then reloads the list after 2.5 s. */
        const val SETTLE_MILLIS = 2_500L
    }
}
