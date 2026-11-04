package com.waivera.waivera.feature.today.notices

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.TopBarAction
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraPullToRefreshBox
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.PastLabel
import com.waivera.waivera.feature.today.ui.NoticeRow
import com.waivera.waivera.feature.today.ui.SectionSkeleton
import java.time.LocalTime

/**
 * The notifications inbox (the web bell's list), opened from Today's bell or a push. Owned by
 * `:feature:today`; routed by `:app`. Events open in the app; everything else opens its page on
 * the web app until the app has a screen for it.
 */
@Composable
fun NoticesScreen(onBack: () -> Unit, onOpenEvent: (eventId: String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: NoticesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val uriHandler = LocalUriHandler.current
    val openEvent by rememberUpdatedState(onOpenEvent)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NoticesEffect.Open -> when (val destination = effect.destination) {
                    is NoticeDestination.Event -> openEvent(destination.eventId)
                    is NoticeDestination.Web -> runCatching { uriHandler.openUri(destination.url) }
                    NoticeDestination.None -> Unit
                }
                NoticesEffect.NothingToOpen ->
                    snackbar.showSnackbar(resources.getString(R.string.feature_today_notices_nothing_to_open))
                NoticesEffect.MarkAllReadFailed -> {
                    val result = snackbar.showSnackbar(
                        message = resources.getString(R.string.feature_today_mark_all_failed),
                        actionLabel = resources.getString(R.string.feature_today_retry),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.markAllRead()
                }
            }
        }
    }
    NoticesContent(
        state = state,
        snackbar = snackbar,
        callbacks = NoticesCallbacks(
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onRetryConnection = viewModel::retryConnection,
            onFilter = viewModel::setFilter,
            onOpen = viewModel::open,
            onMarkAllRead = viewModel::markAllRead,
        ),
        modifier = modifier,
    )
}

internal class NoticesCallbacks(
    val onBack: () -> Unit,
    val onRefresh: () -> Unit,
    val onRetryConnection: () -> Unit,
    val onFilter: (NoticeFilter) -> Unit,
    val onOpen: (id: String) -> Unit,
    val onMarkAllRead: () -> Unit,
)

@Composable
internal fun NoticesContent(
    state: NoticesUiState,
    callbacks: NoticesCallbacks,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val markAll = stringResource(R.string.feature_today_mark_all_read)
            WaiveraTopBar(
                title = stringResource(R.string.feature_today_notices_title),
                subtitle = if (state.unreadCount > 0) {
                    pluralStringResource(R.plurals.feature_today_notices_unread, state.unreadCount, state.unreadCount)
                } else {
                    null
                },
                onBack = callbacks.onBack,
                actions = if (state.unreadCount > 0 && !state.isMarkingAllRead) {
                    listOf(
                        TopBarAction(
                            icon = WaiveraIcons.MarkRead,
                            contentDescription = markAll,
                            onClick = callbacks.onMarkAllRead,
                        ),
                    )
                } else {
                    emptyList()
                },
            )
            ConnectionBanner(
                state = state.connection,
                onRetry = callbacks.onRetryConnection,
                offlineMessage = stringResource(R.string.feature_today_offline_saved),
            )
            NoticeFilterChips(selected = state.filter, onSelect = callbacks.onFilter)
            if (state.refreshFailed) {
                WaiveraBanner(
                    message = stringResource(R.string.feature_today_notices_refresh_failed),
                    tone = BannerTone.Warning,
                    actionLabel = stringResource(R.string.feature_today_retry),
                    onAction = callbacks.onRefresh,
                    actionIcon = WaiveraIcons.Refresh,
                )
            }
            WaiveraPullToRefreshBox(
                isRefreshing = state.isRefreshing && state.phase != NoticesPhase.Loading,
                onRefresh = callbacks.onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                NoticesBody(state, callbacks)
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues()),
        )
    }
}

