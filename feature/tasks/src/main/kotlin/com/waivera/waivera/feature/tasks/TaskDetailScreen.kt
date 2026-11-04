package com.waivera.waivera.feature.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBottomActionBar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraCountTone
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraSectionAction
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberLatestNonNull
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.tasks.files.RequiredFilesSection
import com.waivera.waivera.feature.tasks.files.TaskUploadsEffect
import com.waivera.waivera.feature.tasks.files.TaskUploadsUiState
import com.waivera.waivera.feature.tasks.files.TaskUploadsViewModel
import com.waivera.waivera.feature.tasks.files.UploadActions
import com.waivera.waivera.feature.tasks.files.UploadSheetActions
import com.waivera.waivera.feature.tasks.files.UploadSourceHost
import com.waivera.waivera.feature.tasks.files.rememberTaskFileActions
import kotlinx.coroutines.launch

/** One task: its details, recent activity and Log activity. Owned by `:feature:tasks`; routed by `:app`. */
@Composable
fun TaskDetailScreen(
    taskId: Int,
    onBack: () -> Unit,
    onOpenFile: (taskId: Int, attachmentId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<TaskDetailViewModel, TaskDetailViewModel.Factory>(
        key = "task-$taskId",
        creationCallback = { factory -> factory.create(taskId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TaskDetailEffect.Logged -> snackbar.showSnackbar(
                    effect.savedAs?.let { resources.getString(R.string.feature_tasks_logged_saved_as, it) }
                        ?: resources.getString(R.string.feature_tasks_log_done),
                )
                is TaskDetailEffect.Asked -> snackbar.showSnackbar(resources.getString(effect.message()))
                TaskDetailEffect.WebUnavailable ->
                    snackbar.showSnackbar(resources.getString(R.string.feature_tasks_web_unavailable))
                is TaskDetailEffect.OpenFile -> onOpenFile(effect.taskId, effect.attachmentId)
                is TaskDetailEffect.OpenWeb -> {
                    val opened = runCatching { uriHandler.openUri(effect.url) }.isSuccess
                    if (!opened) snackbar.showSnackbar(resources.getString(R.string.feature_tasks_web_failed))
                }
            }
        }
    }
    val fileActions = rememberTaskFileActions(taskId, snackbar, onOpen = viewModel::openFile)
    val uploadsViewModel = hiltViewModel<TaskUploadsViewModel, TaskUploadsViewModel.Factory>(
        key = "task-uploads-$taskId",
        creationCallback = { factory -> factory.create(taskId) },
    )
    val uploads by uploadsViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.task?.isClientTask) {
        state.task?.let { uploadsViewModel.employeeTask = !it.isClientTask }
    }
    LaunchedEffect(uploadsViewModel) {
        uploadsViewModel.effects.collect { effect ->
            when (effect) {
                TaskUploadsEffect.FilesChanged -> viewModel.loadFiles()
            }
        }
    }
    TaskDetailContent(
        state = state,
        uploads = uploads,
        uploadActions = UploadActions(
            onUploadRequired = uploadsViewModel::startRequired,
            onOpenForm = viewModel::openOnWeb,
            onRetryRequired = uploadsViewModel::loadRequired,
            onAddFile = uploadsViewModel::startExtra,
            onRetryUpload = uploadsViewModel::retry,
            onDismissUpload = uploadsViewModel::dismiss,
        ),
        snackbar = snackbar,
        actions = TaskDetailActions(
            onBack = onBack,
            onRetry = viewModel::load,
            onRetryActivity = viewModel::loadActivity,
            onOpenFile = fileActions::choose,
            onRetryFiles = viewModel::loadFiles,
            onLog = viewModel::startLog,
            onOpenWeb = viewModel::openOnWeb,
            onAsk = viewModel::startAsk,
        ),
        modifier = modifier,
    )
    UploadSourceHost(
        sheet = uploads.sheet,
        actions = UploadSheetActions(
            onDismiss = uploadsViewModel::closeSheet,
            onCategory = uploadsViewModel::pickCategory,
            onRetryCategories = uploadsViewModel::loadCategories,
        ),
        onPicked = uploadsViewModel::picked,
    )
    state.ask?.let { form ->
        AskChangeSheet(
            form = form,
            task = state.task,
            today = state.today,
            actions = AskActions(
                onChoose = viewModel::chooseAsk,
                onBack = viewModel::backToChoices,
                onDismiss = viewModel::dismissAsk,
                onRetryReasons = viewModel::loadAskReasons,
                onRetryFiles = viewModel::loadAskFiles,
                onReason = viewModel::setAskReason,
                onDue = viewModel::setAskDue,
                onExplanation = viewModel::setAskExplanation,
                onEvidence = viewModel::setAskEvidence,
                onSubmit = viewModel::submitAsk,
            ),
        )
    }
    state.log?.let { form ->
        LogActivitySheet(
            form = form,
            task = state.task,
            actions = LogActivityActions(
                onDismiss = viewModel::dismissLog,
                onRetryTypes = viewModel::loadTypes,
                onChooseType = viewModel::chooseType,
                onStep = viewModel::stepMinutes,
                onWhen = viewModel::setWhen,
                onDescription = viewModel::setDescription,
                onSubmit = viewModel::submitLog,
                onFilePicked = viewModel::pickLogFile,
                onRemoveFile = viewModel::removeLogFile,
            ),
        )
    }
}

