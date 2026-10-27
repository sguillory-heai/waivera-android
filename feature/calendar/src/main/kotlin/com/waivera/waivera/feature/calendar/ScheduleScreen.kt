package com.waivera.waivera.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraSegmentedControl
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonAlpha
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.component.rememberTopBarAccount
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.model.ScheduleText
import com.waivera.waivera.feature.calendar.model.ScheduleView
import com.waivera.waivera.feature.calendar.schedule.AgendaCard
import com.waivera.waivera.feature.calendar.schedule.AgendaState
import com.waivera.waivera.feature.calendar.schedule.ScheduleGrid
import com.waivera.waivera.feature.calendar.schedule.SchedulePreviewData
import com.waivera.waivera.feature.calendar.schedule.ScheduleUiState
import com.waivera.waivera.feature.calendar.schedule.ScheduleViewModel
import com.waivera.waivera.feature.calendar.ui.CalendarIcons
import com.waivera.waivera.feature.calendar.ui.currentLocale
import com.waivera.waivera.feature.calendar.ui.periodTitleText
import com.waivera.waivera.feature.calendar.ui.zoneNoticeText
import java.time.LocalDate

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
        WaiveraTopBar(
            title = stringResource(R.string.feature_calendar_schedule_title),
            subtitle = stringResource(R.string.feature_calendar_schedule_subtitle),
            actions = listOf(
                TopBarAction(
                    icon = WaiveraIcons.Refresh,
                    contentDescription = stringResource(R.string.feature_calendar_refresh),
                    onClick = actions.onRefresh,
                ),
            ),
            account = rememberTopBarAccount(
                onClick = actions.onAccountClick,
                fallbackName = stringResource(R.string.feature_calendar_account_name),
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = WaiveraSpacing.Large),
            ) {
                item(key = "banners") { ScheduleBanners(state, actions) }
                item(key = "controls") { ScheduleControls(state, actions) }
                item(key = "grid") {
                    ScheduleGrid(view = state.view, cells = state.cells, onSelectDate = actions.onSelectDate)
                }
                item(key = "agendaHeader") { AgendaHeader(state) }
                agendaItems(state, actions)
            }
        }
    }
}

private fun LazyListScope.agendaItems(state: ScheduleUiState, actions: ScheduleActions) {
    val itemModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
    when (val agenda = state.agenda) {
        AgendaState.Loading -> item(key = "loading") { AgendaSkeleton() }
        is AgendaState.Error -> item(key = "error") {
            WaiveraErrorState(
                title = stringResource(R.string.feature_calendar_error_title),
                message = stringResource(agenda.error.messageRes),
                onRetry = actions.onRetry,
                modifier = Modifier.padding(WaiveraSpacing.Gutter),
            )
        }
        AgendaState.Empty -> item(key = "empty") {
            WaiveraEmptyState(
                title = stringResource(R.string.feature_calendar_empty_title),
                message = stringResource(R.string.feature_calendar_empty_message),
                icon = WaiveraIcons.Calendar,
                modifier = Modifier.padding(WaiveraSpacing.Gutter),
            )
        }
        is AgendaState.Content -> items(agenda.items, key = { it.key }) { item ->
            val motion = WaiveraTheme.motion
            AgendaCard(
                item = item,
                currentYear = state.today.year,
                onOpenEvent = actions.onOpenEvent,
                modifier = itemModifier.animateItem(
                    fadeInSpec = motion.state(),
                    placementSpec = motion.layout(),
                    fadeOutSpec = motion.state(),
                ),
            )
        }
    }
}

@Composable
private fun ScheduleBanners(state: ScheduleUiState, actions: ScheduleActions) {
    val bannerModifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
    Column(modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall)) {
        if (state.isOffline) {
            WaiveraBanner(
                message = stringResource(R.string.feature_calendar_offline),
                tone = BannerTone.Offline,
                actionLabel = stringResource(R.string.feature_calendar_retry),
                onAction = actions.onRetry,
                modifier = bannerModifier,
            )
        } else if (state.feedError != null) {
            WaiveraBanner(
                message = stringResource(R.string.feature_calendar_feed_refresh_failed),
                tone = BannerTone.Error,
                actionLabel = stringResource(R.string.feature_calendar_retry),
                onAction = actions.onRetry,
                modifier = bannerModifier,
            )
        }
        if (state.milestonesError != null && !state.isOffline) {
            WaiveraBanner(
                message = stringResource(R.string.feature_calendar_milestones_failed),
                tone = BannerTone.Warning,
                actionLabel = stringResource(R.string.feature_calendar_retry),
                onAction = actions.onRetryMilestones,
                modifier = bannerModifier,
            )
        }
    }
}

@Composable
private fun ScheduleControls(state: ScheduleUiState, actions: ScheduleActions) {
    val views = ScheduleView.entries
    Column(
        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        WaiveraSegmentedControl(
            options = views.map { stringResource(it.labelRes()) },
            selectedIndex = views.indexOf(state.view),
            onSelect = { actions.onSelectView(views[it]) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaiveraIconButton(
                icon = CalendarIcons.ChevronLeft,
                contentDescription = stringResource(R.string.feature_calendar_previous_period),
                onClick = { actions.onStep(-1) },
            )
            Text(
                text = periodTitleText(state.title),
                style = MaterialTheme.typography.titleMedium,
                color = WaiveraTheme.colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            WaiveraIconButton(
                icon = WaiveraIcons.ChevronRight,
                contentDescription = stringResource(R.string.feature_calendar_next_period),
                onClick = { actions.onStep(1) },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            val notice = state.zoneNotice
            if (notice != null) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
                ) {
                    Icon(
                        painter = painterResource(WaiveraIcons.Info),
                        contentDescription = null,
                        tint = WaiveraTheme.colors.textSecondary,
                        modifier = Modifier.size(WaiveraSizes.IconSmall),
                    )
                    Text(
                        text = zoneNoticeText(notice),
                        style = MaterialTheme.typography.labelMedium,
                        color = WaiveraTheme.colors.textSecondary,
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            val goToToday = stringResource(R.string.feature_calendar_go_to_today)
            WaiveraButton(
                text = stringResource(R.string.feature_calendar_today),
                onClick = actions.onToday,
                variant = WaiveraButtonVariant.Secondary,
                modifier = Modifier.semantics { contentDescription = goToToday },
            )
        }
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
    val alpha = rememberSkeletonAlpha()
    Column(modifier = Modifier.clearAndSetSemantics { contentDescription = caption }) {
        repeat(SKELETON_ROWS) { SkeletonListRow(alpha = alpha) }
    }
}

private fun ScheduleView.labelRes(): Int = when (this) {
    ScheduleView.DAY -> R.string.feature_calendar_view_day
    ScheduleView.WEEK -> R.string.feature_calendar_view_week
    ScheduleView.MONTH -> R.string.feature_calendar_view_month
}

private const val SKELETON_ROWS = 3

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
