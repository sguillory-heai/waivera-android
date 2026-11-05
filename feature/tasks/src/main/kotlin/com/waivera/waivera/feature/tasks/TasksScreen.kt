package com.waivera.waivera.feature.tasks

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.designsystem.R as DesignR
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCountTone
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraPullToRefreshBox
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
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
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.core.designsystem.theme.swapTransition
import kotlinx.coroutines.launch

/** The Tasks tab root. Owned by `:feature:tasks`; routed by `:app`. */
@Composable
fun TasksScreen(
    onAccountClick: () -> Unit,
    onOpenTask: (Int) -> Unit,
    onOpenMyRequests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: TasksViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TasksContent(
        state = state,
        actions = TasksActions(
            onAccountClick = onAccountClick,
            onSegment = viewModel::setSegment,
            onWindow = viewModel::setWindow,
            onAssignedToMe = viewModel::setAssignedToMe,
            onDelays = viewModel::setDelays,
            onQueryChange = viewModel::onQueryChange,
            onRefresh = viewModel::refresh,
            onShowMore = viewModel::showMore,
            onShowFewer = viewModel::showFewer,
            onOpenTask = onOpenTask,
        ),
        requests = { RequestsScreen(onOpenTask = onOpenTask, onOpenMine = onOpenMyRequests) },
        modifier = modifier,
    )
}

@Suppress("LongParameterList") // One callback per control; every one has a default.
class TasksActions(
    val onAccountClick: () -> Unit = {},
    val onSegment: (TasksSegment) -> Unit = {},
    val onWindow: (TaskWindow) -> Unit = {},
    val onAssignedToMe: (Boolean) -> Unit = {},
    val onDelays: (DelayFilter) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onShowMore: (DueGroup) -> Unit = {},
    val onShowFewer: (DueGroup) -> Unit = {},
    val onOpenTask: (Int) -> Unit = {},
)

@Composable
fun TasksContent(
    state: TasksUiState,
    actions: TasksActions,
    modifier: Modifier = Modifier,
    requests: @Composable () -> Unit = {},
) {
    val colors = WaiveraTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_tasks_title),
            account = rememberTopBarAccount(onClick = actions.onAccountClick),
        )
        SegmentRow(state, actions.onSegment)
        val motion = WaiveraTheme.motion
        // Each segment slides in from the side it sits on, so the switch reads as moving along the row.
        AnimatedContent(
            targetState = state.segment,
            transitionSpec = { motion.swapTransition(direction = targetState.ordinal - initialState.ordinal) },
            modifier = Modifier.weight(1f),
            label = "tasksSegment",
        ) { segment ->
            if (segment == TasksSegment.Requests) {
                Box(Modifier.fillMaxSize()) { requests() }
            } else {
                TaskListPane(state, actions)
            }
        }
    }
}

@Composable
private fun SegmentRow(state: TasksUiState, onSegment: (TasksSegment) -> Unit) {
    val segments = state.segments
    val labels = segments.map { it.label() }
    WaiveraSegmentedControl(
        options = labels,
        selectedIndex = segments.indexOf(state.segment).coerceAtLeast(0),
        onSelect = { onSegment(segments[it]) },
        counts = segments.map { if (it == TasksSegment.Requests) state.waitingCount else null },
        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
    )
}

@Composable
private fun TasksSegment.label(): String = stringResource(
    when (this) {
        TasksSegment.Casework -> R.string.feature_tasks_segment_casework
        TasksSegment.MyWork -> R.string.feature_tasks_segment_my_work
        TasksSegment.Requests -> R.string.feature_tasks_segment_requests
    },
)