internal data class TaskDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onRetryActivity: () -> Unit,
    val onLog: () -> Unit,
    val onOpenWeb: () -> Unit,
    val onAsk: () -> Unit = {},
    val onOpenFile: (Int) -> Unit = {},
    val onRetryFiles: () -> Unit = {},
) {
    companion object {
        val None = TaskDetailActions({}, {}, {}, {}, {})
    }
}

private fun TaskDetailEffect.Asked.message(): Int = when {
    inEffect && kind == AskKind.MoreTime -> R.string.feature_tasks_asked_in_effect_more_time
    inEffect && kind == AskKind.Delay -> R.string.feature_tasks_asked_in_effect_delay
    kind == AskKind.MoreTime -> R.string.feature_tasks_asked_more_time
    kind == AskKind.Delay -> R.string.feature_tasks_asked_delay
    else -> R.string.feature_tasks_asked_closure
}

@Composable
internal fun TaskDetailContent(
    state: TaskDetailUiState,
    snackbar: SnackbarHostState,
    actions: TaskDetailActions,
    modifier: Modifier = Modifier,
    uploads: TaskUploadsUiState = TaskUploadsUiState(required = Loadable.Ready(emptyList())),
    uploadActions: UploadActions = UploadActions(),
) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    val task = state.task
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        WaiveraTopBar(
            title = task?.title ?: stringResource(R.string.feature_tasks_title),
            subtitle = task?.key,
            onBack = actions.onBack,
            actions = if (task != null) {
                listOf(
                    TopBarAction(
                        icon = WaiveraIcons.OpenExternal,
                        contentDescription = stringResource(R.string.feature_tasks_open_on_web),
                        onClick = actions.onOpenWeb,
                    ),
                )
            } else {
                emptyList()
            },
        )
        Box(Modifier.weight(1f)) {
            WaiveraLoadSwap(
                loading = state.phase == DetailPhase.Loading,
                modifier = Modifier.fillMaxSize(),
            ) { loading ->
                when {
                    loading -> if (rememberSkeletonVisible(loading = true)) DetailSkeleton()
                    task == null -> WaiveraErrorState(
                        title = stringResource(R.string.feature_tasks_detail_error_title),
                        message = stringResource(state.errorMessage ?: R.string.feature_tasks_activity_error),
                        onRetry = actions.onRetry,
                        modifier = Modifier.padding(top = WaiveraSpacing.Large),
                    )
                    else -> DetailBody(state, task, actions, uploads, uploadActions)
                }
            }
            // Above the action bar, so a confirmation never covers Log activity.
            WaiveraSnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = WaiveraSpacing.Small),
            )
        }
        if (task != null) {
            WaiveraBottomActionBar(insets = WindowInsets.navigationBars) {
                if (state.canLog) {
                    WaiveraButton(
                        text = stringResource(R.string.feature_tasks_log_activity),
                        onClick = actions.onLog,
                        fullWidth = true,
                        leadingIcon = WaiveraIcons.Add,
                        size = WaiveraButtonSize.Large,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.feature_tasks_finished_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                // Ask for a change leaves while a request is pending; the banner says why.
                AnimatedVisibility(
                    visible = state.canAsk,
                    enter = expandVertically(motion.layout(), expandFrom = Alignment.Top) + fadeIn(motion.state()),
                    exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
                ) {
                    WaiveraButton(
                        text = stringResource(R.string.feature_tasks_ask_change),
                        onClick = actions.onAsk,
                        variant = WaiveraButtonVariant.Text,
                        fullWidth = true,
                        size = WaiveraButtonSize.Large,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBody(
    state: TaskDetailUiState,
    task: TaskDetail,
    actions: TaskDetailActions,
    uploads: TaskUploadsUiState,
    uploadActions: UploadActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = WaiveraSpacing.Large),
    ) {
        val motion = WaiveraTheme.motion
        // The banner grows in when a request is sent from here, and leaves if it fails.
        AnimatedVisibility(
            visible = state.pending != null,
            enter = expandVertically(motion.layout(), expandFrom = Alignment.Top) + fadeIn(motion.state()),
            exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
        ) {
            val shown = rememberLatestNonNull(state.pending)
            shown?.let { PendingRequestBanner(it) }
        }
        state.delay?.let { DelayBanner(it) }
        WaiveraSectionHeader(
            text = stringResource(R.string.feature_tasks_detail_section),
            first = state.pending == null && state.delay == null,
        )
        WaiveraCardGroup {
            Column(Modifier.fillMaxWidth().padding(vertical = WaiveraSpacing.MediumSmall)) {
                InfoRow(stringResource(R.string.feature_tasks_field_status), R.drawable.feature_tasks_ic_task) {
                    WaiveraStatusBadge(text = task.state.label(), tone = TaskFormat.tone(task.state, state.late))
                }
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_field_due), WaiveraIcons.Calendar) {
                    Text(
                        text = state.dueDate ?: stringResource(R.string.feature_tasks_no_due),
                        style = WaiveraTextStyles.FieldValue,
                        color = if (state.late) WaiveraTheme.colors.dangerText else WaiveraTheme.colors.text,
                    )
                }
                task.about?.let {
                    InfoDivider()
                    InfoRow(stringResource(R.string.feature_tasks_field_about), WaiveraIcons.Person) { InfoValue(it) }
                }
                task.type?.let {
                    InfoDivider()
                    InfoRow(stringResource(R.string.feature_tasks_field_type), WaiveraIcons.Hash) { InfoValue(it) }
                }
                task.assigneeName?.let {
                    InfoDivider()
                    InfoRow(stringResource(R.string.feature_tasks_field_assigned_to), WaiveraIcons.People) {
                        InfoValue(it)
                    }
                }
                task.assignedBy?.let {
                    InfoDivider()
                    InfoRow(stringResource(R.string.feature_tasks_field_assigned_by), WaiveraIcons.People) {
                        InfoValue(it)
                    }
                }
            }
        }
        task.description?.takeIf { it.isNotBlank() }?.let { description ->
            WaiveraSectionHeader(text = stringResource(R.string.feature_tasks_description_section))
            WaiveraCardGroup {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.text,
                    modifier = Modifier.padding(WaiveraSpacing.Medium),
                )
            }
        }
        RequiredFilesSection(uploads.required, canUpload = state.canUpload, actions = uploadActions)
        FilesSection(
            files = state.files,
            onOpen = actions.onOpenFile,
            onRetry = actions.onRetryFiles,
            uploads = uploads.uploads,
            uploadActions = uploadActions,
            canUpload = state.canUpload,
        )
        ActivitySection(state.activity, actions.onRetryActivity)
    }
}

/**
 * The activity, oldest first so new work lands at the bottom. A long log gets "Jump to latest" on
 * its header, and an entry just logged here scrolls itself into view.
 */
@Composable
private fun ActivitySection(activity: Loadable<List<ActivityRow>>, onRetry: () -> Unit) {
    val rows = (activity as? Loadable.Ready)?.value.orEmpty()
    val latest = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val count = rows.size
    // At large font the title and the link don't fit on one line, so the link gets its own row.
    val large = isLargeFontScale()
    WaiveraSectionHeader(
        title = stringResource(R.string.feature_tasks_activity_section),
        count = count.takeIf { it > 0 },
        countDescription = pluralStringResource(R.plurals.feature_tasks_activity_count, count, count),
        countTone = WaiveraCountTone.Info,
        action = if (count > JUMP_AFTER && !large) {
            WaiveraSectionAction(
                label = stringResource(R.string.feature_tasks_activity_jump),
                onClick = { scope.launch { latest.bringIntoView() } },
            )
        } else {
            null
        },
        modifier = Modifier.padding(
            start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
            end = WaiveraSpacing.Gutter,
            top = WaiveraSpacing.MediumLarge,
            bottom = WaiveraSpacing.Small + WaiveraSizes.SectionActionOverhang,
        ),
    )
    if (count > JUMP_AFTER && large) {
        WaiveraButton(
            text = stringResource(R.string.feature_tasks_activity_jump),
            onClick = { scope.launch { latest.bringIntoView() } },
            variant = WaiveraButtonVariant.Text,
            size = WaiveraButtonSize.Compact,
            modifier = Modifier.padding(start = WaiveraSpacing.Small, bottom = WaiveraSpacing.Small),
        )
    }
    WaiveraCardGroup {
        when (activity) {
            Loadable.Loading -> Column(
                modifier = Modifier.padding(WaiveraSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                val pulse = rememberSkeletonPulse()
                SkeletonText(style = MaterialTheme.typography.bodyLarge, widthFraction = SKELETON_WIDE, pulse = pulse)
                SkeletonText(
                    style = MaterialTheme.typography.bodyMedium,
                    widthFraction = SKELETON_NARROW,
                    pulse = pulse,
                )
            }
            Loadable.Failed -> WaiveraErrorState(
                message = stringResource(R.string.feature_tasks_activity_error),
                onRetry = onRetry,
                dense = true,
            )
            is Loadable.Ready -> if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_tasks_activity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.padding(WaiveraSpacing.Medium),
                )
            } else {
                rows.forEachIndexed { index, row ->
                    key(row.id) {
                        if (index > 0) WaiveraInsetDivider()
                        ActivityItem(
                            row = row,
                            modifier = if (index ==
                                rows.lastIndex
                            ) {
                                Modifier.bringIntoViewRequester(latest)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_tasks_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WaiveraSpacing.Gutter)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        repeat(SKELETON_ROWS) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WaiveraTheme.colors.surface, WaiveraShapes.ListCard)
                    .border(WaiveraSpacing.Hairline, WaiveraTheme.colors.border, WaiveraShapes.ListCard)
                    .padding(WaiveraSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                SkeletonText(style = WaiveraTextStyles.FieldLabel, widthFraction = SKELETON_NARROW, pulse = pulse)
                SkeletonText(style = WaiveraTextStyles.FieldValue, widthFraction = SKELETON_WIDE, pulse = pulse)
            }
        }
    }
}

@Composable
private fun PendingRequestBanner(pending: PendingBanner) {
    val (title, message) = when (pending.kind) {
        RequestKind.MoreTime -> stringResource(R.string.feature_tasks_pending_more_time_title) to (
            pending.newDue?.let { stringResource(R.string.feature_tasks_pending_more_time, it) }
                ?: stringResource(R.string.feature_tasks_pending_more_time_short)
            )
        RequestKind.Delay -> {
            val start = pending.delayStart
            val end = pending.delayEnd
            stringResource(R.string.feature_tasks_pending_delay_title) to (
                if (start != null && end != null) {
                    stringResource(R.string.feature_tasks_pending_delay, start, end)
                } else {
                    stringResource(R.string.feature_tasks_pending_delay_short)
                }
                )
        }
        RequestKind.Closure ->
            stringResource(R.string.feature_tasks_pending_closure_title) to
                stringResource(R.string.feature_tasks_pending_closure)
        else ->
            stringResource(R.string.feature_tasks_pending_other_title) to
                stringResource(R.string.feature_tasks_pending_other)
    }
    WaiveraBanner(
        title = title,
        message = message,
        tone = BannerTone.Warning,
        icon = pending.kind.icon(),
        modifier = Modifier.padding(top = WaiveraSpacing.Small, bottom = WaiveraSpacing.ExtraSmall),
    )
}

/**
 * An approved delay covers today. Like the web's delay note it says the due date stays put, and
 * the task is left out of Overdue until the delay ends.
 */
@Composable
private fun DelayBanner(delay: DelayNote) {
    val title = delay.through?.let { stringResource(R.string.feature_tasks_delayed_through, it) }
        ?: stringResource(R.string.feature_tasks_flag_delayed)
    val why = listOfNotNull(delay.reason, delay.explanation).joinToString("\n")
    WaiveraBanner(
        title = title,
        message = if (why.isEmpty()) {
            stringResource(R.string.feature_tasks_delay_note)
        } else {
            stringResource(R.string.feature_tasks_delay_note_with_reason, why)
        },
        tone = BannerTone.Info,
        icon = WaiveraIcons.Schedule,
        modifier = Modifier.padding(top = WaiveraSpacing.Small, bottom = WaiveraSpacing.ExtraSmall),
    )
}

private const val SKELETON_ROWS = 3

/** More entries than this get a shortcut to the newest. */
private const val JUMP_AFTER = 5
private const val SKELETON_WIDE = 0.7f
private const val SKELETON_NARROW = 0.4f

@WaiveraPreviews
@Composable
private fun TaskDetailPreview() {
    WaiveraTheme {
        TaskDetailContent(
            state = TaskPreviewData.detailState(),
            snackbar = remember { SnackbarHostState() },
            actions = TaskDetailActions.None,
        )
    }
}
