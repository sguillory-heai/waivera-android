package com.waivera.waivera.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBusyIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraPullToRefreshBox
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.component.rememberTopBarAccount
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** The Tasks tab root. Owned by `:feature:tasks`; routed by `:app`. */
@Composable
fun TasksScreen(onAccountClick: () -> Unit, onOpenTask: (Int) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TasksViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TasksContent(
        state = state,
        actions = TasksActions(
            onAccountClick = onAccountClick,
            onWindow = viewModel::setWindow,
            onQueryChange = viewModel::onQueryChange,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onOpenTask = onOpenTask,
        ),
        modifier = modifier,
    )
}

class TasksActions(
    val onAccountClick: () -> Unit = {},
    val onWindow: (TaskWindow) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpenTask: (Int) -> Unit = {},
)

@Composable
fun TasksContent(state: TasksUiState, actions: TasksActions, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val refreshLabel = stringResource(R.string.feature_tasks_refresh)
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_tasks_title),
            account = rememberTopBarAccount(onClick = actions.onAccountClick),
        )
        TaskSearchField(value = state.query, onValueChange = actions.onQueryChange)
        WaiveraSegmentedControl(
            options = listOf(
                stringResource(R.string.feature_tasks_window_due_soon),
                stringResource(R.string.feature_tasks_window_overdue),
                stringResource(R.string.feature_tasks_window_all),
            ),
            selectedIndex = state.window.ordinal,
            onSelect = { actions.onWindow(TaskWindow.entries[it]) },
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
        )
        if (state.refreshFailed) {
            WaiveraBanner(
                message = stringResource(R.string.feature_tasks_refresh_failed),
                tone = BannerTone.Warning,
                actionLabel = stringResource(R.string.feature_tasks_try_again),
                onAction = actions.onRefresh,
                actionIcon = WaiveraIcons.Refresh,
            )
        }
        WaiveraPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .weight(1f)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(refreshLabel) {
                            actions.onRefresh()
                            true
                        },
                    )
                },
        ) {
            WaiveraLoadSwap(loading = state.phase == TasksPhase.Loading, modifier = Modifier.fillMaxSize()) { loading ->
                when {
                    loading -> if (rememberSkeletonVisible(loading = true)) TasksSkeleton()
                    state.phase == TasksPhase.Error -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            WaiveraErrorState(
                                title = stringResource(R.string.feature_tasks_error_title),
                                message = stringResource(state.errorMessage ?: R.string.feature_tasks_refresh_failed),
                                onRetry = actions.onRefresh,
                                modifier = Modifier.padding(top = WaiveraSpacing.Large),
                            )
                        }
                    }
                    state.phase == TasksPhase.Empty -> TasksEmpty(state, actions)
                    else -> TaskList(state, actions)
                }
            }
        }
    }
}

@Composable
private fun TaskSearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = WaiveraTheme.colors
    val focusManager = LocalFocusManager.current
    val placeholder = stringResource(R.string.feature_tasks_search)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
            .semantics { contentDescription = placeholder },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(text = placeholder, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = {
            Icon(
                painter = painterResource(WaiveraIcons.Search),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                WaiveraIconButton(
                    icon = WaiveraIcons.Close,
                    contentDescription = stringResource(R.string.feature_tasks_search_clear),
                    onClick = { onValueChange("") },
                    tint = colors.textSecondary,
                )
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = WaiveraShapes.Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.fieldBorder,
            cursorColor = colors.accent,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
        ),
    )
}

@Composable
private fun TasksEmpty(state: TasksUiState, actions: TasksActions) {
    // Inside a list so pull-to-refresh still works on an empty screen.
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (state.query.isNotBlank()) {
                WaiveraEmptyState(
                    title = stringResource(R.string.feature_tasks_no_match_title),
                    message = stringResource(R.string.feature_tasks_no_match_message),
                    illustration = WaiveraIllustration.Search,
                    actionLabel = stringResource(R.string.feature_tasks_clear_search),
                    onAction = { actions.onQueryChange("") },
                    modifier = Modifier.padding(top = WaiveraSpacing.Large),
                )
            } else {
                val (title, message) = when (state.window) {
                    TaskWindow.DueSoon ->
                        R.string.feature_tasks_empty_due_soon_title to
                            R.string.feature_tasks_empty_due_soon_message
                    TaskWindow.Overdue ->
                        R.string.feature_tasks_empty_overdue_title to
                            R.string.feature_tasks_empty_overdue_message
                    TaskWindow.AllOpen ->
                        R.string.feature_tasks_empty_all_title to
                            R.string.feature_tasks_empty_all_message
                }
                WaiveraEmptyState(
                    title = stringResource(title),
                    message = stringResource(message),
                    illustration = WaiveraIllustration.CaughtUp,
                    modifier = Modifier.padding(top = WaiveraSpacing.Large),
                )
            }
        }
    }
}

