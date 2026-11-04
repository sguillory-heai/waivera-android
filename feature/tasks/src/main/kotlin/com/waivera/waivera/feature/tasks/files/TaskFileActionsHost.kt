package com.waivera.waivera.feature.tasks.files

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.feature.tasks.R

/**
 * Hooks a task's files up to the open / save / share sheet: tapping a file calls
 * [TaskFileActionsViewModel.choose], and when the sheet (or a person without the download
 * permission) wants to look at it, [onOpen] takes over with the existing viewer-or-web routing.
 */
@Composable
internal fun rememberTaskFileActions(
    taskId: Int,
    snackbar: SnackbarHostState,
    onOpen: (attachmentId: Int) -> Unit,
): TaskFileActionsViewModel {
    val viewModel = hiltViewModel<TaskFileActionsViewModel, TaskFileActionsViewModel.Factory>(
        key = "task-files-$taskId",
        creationCallback = { factory -> factory.create(taskId) },
    )
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val latestOpen by rememberUpdatedState(onOpen)
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SAVE_MIME_TYPE)) { uri ->
        viewModel.saveTo(uri?.toString())
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FileActionEffect.Open -> latestOpen(effect.attachmentId)
                is FileActionEffect.PickSaveLocation -> try {
                    saveFile.launch(effect.fileName)
                } catch (_: ActivityNotFoundException) {
                    viewModel.saveTo(null)
                    snackbar.showSnackbar(resources.getString(R.string.feature_tasks_file_save_failed))
                }
                is FileActionEffect.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = effect.mimeType
                        putExtra(Intent.EXTRA_STREAM, effect.uri.toUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(
                        send,
                        resources.getString(R.string.feature_tasks_file_share_title, effect.fileName),
                    )
                    try {
                        context.startActivity(chooser)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.showSnackbar(resources.getString(R.string.feature_tasks_file_share_failed))
                    }
                }
                is FileActionEffect.Message -> snackbar.showSnackbar(resources.getString(effect.text))
            }
        }
    }
    sheet?.let { current ->
        TaskFileActionsSheet(
            sheet = current,
            actions = FileSheetActions(
                onDismiss = viewModel::dismiss,
                onOpen = viewModel::open,
                onSave = viewModel::save,
                onShare = viewModel::share,
            ),
        )
    }
    return viewModel
}

/** Generic type for the "save to" picker; the file name carries the real extension. */
private const val SAVE_MIME_TYPE = "application/octet-stream"