@Composable
private fun NoticesBody(state: NoticesUiState, callbacks: NoticesCallbacks) {
    val top = Modifier.padding(top = WaiveraSpacing.Large)
    when (state.phase) {
        NoticesPhase.Loading -> if (rememberSkeletonVisible(loading = true)) {
            SectionSkeleton(modifier = Modifier.padding(top = WaiveraSpacing.Small))
        }
        // The raw error never shows; the copy is fixed.
        NoticesPhase.Error -> WaiveraErrorState(
            title = stringResource(R.string.feature_today_notices_error_title),
            message = stringResource(R.string.feature_today_notices_error_message),
            onRetry = callbacks.onRefresh,
            modifier = top,
        )
        NoticesPhase.Empty -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                if (state.isFilteredEmpty) {
                    WaiveraEmptyState(
                        title = stringResource(R.string.feature_today_notices_filtered_empty_title),
                        message = stringResource(R.string.feature_today_notices_filtered_empty_message),
                        illustration = WaiveraIllustration.CaughtUp,
                        actionLabel = stringResource(R.string.feature_today_notices_show_all),
                        onAction = { callbacks.onFilter(NoticeFilter.All) },
                        modifier = top,
                    )
                } else {
                    WaiveraEmptyState(
                        title = stringResource(R.string.feature_today_notices_empty_title),
                        message = stringResource(R.string.feature_today_notices_empty_message),
                        illustration = WaiveraIllustration.CaughtUp,
                        modifier = top,
                    )
                }
            }
        }
        NoticesPhase.Content -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = WaiveraSpacing.Large),
        ) {
            state.groups.forEachIndexed { index, group ->
                item(key = "day-${group.day}", contentType = "header") {
                    WaiveraSectionHeader(text = stringResource(group.day.labelRes()), first = index == 0)
                }
                items(group.rows, key = { it.id }, contentType = { "notice" }) { row ->
                    NoticeRow(
                        kind = row.kind,
                        title = row.title,
                        message = row.message,
                        at = row.at,
                        unread = row.unread,
                        opensOutside = row.opensOutside,
                        onClick = { callbacks.onOpen(row.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** Single choice; scrolls sideways at 200% instead of wrapping a label. */
@Composable
private fun NoticeFilterChips(selected: NoticeFilter, onSelect: (NoticeFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = WaiveraSpacing.Gutter)
            .padding(top = WaiveraSpacing.Small, bottom = WaiveraSpacing.Small)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoticeFilter.entries.forEach { option ->
            WaiveraFilterChip(
                label = stringResource(option.labelRes()),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

private fun NoticeFilter.labelRes(): Int = when (this) {
    NoticeFilter.All -> R.string.feature_today_notices_filter_all
    NoticeFilter.Tasks -> R.string.feature_today_notices_filter_tasks
    NoticeFilter.Approvals -> R.string.feature_today_notices_filter_approvals
    NoticeFilter.Meetings -> R.string.feature_today_notices_filter_meetings
}

private fun NoticeDay.labelRes(): Int = when (this) {
    NoticeDay.Today -> R.string.feature_today_notices_day_today
    NoticeDay.Yesterday -> R.string.feature_today_notices_day_yesterday
    NoticeDay.ThisWeek -> R.string.feature_today_notices_day_this_week
    NoticeDay.Earlier -> R.string.feature_today_notices_day_earlier
}

/** Sample states for previews and screenshot tests. */
internal object NoticesPreviewStates {
    val callbacks = NoticesCallbacks({}, {}, {}, {}, {}, {})

    private val TwoPm: PastLabel = PastLabel.TodayAt(LocalTime.NOON.plusHours(2))
    private val Monday: PastLabel = PastLabel.Yesterday

    private val todayGroup = NoticeGroup(
        NoticeDay.Today,
        listOf(
            NoticeRowModel(
                "n1",
                NoticeKind.TaskAssigned,
                "Task 1042 was assigned to you",
                "Priya Raman assigned you a task",
                TwoPm,
                unread = true,
                opensOutside = true,
            ),
            NoticeRowModel(
                "n2",
                NoticeKind.Meeting,
                "Team huddle starts at 3:00 PM",
                "Waivera video meeting",
                TwoPm,
                unread = true,
                opensOutside = false,
            ),
            NoticeRowModel(
                "n3",
                NoticeKind.Signature,
                "Service agreement",
                "Jordan Lee asked for your signature",
                TwoPm,
                unread = true,
                opensOutside = true,
            ),
        ),
    )

    private val weekGroup = NoticeGroup(
        NoticeDay.ThisWeek,
        listOf(
            NoticeRowModel(
                "n4",
                NoticeKind.TaskActivity,
                "New activity on task 987",
                "Jordan Lee logged 45 min",
                Monday,
                unread = false,
                opensOutside = true,
            ),
            NoticeRowModel(
                "n5",
                NoticeKind.TimeOff,
                "Time off approved",
                "Nov 26 to Nov 27",
                Monday,
                unread = false,
                opensOutside = false,
            ),
            NoticeRowModel(
                "n6",
                NoticeKind.Survey,
                "Storm readiness check-in",
                "Please respond as soon as possible",
                Monday,
                unread = false,
                opensOutside = true,
            ),
        ),
    )

    fun populated() = NoticesUiState(
        unreadCount = 3,
        phase = NoticesPhase.Content,
        totalCount = 6,
        groups = listOf(todayGroup, weekGroup),
    )
}

@WaiveraPreviews
@Composable
private fun NoticesPreview() {
    WaiveraTheme {
        NoticesContent(state = NoticesPreviewStates.populated(), callbacks = NoticesPreviewStates.callbacks)
    }
}

@WaiveraPreviews
@Composable
private fun NoticesEmptyPreview() {
    WaiveraTheme {
        NoticesContent(
            state = NoticesUiState(phase = NoticesPhase.Empty),
            callbacks = NoticesPreviewStates.callbacks,
        )
    }
}