@Composable
private fun TaskList(state: TasksUiState, actions: TasksActions) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, state.canLoadMore, actions.onLoadMore)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = WaiveraSpacing.Gutter,
            end = WaiveraSpacing.Gutter,
            top = WaiveraSpacing.Small,
            bottom = WaiveraSpacing.Large,
        ),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        item(key = "meta") { TasksMeta(state) }
        items(state.rows, key = { it.id }) { row ->
            TaskCard(row = row, onClick = { actions.onOpenTask(row.id) }, modifier = Modifier.animateItem())
        }
        if (state.isLoadingMore) {
            item(key = "more") {
                val loadingMore = stringResource(R.string.feature_tasks_loading_more)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WaiveraSpacing.Medium)
                        .semantics { contentDescription = loadingMore },
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WaiveraBusyIndicator()
                }
            }
        }
    }
}

@Composable
private fun LoadMoreWhenNearEnd(listState: LazyListState, enabled: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }
}

@Composable
private fun TasksMeta(state: TasksUiState) {
    val text = if (state.query.isNotBlank()) {
        pluralStringResource(R.plurals.feature_tasks_meta_matches, state.totalCount, state.totalCount)
    } else {
        state.counts?.let { metaText(it) }
    } ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = WaiveraTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = WaiveraSpacing.ExtraSmall, vertical = WaiveraSpacing.XxSmall),
    )
}

@Composable
private fun metaText(counts: TaskCounts): String {
    val open = pluralStringResource(R.plurals.feature_tasks_meta_open, counts.open, counts.open)
    if (counts.overdue == 0) return open
    val overdue = pluralStringResource(R.plurals.feature_tasks_meta_overdue, counts.overdue, counts.overdue)
    return stringResource(R.string.feature_tasks_meta_separator, open, overdue)
}

@Composable
internal fun TaskCard(row: TaskRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val overdue = row.due.overdue
    WaiveraPressable(
        depth = PressDepth.Card,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(colors.surface, WaiveraShapes.SheetCard),
        onClickLabel = stringResource(R.string.feature_tasks_open_task),
        shape = WaiveraShapes.SheetCard,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.Top,
        ) {
            WaiveraIconTile(
                icon = R.drawable.feature_tasks_ic_task,
                tone = if (overdue) WaiveraTileTone.Danger else WaiveraTileTone.Amber,
                size = WaiveraSizes.CardTile,
                iconSize = WaiveraSizes.CardTileGlyph,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            ) {
                Text(
                    text = row.key,
                    style = WaiveraTextStyles.Kicker,
                    color = if (overdue) colors.dangerText else colors.textSecondary,
                )
                Text(text = row.title, style = MaterialTheme.typography.titleSmall, color = colors.text)
                row.about?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                Text(
                    text = row.due.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) colors.dangerText else colors.textSecondary,
                )
                WaiveraStatusBadge(text = row.state.label(), tone = TaskFormat.tone(row.state, overdue = false))
            }
            Icon(
                painter = painterResource(WaiveraIcons.ChevronRight),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
private fun TasksSkeleton() {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_tasks_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        repeat(SKELETON_CARDS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WaiveraTheme.colors.surface, WaiveraShapes.SheetCard)
                    .padding(WaiveraSpacing.MediumSmall),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            ) {
                SkeletonBlock(
                    modifier = Modifier.size(WaiveraSizes.CardTile),
                    shape = WaiveraShapes.Tile,
                    pulse = pulse,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
                    SkeletonText(style = WaiveraTextStyles.Kicker, widthFraction = KEY_LINE, pulse = pulse)
                    SkeletonText(style = MaterialTheme.typography.titleSmall, widthFraction = TITLE_LINE, pulse = pulse)
                    SkeletonText(style = MaterialTheme.typography.bodyMedium, widthFraction = DUE_LINE, pulse = pulse)
                }
            }
        }
    }
}

private const val LOAD_MORE_AHEAD = 4
private const val SKELETON_CARDS = 4
private const val KEY_LINE = 0.25f
private const val TITLE_LINE = 0.75f
private const val DUE_LINE = 0.45f

internal object TasksPreviewStates {
    val rows = listOf(
        TaskRow(
            991,
            "HEA-991",
            "Quarterly monitoring visit",
            "Jordan Ellis",
            DueText.WasOn("Oct 30"),
            TaskState.InProgress,
        ),
        TaskRow(
            1042,
            "HEA-1042",
            "Annual plan review",
            "Maya Thompson",
            DueText.On("Fri, Nov 6"),
            TaskState.NotStarted,
        ),
        TaskRow(
            1017,
            "HEA-1017",
            "Update emergency contacts",
            "Jordan Ellis",
            DueText.On("Mon, Nov 9"),
            TaskState.InProgress,
        ),
        TaskRow(987, "HEA-987", "Service authorization follow-up", "Luis Ortega", DueText.Tomorrow, TaskState.InReview),
    )

    fun populated() = TasksUiState(
        rows = rows,
        totalCount = rows.size,
        counts = TaskCounts(open = 5, overdue = 1),
        phase = TasksPhase.Content,
    )
}

@WaiveraPreviews
@Composable
private fun TasksPreview() {
    WaiveraTheme { TasksContent(state = TasksPreviewStates.populated(), actions = remember { TasksActions() }) }
}
