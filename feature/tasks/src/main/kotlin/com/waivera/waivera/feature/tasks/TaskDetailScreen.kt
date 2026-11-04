package com.waivera.waivera.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraBottomActionBar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** One task: its details, recent activity and Log activity. Owned by `:feature:tasks`; routed by `:app`. */
@Composable
fun TaskDetailScreen(taskId: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
                TaskDetailEffect.Logged -> snackbar.showSnackbar(resources.getString(R.string.feature_tasks_log_done))
                TaskDetailEffect.WebUnavailable ->
                    snackbar.showSnackbar(resources.getString(R.string.feature_tasks_web_unavailable))
                is TaskDetailEffect.OpenWeb -> {
                    val opened = runCatching { uriHandler.openUri(effect.url) }.isSuccess
                    if (!opened) snackbar.showSnackbar(resources.getString(R.string.feature_tasks_web_failed))
                }
            }
        }
    }
    TaskDetailContent(
        state = state,
        snackbar = snackbar,
        actions = TaskDetailActions(
            onBack = onBack,
            onRetry = viewModel::load,
            onRetryActivity = viewModel::loadActivity,
            onLog = viewModel::startLog,
            onOpenWeb = viewModel::openOnWeb,
        ),
        modifier = modifier,
    )
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
) {
    companion object {
        val None = TaskDetailActions({}, {}, {}, {}, {})
    }
}

@Composable
internal fun TaskDetailContent(
    state: TaskDetailUiState,
    snackbar: SnackbarHostState,
    actions: TaskDetailActions,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val task = state.task
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        WaiveraTopBar(
            title = task?.title ?: stringResource(R.string.feature_tasks_title),
            subtitle = task?.key,
            onBack = actions.onBack,
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
                    else -> DetailBody(state, task, actions.onRetryActivity)
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
                WaiveraButton(
                    text = stringResource(R.string.feature_tasks_open_on_web),
                    onClick = actions.onOpenWeb,
                    variant = WaiveraButtonVariant.Text,
                    fullWidth = true,
                    leadingIcon = WaiveraIcons.OpenExternal,
                )
            }
        }
    }
}

@Composable
private fun DetailBody(state: TaskDetailUiState, task: TaskDetail, onRetryActivity: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = WaiveraSpacing.Large),
    ) {
        WaiveraSectionHeader(text = stringResource(R.string.feature_tasks_detail_section), first = true)
        WaiveraCardGroup {
            Column(Modifier.fillMaxWidth().padding(vertical = WaiveraSpacing.MediumSmall)) {
                InfoRow(stringResource(R.string.feature_tasks_field_status), R.drawable.feature_tasks_ic_task) {
                    WaiveraStatusBadge(text = task.state.label(), tone = TaskFormat.tone(task.state, state.due.overdue))
                }
                InfoDivider()
                InfoRow(stringResource(R.string.feature_tasks_field_due), WaiveraIcons.Calendar) {
                    Text(
                        text = state.dueDate ?: stringResource(R.string.feature_tasks_no_due),
                        style = WaiveraTextStyles.FieldValue,
                        color = if (state.due.overdue) WaiveraTheme.colors.dangerText else WaiveraTheme.colors.text,
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
        WaiveraSectionHeader(text = stringResource(R.string.feature_tasks_activity_section))
        ActivitySection(state.activity, onRetryActivity)
    }
}

@Composable
private fun ActivitySection(activity: Loadable<List<ActivityRow>>, onRetry: () -> Unit) {
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
            is Loadable.Ready -> if (activity.value.isEmpty()) {
                Text(
                    text = stringResource(R.string.feature_tasks_activity_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.padding(WaiveraSpacing.Medium),
                )
            } else {
                activity.value.forEachIndexed { index, row ->
                    if (index > 0) WaiveraInsetDivider()
                    ActivityItem(row)
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(row: ActivityRow) {
    val who = if (row.byMe) stringResource(R.string.feature_tasks_activity_by_you) else row.author
    val whenText = row.moment?.text()
    val supporting = listOfNotNull(whenText, who).joinToString(SEPARATOR)
    WaiveraListRow(
        headline = stringResource(R.string.feature_tasks_activity_row, row.type, durationText(row.minutes)),
        supporting = listOfNotNull(supporting.ifBlank { null }, row.description?.ifBlank { null }).joinToString("\n")
            .ifBlank { null },
        leading = {
            WaiveraIconTile(
                icon = R.drawable.feature_tasks_ic_activity,
                tone = if (row.isNew) WaiveraTileTone.Teal else WaiveraTileTone.Brand,
                size = WaiveraSizes.FieldTile,
                iconSize = WaiveraSizes.FieldTileGlyph,
            )
        },
    )
}

@Composable
private fun InfoRow(label: String, icon: Int, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaiveraIconTile(
            icon = icon,
            tone = WaiveraTileTone.Brand,
            size = WaiveraSizes.FieldTile,
            iconSize = WaiveraSizes.FieldTileGlyph,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(text = label, style = WaiveraTextStyles.FieldLabel, color = WaiveraTheme.colors.textSecondary)
            value()
        }
    }
}

@Composable
private fun InfoValue(text: String) {
    Text(text = text, style = WaiveraTextStyles.FieldValue, color = WaiveraTheme.colors.text)
}

@Composable
private fun InfoDivider() {
    WaiveraInsetDivider(
        inset = WaiveraSpacing.Medium + WaiveraSizes.FieldDividerInset,
        modifier = Modifier.padding(end = WaiveraSpacing.Medium),
    )
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

private const val SEPARATOR = " · "
private const val SKELETON_ROWS = 3
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