@Composable
private fun TaskListPane(state: TasksUiState, actions: TasksActions) {
    val refreshLabel = stringResource(R.string.feature_tasks_refresh)
    Column(Modifier.fillMaxSize()) {
        TaskSearchField(
            value = state.query,
            placeholder = stringResource(
                if (state.segment == TasksSegment.MyWork) {
                    R.string.feature_tasks_search_mine
                } else {
                    R.string.feature_tasks_search_casework
                },
            ),
            onValueChange = actions.onQueryChange,
        )
        FilterChips(state, actions)
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

/** The due windows, then (casework only) Assigned to me and Delays. One row that scrolls at large font. */
@Composable
private fun FilterChips(state: TasksUiState, actions: TasksActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        TaskWindow.entries.forEach { window ->
            WaiveraFilterChip(
                label = stringResource(
                    when (window) {
                        TaskWindow.DueSoon -> R.string.feature_tasks_window_due_soon
                        TaskWindow.Overdue -> R.string.feature_tasks_window_overdue
                        TaskWindow.AllOpen -> R.string.feature_tasks_window_all
                    },
                ),
                selected = window == state.window,
                onClick = { actions.onWindow(window) },
            )
        }
        if (state.segment == TasksSegment.Casework) {
            WaiveraFilterChip(
                label = stringResource(R.string.feature_tasks_assigned_to_me),
                selected = state.assignedToMe,
                onClick = { actions.onAssignedToMe(!state.assignedToMe) },
                leadingIcon = WaiveraIcons.Person,
            )
        }
        if (state.showsDelays) DelaysChip(selected = state.delays, onSelect = actions.onDelays)
    }
}

@Composable
private fun TaskSearchField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val colors = WaiveraTheme.colors
    val focusManager = LocalFocusManager.current
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
                val (title, message) = if (state.showsDelays && state.delays == DelayFilter.Only) {
                    R.string.feature_tasks_empty_delayed_title to R.string.feature_tasks_empty_delayed_message
                } else {
                    emptyCopy(state)
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

private fun emptyCopy(state: TasksUiState): Pair<Int, Int> = when (state.window) {
    TaskWindow.DueSoon ->
        R.string.feature_tasks_empty_due_soon_title to R.string.feature_tasks_empty_due_soon_message
    TaskWindow.Overdue ->
        R.string.feature_tasks_empty_overdue_title to R.string.feature_tasks_empty_overdue_message
    TaskWindow.AllOpen ->
        R.string.feature_tasks_empty_all_title to
            when {
                state.segment == TasksSegment.MyWork -> R.string.feature_tasks_empty_mine_message
                state.assignedToMe -> R.string.feature_tasks_empty_assigned_message
                else -> R.string.feature_tasks_empty_casework_message
            }
}

@Composable
private fun TaskList(state: TasksUiState, actions: TasksActions) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Each header's place in the list (after the meta line), for folding a long section back.
    val headerIndex = remember(state.sections) {
        var index = 1
        state.sections.associate { section ->
            (section.group to index).also { index += 1 + section.rows.size + if (section.more != null) 1 else 0 }
        }
    }
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
        state.sections.forEach { section ->
            item(key = "group-${section.group}") {
                GroupHeader(section, Modifier.animateItem())
            }
            items(section.rows, key = { it.id }) { row ->
                TaskCard(row = row, onClick = { actions.onOpenTask(row.id) }, modifier = Modifier.animateItem())
            }
            section.more?.let { more ->
                item(key = "more-${section.group}") {
                    SectionMoreButton(
                        group = section.group,
                        more = more,
                        onShowMore = { actions.onShowMore(section.group) },
                        onShowFewer = {
                            actions.onShowFewer(section.group)
                            // Folding a long section would leave the person far below it; bring its header back.
                            val header = headerIndex[section.group] ?: return@SectionMoreButton
                            if (listState.firstVisibleItemIndex > header) {
                                scope.launch { listState.animateScrollToItem(header) }
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/**
 * Under a section that holds more than its first rows: "Show 42 more" opens the next batch in
 * place, and once it is all open "Show fewer" folds it back. The header keeps the real total.
 */
@Composable
internal fun SectionMoreButton(
    group: DueGroup,
    more: SectionMore,
    onShowMore: () -> Unit,
    onShowFewer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupName = stringResource(group.titleRes)
    val (text, description) = when (more) {
        is SectionMore.ShowMore -> if (more.failed) {
            stringResource(R.string.feature_tasks_section_more_failed) to
                stringResource(R.string.feature_tasks_section_more_failed_description, groupName)
        } else {
            pluralStringResource(R.plurals.feature_tasks_section_more, more.hidden, more.hidden) to
                pluralStringResource(
                    R.plurals.feature_tasks_section_more_description,
                    more.hidden,
                    more.hidden,
                    groupName,
                )
        }
        SectionMore.ShowFewer -> stringResource(R.string.feature_tasks_section_fewer) to
            stringResource(R.string.feature_tasks_section_fewer_description, groupName)
    }
    WaiveraButton(
        text = text,
        onClick = if (more is SectionMore.ShowMore) onShowMore else onShowFewer,
        variant = WaiveraButtonVariant.Tonal,
        fullWidth = true,
        leadingIcon = when {
            more is SectionMore.ShowFewer -> DesignR.drawable.core_designsystem_ic_expand_less
            more is SectionMore.ShowMore && more.failed -> DesignR.drawable.core_designsystem_ic_refresh
            else -> DesignR.drawable.core_designsystem_ic_expand_more
        },
        loadingText = stringResource(R.string.feature_tasks_loading_more).takeIf {
            more is SectionMore.ShowMore && more.loading
        },
        size = WaiveraButtonSize.Card,
        // While busy the button reads its loading text, so TalkBack hears that it is working.
        modifier = if (more is SectionMore.ShowMore && more.loading) {
            modifier
        } else {
            modifier.semantics { contentDescription = description }
        },
    )
}

private val DueGroup.titleRes: Int
    get() = when (this) {
        DueGroup.Overdue -> R.string.feature_tasks_group_overdue
        DueGroup.Delayed -> R.string.feature_tasks_group_delayed
        DueGroup.ThisWeek -> R.string.feature_tasks_group_this_week
        DueGroup.NextWeek -> R.string.feature_tasks_group_next_week
        DueGroup.Later -> R.string.feature_tasks_group_later
        DueGroup.NoDue -> R.string.feature_tasks_group_no_due
    }

@Composable
private fun TasksMeta(state: TasksUiState) {
    val text = if (state.query.isNotBlank()) {
        pluralStringResource(R.plurals.feature_tasks_meta_matches, state.totalCount, state.totalCount)
    } else {
        state.counts?.let { metaText(it, delaysHidden = state.showsDelays && state.delays == DelayFilter.Hide) }
    } ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = WaiveraTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = WaiveraSpacing.ExtraSmall, vertical = WaiveraSpacing.XxSmall),
    )
}

@Composable
private fun metaText(counts: TaskCounts, delaysHidden: Boolean): String {
    // With delays hidden, "open" counts only what the list can show.
    val openCount = if (delaysHidden) (counts.open - counts.delayed).coerceAtLeast(0) else counts.open
    val open = pluralStringResource(R.plurals.feature_tasks_meta_open, openCount, openCount)
    val overdue = counts.overdue.takeIf { it > 0 }
        ?.let { pluralStringResource(R.plurals.feature_tasks_meta_overdue, it, it) }
    // With delays hidden, the line says so instead of counting what the list leaves out.
    val delayed = when {
        delaysHidden -> stringResource(R.string.feature_tasks_meta_delays_hidden)
        counts.delayed > 0 -> pluralStringResource(R.plurals.feature_tasks_meta_delayed, counts.delayed, counts.delayed)
        else -> null
    }
    var text = open
    // Each later part joins with the same separator: "14 open · 1 overdue · 2 delayed".
    listOfNotNull(overdue, delayed).forEach { part ->
        text = stringResource(R.string.feature_tasks_meta_separator, text, part)
    }
    return text
}

@Composable
private fun GroupHeader(section: TaskSection, modifier: Modifier = Modifier) {
    // The server's total for the group, not the rows shown, so it matches the line above.
    val count = section.total
    WaiveraSectionHeader(
        title = stringResource(section.group.titleRes),
        count = count,
        countDescription = pluralStringResource(R.plurals.feature_tasks_group_count, count, count),
        countTone = if (section.group == DueGroup.Overdue) WaiveraCountTone.Attention else WaiveraCountTone.Info,
        modifier = modifier.padding(top = WaiveraSpacing.Small),
    )
}

/**
 * At a glance: the due date as a tile, the title, who it is about, one line of facts (lateness in
 * the attention colour, then the key, then another owner) and at most one badge.
 */
@Composable
internal fun TaskCard(row: TaskRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val large = isLargeFontScale()
    WaiveraPressable(
        depth = PressDepth.Card,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(colors.surface, WaiveraShapes.SheetCard)
            .then(
                // Shadows vanish on the dark background, so a hairline keeps the card's edge.
                if (colors.isDark) {
                    Modifier.border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.SheetCard)
                } else {
                    Modifier
                },
            ),
        onClickLabel = stringResource(R.string.feature_tasks_open_task),
        shape = WaiveraShapes.SheetCard,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(WaiveraSpacing.MediumSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.Top,
        ) {
            DateTile(row)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    maxLines = if (large) LARGE_TITLE_LINES else TITLE_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                row.about?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = metaLine(row, large),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                row.flag?.let { flag ->
                    WaiveraStatusBadge(
                        text = flag.label(),
                        tone = when (flag) {
                            RowFlag.ClosureRequested, RowFlag.InReview -> BadgeTone.Warning
                            RowFlag.InProgress -> BadgeTone.Accent
                        },
                        modifier = Modifier.padding(top = WaiveraSpacing.XxSmall),
                    )
                }
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

/**
 * The due date as a tile; really overdue turns it red, while a delay keeps it brand violet (the
 * web's purple "Delayed" ink). TalkBack reads the full due text instead.
 */
@Composable
private fun DateTile(row: TaskRow) {
    val colors = WaiveraTheme.colors
    val tint = if (row.due.overdue && !row.delayed) colors.tiles.danger else colors.tiles.brand
    val dueText = row.due.text()
    Column(
        modifier = Modifier
            .widthIn(min = WaiveraSizes.CardTile + WaiveraSpacing.Small)
            .heightIn(min = WaiveraSizes.CardTile + WaiveraSpacing.Small)
            .background(tint.container, WaiveraShapes.Tile)
            .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.ExtraSmall)
            .clearAndSetSemantics { contentDescription = dueText },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val month = row.tileMonth
        val day = row.tileDay
        if (month != null && day != null) {
            Text(
                month.uppercase(),
                style = WaiveraTextStyles.Kicker,
                color = tint.content,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = day,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = tint.content,
                maxLines = 1,
                softWrap = false,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.feature_tasks_ic_task),
                contentDescription = null,
                tint = tint.content,
                modifier = Modifier.size(WaiveraSizes.CardTileGlyph),
            )
        }
    }
}

/**
 * Lateness (attention colour) or the delay (accent colour), the key, then another owner on its own
 * line. At 200% each fact stacks.
 */
@Composable
private fun metaLine(row: TaskRow, large: Boolean): AnnotatedString {
    val danger = WaiveraTheme.colors.dangerText
    // The link ink is the accent tuned to read as text in both themes.
    val accent = WaiveraTheme.colors.link
    val late = row.daysLate?.let { pluralStringResource(R.plurals.feature_tasks_days_late, it, it) }
    val delay = when {
        !row.delayed -> null
        row.delayedThrough != null -> stringResource(R.string.feature_tasks_delayed_through, row.delayedThrough.nbsp())
        else -> stringResource(R.string.feature_tasks_flag_delayed)
    }
    val owner = row.assignee?.let { stringResource(R.string.feature_tasks_assigned_to, it.replace(' ', NBSP)) }
    val separator = if (large) "\n" else META_SEPARATOR
    return buildAnnotatedString {
        if (late != null) {
            withStyle(SpanStyle(color = danger)) { append(late) }
            append(separator)
        }
        if (delay != null) {
            withStyle(SpanStyle(color = accent)) { append(delay) }
            append(separator)
        }
        append(row.key)
        if (owner != null) {
            append("\n")
            append(owner)
        }
    }
}

@Composable
private fun RowFlag.label(): String = when (this) {
    RowFlag.ClosureRequested -> stringResource(R.string.feature_tasks_flag_closure)
    RowFlag.InProgress -> stringResource(R.string.feature_tasks_state_in_progress)
    RowFlag.InReview -> stringResource(R.string.feature_tasks_state_in_review)
}

@Composable
internal fun TasksSkeleton(cards: Int = SKELETON_CARDS) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_tasks_loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        repeat(cards) {
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

private const val TITLE_LINES = 2
private const val LARGE_TITLE_LINES = 4
private const val META_SEPARATOR = "\u00A0· "
private const val NBSP = '\u00A0'

/** Keeps "Nov 20" on one line. */
private fun String.nbsp(): String = replace(' ', NBSP)

private const val SKELETON_CARDS = 4
private const val KEY_LINE = 0.25f
private const val TITLE_LINE = 0.75f
private const val DUE_LINE = 0.45f

internal object TasksPreviewStates {
    val rows = listOf(
        TaskRow(
            991, "HEA-991", "Quarterly monitoring visit", "Jordan Ellis", DueText.WasOn("Oct 30"), TaskState.InProgress,
            tileMonth = "Oct", tileDay = "30", delayed = true, delayedThrough = "Nov 20", group = DueGroup.Delayed,
        ),
        TaskRow(
            1078, "HEA-1078", "Annual plan signatures", "Sam Lee", DueText.WasOn("Oct 31"), TaskState.NotStarted,
            tileMonth = "Oct", tileDay = "31", daysLate = 4, assignee = "Luis Ortega", group = DueGroup.Overdue,
        ),
        TaskRow(
            1042, "HEA-1042", "Annual plan review", "Maya Thompson", DueText.On("Fri, Nov 6"), TaskState.NotStarted,
            tileMonth = "Nov", tileDay = "6", group = DueGroup.ThisWeek,
        ),
        TaskRow(
            987, "HEA-987", "Service authorization follow-up", "Luis Ortega", DueText.Tomorrow, TaskState.InReview,
            tileMonth = "Nov", tileDay = "5", assignee = "Priya Raman", flag = RowFlag.InReview,
            group = DueGroup.ThisWeek,
        ),
        TaskRow(
            1017, "HEA-1017", "Update emergency contacts", "Jordan Ellis",
            DueText.On(
                "Mon, Nov 9",
            ),
            TaskState.InProgress,
            tileMonth = "Nov", tileDay = "9", flag = RowFlag.ClosureRequested, group = DueGroup.NextWeek,
        ),
    )

    fun populated(showsRequests: Boolean = true) = TasksUiState(
        sections = rows.groupBy { it.group }.toSortedMap().map { (group, items) -> TaskSection(group, items) },
        totalCount = rows.size,
        counts = TaskCounts(open = 14, overdue = 1, delayed = 1),
        phase = TasksPhase.Content,
        showsRequests = showsRequests,
        waitingCount = if (showsRequests) 2 else 0,
    )
}

@WaiveraPreviews
@Composable
private fun TasksPreview() {
    WaiveraTheme { TasksContent(state = TasksPreviewStates.populated(), actions = remember { TasksActions() }) }
}

@WaiveraPreviews
@Composable
private fun SectionMorePreview() {
    WaiveraTheme {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            SectionMoreButton(DueGroup.Overdue, SectionMore.ShowMore(hidden = 42), onShowMore = {}, onShowFewer = {})
            SectionMoreButton(DueGroup.Delayed, SectionMore.ShowMore(hidden = 13, loading = true), {}, {})
            SectionMoreButton(DueGroup.ThisWeek, SectionMore.ShowMore(hidden = 8, failed = true), {}, {})
            SectionMoreButton(DueGroup.Later, SectionMore.ShowFewer, onShowMore = {}, onShowFewer = {})
        }
    }
}
