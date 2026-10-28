package com.waivera.waivera.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonListRow
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.component.rememberTopBarAccount
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.calendar.model.AgendaNow
import com.waivera.waivera.feature.calendar.model.ScheduleText
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.schedule.AgendaCard
import com.waivera.waivera.feature.calendar.schedule.AgendaState
import com.waivera.waivera.feature.calendar.schedule.NowDivider
import com.waivera.waivera.feature.calendar.schedule.ScheduleGrid
import com.waivera.waivera.feature.calendar.schedule.SchedulePreviewData
import com.waivera.waivera.feature.calendar.schedule.ScheduleUiState
import com.waivera.waivera.feature.calendar.schedule.ScheduleViewModel
import com.waivera.waivera.feature.calendar.schedule.usesCompactCalendar
import com.waivera.waivera.feature.calendar.ui.CalendarIcons
import com.waivera.waivera.feature.calendar.ui.currentLocale
import com.waivera.waivera.feature.calendar.ui.periodTitleText
import com.waivera.waivera.feature.calendar.ui.zoneNoticeText
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * Entry point for the schedule tab. Owned by `:feature:calendar`; routed by `:app`.
 */
@Composable
fun ScheduleScreen(onAccountClick: () -> Unit, onOpenEvent: (eventId: String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: ScheduleViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // Keeps today's "Now" divider and the ended cards current while the screen stays open.
    LaunchedEffect(viewModel) {
        while (true) {
            delay(NOW_TICK_MILLIS)
            viewModel.onResume()
        }
    }
    PreferWeekAtLargeFont(view = state.view, onSelectView = viewModel::selectView)
    ScheduleContent(
        state = state,
        actions = ScheduleActions(
            onAccountClick = onAccountClick,
            onOpenEvent = onOpenEvent,
            onSelectView = viewModel::selectView,
            onSelectDate = viewModel::selectDate,
            onStep = viewModel::step,
            onToday = viewModel::goToToday,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onRetryMilestones = viewModel::retryMilestones,
        ),
        modifier = modifier,
    )
}

/** Everything the schedule screen can ask for. */
@Immutable
class ScheduleActions(
    val onAccountClick: () -> Unit = {},
    val onOpenEvent: (String) -> Unit = {},
    val onSelectView: (ScheduleView) -> Unit = {},
    val onSelectDate: (LocalDate) -> Unit = {},
    val onStep: (Int) -> Unit = {},
    val onToday: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryMilestones: () -> Unit = {},
)

/** Stateless schedule screen, for previews and screenshot tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleContent(state: ScheduleUiState, actions: ScheduleActions, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val jumpToToday = stringResource(R.string.feature_calendar_go_to_today)
        val refreshLabel = stringResource(R.string.feature_calendar_refresh)
        WaiveraTopBar(
            title = stringResource(R.string.feature_calendar_schedule_title),
            // Refresh is pull-to-refresh (and a TalkBack action on the list), not a third header icon.
            actions = if (state.showsJumpToToday) {
                listOf(TopBarAction(WaiveraIcons.JumpToToday, jumpToToday, actions.onToday))
            } else {
                emptyList()
            },
            account = rememberTopBarAccount(
                onClick = actions.onAccountClick,
                fallbackName = "",
            ),
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(refreshLabel) {
                                actions.onRefresh()
                                true
                            },
                        )
                    },
                contentPadding = PaddingValues(bottom = WaiveraSpacing.Large),
            ) {
                item(key = "views") { ViewPicker(state, actions) }
                item(key = "banners") { ScheduleBanners(state, actions) }
                item(key = "period") { PeriodRow(state, actions) }
                item(key = "grid") {
                    ScheduleGrid(view = state.view, cells = state.cells, onSelectDate = actions.onSelectDate)
                }
                // The Day view's period row already names the day (UI2-27); a second heading repeated it.
                if (state.agenda !is AgendaState.Error && state.view != ScheduleView.DAY) {
                    item(key = "agendaHeader") { AgendaHeader(state) }
                }
                agendaItems(state, actions)
            }
        }
    }
}

private fun LazyListScope.agendaItems(state: ScheduleUiState, actions: ScheduleActions) {
    val itemModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
    when (val agenda = state.agenda) {
        AgendaState.Loading -> item(key = "loading") { AgendaSkeleton() }
        // The failure is the banner above the grid, where it is seen without scrolling.
        is AgendaState.Error -> Unit
        AgendaState.Empty -> item(key = "empty") {
            WaiveraEmptyState(
                title = stringResource(R.string.feature_calendar_empty_title),
                message = stringResource(R.string.feature_calendar_empty_message),
                icon = WaiveraIcons.Calendar,
                modifier = Modifier.padding(WaiveraSpacing.Gutter),
            )
        }
        is AgendaState.Content -> {
            val now = state.agendaNow
            agenda.items.forEach { card ->
                if (now != null && now.beforeKey == card.key) nowItem(now)
                item(key = card.key) {
                    val motion = WaiveraTheme.motion
                    AgendaCard(
                        item = card,
                        currentYear = state.today.year,
                        onOpenEvent = actions.onOpenEvent,
                        ended = now != null && card.key in now.endedKeys,
                        modifier = itemModifier.animateItem(
                            fadeInSpec = motion.state(),
                            placementSpec = motion.layout(),
                            fadeOutSpec = motion.state(),
                        ),
                    )
                }
            }
            if (now != null && now.beforeKey == null) nowItem(now)
        }
    }
}

private fun LazyListScope.nowItem(now: AgendaNow) {
    item(key = NOW_KEY) {
        NowDivider(
            time = now.time,
            modifier = Modifier
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
                .animateItem(),
        )
    }
}

private const val NOW_KEY = "now"

/**
 * One status signal under the view picker, above the grid, so a failure is seen without
 * scrolling: a failed first load wins over offline, offline wins over a failed refresh.
 */
@Composable
private fun ScheduleBanners(state: ScheduleUiState, actions: ScheduleActions) {
    val bannerModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
    val retry = stringResource(R.string.feature_calendar_retry)
    val agenda = state.agenda
    Column {
        when {
            agenda is AgendaState.Error -> WaiveraBanner(
                title = stringResource(R.string.feature_calendar_error_title),
                message = stringResource(agenda.error.messageRes),
                tone = BannerTone.Error,
                actionLabel = retry,
                onAction = actions.onRetry,
                actionIcon = WaiveraIcons.Refresh,
                modifier = bannerModifier,
            )
            state.isOffline -> WaiveraBanner(
                message = stringResource(R.string.feature_calendar_offline),
                tone = BannerTone.Offline,
                actionLabel = retry,
                onAction = actions.onRetry,
                actionIcon = WaiveraIcons.Refresh,
                modifier = bannerModifier,
            )
            // A stale-data notice, so it follows the status-banner rule: compact at large font,
            // with the title saying what happened and the detail a tap away (UI4-05).
            state.feedError != null -> WaiveraBanner(
                title = stringResource(R.string.feature_calendar_feed_refresh_failed_title),
                message = stringResource(R.string.feature_calendar_feed_refresh_failed_detail),
                // The saved schedule is still on screen, so this is a warning; red is for a failed action.
                tone = BannerTone.Warning,
                actionLabel = retry,
                onAction = actions.onRetry,
                actionIcon = WaiveraIcons.Refresh,
                compact = isLargeFontScale(),
                modifier = bannerModifier,
            )
        }
        if (state.milestonesError != null && !state.isOffline && agenda !is AgendaState.Error) {
            WaiveraBanner(
                message = stringResource(R.string.feature_calendar_milestones_failed),
                tone = BannerTone.Warning,
                actionLabel = retry,
                onAction = actions.onRetryMilestones,
                actionIcon = WaiveraIcons.Refresh,
                modifier = bannerModifier,
            )
        }
        val notice = state.zoneNotice
        if (notice != null && notice.isFallback) {
            WaiveraBanner(message = zoneNoticeText(notice), tone = BannerTone.Info, modifier = bannerModifier)
        }
    }
}

@Composable
private fun ViewPicker(state: ScheduleUiState, actions: ScheduleActions) {
    val views = ScheduleView.entries
    WaiveraSegmentedControl(
        options = views.map { stringResource(it.labelRes()) },
        selectedIndex = views.indexOf(state.view),
        onSelect = { actions.onSelectView(views[it]) },
        modifier = Modifier.padding(
            start = WaiveraSpacing.Gutter,
            end = WaiveraSpacing.Gutter,
            top = WaiveraSpacing.Small,
            bottom = WaiveraSpacing.ExtraSmall,
        ),
    )
}

/**
 * Previous, the period title, next; the saved zone is a quiet caption under the title. In the Day
 * view the title is the only day heading, so it carries the Today chip.
 */
@Composable
private fun PeriodRow(state: ScheduleUiState, actions: ScheduleActions) {
    Column(modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaiveraIconButton(
                icon = CalendarIcons.ChevronLeft,
                contentDescription = stringResource(R.string.feature_calendar_previous_period),
                onClick = { actions.onStep(-1) },
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = periodTitleText(state.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = WaiveraTheme.colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                if (state.view == ScheduleView.DAY && state.isTodaySelected) {
                    WaiveraStatusBadge(text = stringResource(R.string.feature_calendar_today), tone = BadgeTone.Accent)
                }
            }
            WaiveraIconButton(
                icon = WaiveraIcons.ChevronRight,
                contentDescription = stringResource(R.string.feature_calendar_next_period),
                onClick = { actions.onStep(1) },
            )
        }
        val notice = state.zoneNotice
        if (notice != null && !notice.isFallback) {
            Text(
                text = zoneNoticeText(notice),
                style = MaterialTheme.typography.labelMedium,
                color = WaiveraTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * At 150% font and above a month grid fills the first screen, so the first time the tab shows
 * Month at that size it switches to the week strip. Picking Month again afterwards sticks.
 */
@Composable
private fun PreferWeekAtLargeFont(view: ScheduleView, onSelectView: (ScheduleView) -> Unit) {
    val compact = usesCompactCalendar(LocalDensity.current.fontScale)
    var applied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(compact) {
        if (!applied && compact && view == ScheduleView.MONTH) onSelectView(ScheduleView.WEEK)
        applied = true
    }
}

@Composable
private fun AgendaHeader(state: ScheduleUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = WaiveraSpacing.Gutter,
                end = WaiveraSpacing.Gutter,
                top = WaiveraSpacing.Medium,
                bottom = WaiveraSpacing.Small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Text(
            text = ScheduleText.fullDay(state.selectedDate, currentLocale()),
            style = MaterialTheme.typography.titleMedium,
            color = WaiveraTheme.colors.text,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics { heading() },
        )
        if (state.isTodaySelected) {
            WaiveraStatusBadge(text = stringResource(R.string.feature_calendar_today), tone = BadgeTone.Accent)
        }
    }
}

@Composable
private fun AgendaSkeleton() {
    val visible = rememberSkeletonVisible(loading = true)
    val caption = stringResource(R.string.feature_calendar_loading)
    if (!visible) return
    val pulse = rememberSkeletonPulse()
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = caption }) {
        repeat(SKELETON_ROWS) { SkeletonListRow(pulse = pulse) }
    }
}

private fun ScheduleView.labelRes(): Int = when (this) {
    ScheduleView.DAY -> R.string.feature_calendar_view_day
    ScheduleView.WEEK -> R.string.feature_calendar_view_week
    ScheduleView.MONTH -> R.string.feature_calendar_view_month
}

private const val SKELETON_ROWS = 3
private const val NOW_TICK_MILLIS = 60_000L

@WaiveraPreviews
@Composable
private fun ScheduleContentPreview() {
    WaiveraTheme {
        ScheduleContent(state = SchedulePreviewData.month(), actions = ScheduleActions())
    }
}

@WaiveraPreviews
@Composable
private fun ScheduleContentWeekPreview() {
    WaiveraTheme {
        ScheduleContent(state = SchedulePreviewData.week(), actions = ScheduleActions())
    }
}

/** Today in the Day view: the "Now" divider and the ended overnight shift (UI4-12). */
@WaiveraPreviews
@Composable
private fun ScheduleContentDayPreview() {
    WaiveraTheme {
        ScheduleContent(state = SchedulePreviewData.state(ScheduleView.DAY), actions = ScheduleActions())
    }
}
