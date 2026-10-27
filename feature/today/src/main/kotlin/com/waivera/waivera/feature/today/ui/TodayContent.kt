package com.waivera.waivera.feature.today.ui

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.TodayAction
import com.waivera.waivera.feature.today.model.ActivityRow
import com.waivera.waivera.feature.today.model.NeedsYouItem
import com.waivera.waivera.feature.today.model.TodayUiState

/** Navigation and actions for [TodayContent]. Every row deep-links through these lambdas. */
@Stable
internal class TodayCallbacks(
    val onAction: (TodayAction) -> Unit,
    val onAccountClick: () -> Unit,
    val onOpenConversation: (conversationId: Long) -> Unit,
    val onOpenMessage: (conversationId: Long, messageId: Long, threadRootMessageId: Long?) -> Unit,
    val onOpenSchedule: () -> Unit,
    val onOpenEvent: (eventId: String) -> Unit,
)

/**
 * The stateless Today screen: top bar, connection banner, then one pull-to-refresh list in
 * priority order. Loads under ~300 ms show nothing rather than flashing a skeleton.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayContent(
    state: TodayUiState,
    callbacks: TodayCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val formats = rememberTodayFormats()
    val guarded = guardNavigation(callbacks)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
    ) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_today_today_title),
            subtitle = state.date?.let(formats::longDay),
            account = TopBarAccount(
                name = state.accountName.ifBlank { stringResource(R.string.feature_today_account_name) },
                onClick = callbacks.onAccountClick,
            ),
        )
        ConnectionBanner(state = state.connection, onRetry = { callbacks.onAction(TodayAction.RetryConnection) })
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { callbacks.onAction(TodayAction.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                val error = state.loadError
                val showSkeleton = rememberSkeletonVisible(state.isInitialLoad)
                when {
                    error != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        WaiveraErrorState(
                            title = stringResource(R.string.feature_today_error_title),
                            message = stringResource(error.messageRes),
                            onRetry = { callbacks.onAction(TodayAction.Refresh) },
                        )
                    }
                    !state.isInitialLoad -> TodayList(state, guarded, listState)
                    showSkeleton -> TodaySkeleton()
                    else -> Unit
                }
            }
            WaiveraSnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun TodayList(state: TodayUiState, callbacks: TodayCallbacks, listState: LazyListState) {
    val formats = rememberTodayFormats()
    val activity = state.activity
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, activity.hasMore, activity.isLoadingMore, activity.loadMoreFailed) {
        if (nearEnd && activity.hasMore && !activity.isLoadingMore && !activity.loadMoreFailed) {
            callbacks.onAction(TodayAction.LoadMoreActivity)
        }
    }
    val staleLine = state.staleSince?.let { staleText(it, formats) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = WaiveraSpacing.ExtraLarge),
    ) {
        if (staleLine != null) item(key = "stale", contentType = "quiet") { QuietLine(staleLine) }
        needsYouItems(state, callbacks)
        nextUpItems(state, callbacks)
        celebrationItems(state, callbacks)
        activityItems(state, callbacks)
    }
}

private fun LazyListScope.needsYouItems(state: TodayUiState, callbacks: TodayCallbacks) {
    val section = state.needsYou
    when {
        state.isAllCaughtUp -> item(key = "caught-up", contentType = "caught-up") {
            AllCaughtUp(Modifier.animateTodayItem(this, WaiveraTheme.motion))
        }
        section.failed -> {
            header("needs-header", R.string.feature_today_section_needs_you)
            item(key = "needs-error", contentType = "error") {
                SectionError(
                    message = stringResource(R.string.feature_today_needs_you_failed),
                    onRetry = { callbacks.onAction(TodayAction.Refresh) },
                )
            }
        }
        section.items.isNotEmpty() -> {
            header("needs-header", R.string.feature_today_section_needs_you)
            val rowCallbacks = NeedsYouCallbacks(
                onOpenConversation = callbacks.onOpenConversation,
                onOpenMention = { mention -> openMention(mention, callbacks) },
                onOpenEvent = callbacks.onOpenEvent,
                onRespond = { id, accept -> callbacks.onAction(TodayAction.RespondToInvitation(id, accept)) },
            )
            items(section.items, key = { it.key }, contentType = { it::class.simpleName }) { item ->
                NeedsYouRow(item, rowCallbacks, Modifier.animateTodayItem(this, WaiveraTheme.motion))
            }
            if (section.hiddenCount > 0 || section.isExpanded) {
                item(key = "needs-more", contentType = "toggle") {
                    WaiveraButton(
                        text = if (section.isExpanded) {
                            stringResource(R.string.feature_today_needs_you_show_less)
                        } else {
                            pluralStringResource(
                                R.plurals.feature_today_needs_you_show_more,
                                section.hiddenCount,
                                section.hiddenCount,
                            )
                        },
                        onClick = { callbacks.onAction(TodayAction.ToggleNeedsYouExpanded) },
                        variant = WaiveraButtonVariant.Text,
                        modifier = Modifier.animateTodayItem(this, WaiveraTheme.motion),
                    )
                }
            }
        }
    }
}

private fun LazyListScope.nextUpItems(state: TodayUiState, callbacks: TodayCallbacks) {
    val section = state.nextUp
    item(key = "next-header", contentType = "header") {
        val seeSchedule = stringResource(R.string.feature_today_see_schedule_a11y)
        TodaySectionHeader(
            title = stringResource(R.string.feature_today_section_next_up),
            trailing = {
                WaiveraButton(
                    text = stringResource(R.string.feature_today_see_schedule),
                    onClick = callbacks.onOpenSchedule,
                    variant = WaiveraButtonVariant.Text,
                    modifier = Modifier.semantics { contentDescription = seeSchedule },
                )
            },
        )
    }
    when {
        section.failed -> item(key = "next-error", contentType = "error") {
            SectionError(
                message = stringResource(R.string.feature_today_next_up_failed),
                onRetry = { callbacks.onAction(TodayAction.Refresh) },
            )
        }
        !section.isReady -> item(key = "next-loading", contentType = "loading") { SectionSkeleton() }
        section.items.isEmpty() -> item(key = "next-empty", contentType = "quiet") {
            QuietLine(stringResource(R.string.feature_today_next_up_empty))
        }
        else -> items(section.items, key = { "next-${it.eventId}" }, contentType = { "next" }) { item ->
            NextUpRow(
                item = item,
                onClick = { callbacks.onOpenEvent(item.eventId) },
                modifier = Modifier.animateTodayItem(this, WaiveraTheme.motion),
            )
        }
    }
}

private fun LazyListScope.celebrationItems(state: TodayUiState, callbacks: TodayCallbacks) {
    if (state.milestones.isEmpty() && !state.milestonesFailed) return
    header("celebrations-header", R.string.feature_today_section_celebrations)
    if (state.milestonesFailed) {
        item(key = "celebrations-error", contentType = "error") {
            SectionError(
                message = stringResource(R.string.feature_today_milestones_failed),
                onRetry = { callbacks.onAction(TodayAction.Refresh) },
            )
        }
    }
    items(state.milestones, key = { "celebration-${it.userId}" }, contentType = { "celebration" }) { item ->
        CelebrationRow(
            item = item,
            onClick = callbacks.onOpenSchedule,
            modifier = Modifier.animateTodayItem(this, WaiveraTheme.motion),
        )
    }
}

private fun LazyListScope.activityItems(state: TodayUiState, callbacks: TodayCallbacks) {
    val section = state.activity
    item(key = "activity-header", contentType = "header") {
        ActivityHeader(section, onMarkAllRead = { callbacks.onAction(TodayAction.MarkAllActivityRead) })
    }
    when {
        section.failed -> item(key = "activity-error", contentType = "error") {
            SectionError(
                message = stringResource(R.string.feature_today_activity_failed),
                onRetry = { callbacks.onAction(TodayAction.Refresh) },
            )
        }
        !section.isReady -> item(key = "activity-loading", contentType = "loading") { SectionSkeleton() }
        section.isEmpty -> item(key = "activity-empty", contentType = "empty") {
            WaiveraEmptyState(
                title = stringResource(R.string.feature_today_activity_empty_title),
                message = stringResource(R.string.feature_today_activity_empty_message),
                icon = R.drawable.feature_today_ic_done_all,
            )
        }
        else -> {
            item(key = "activity-filters", contentType = "filters") {
                ActivityFilterRow(
                    filter = section.filter,
                    unreadOnly = section.unreadOnly,
                    onFilter = { callbacks.onAction(TodayAction.SetActivityFilter(it)) },
                    onToggleUnread = { callbacks.onAction(TodayAction.ToggleUnreadOnly) },
                )
            }
            if (section.isFilteredEmpty) {
                item(key = "activity-filtered-empty", contentType = "empty") {
                    WaiveraEmptyState(
                        title = stringResource(R.string.feature_today_activity_filtered_title),
                        message = stringResource(R.string.feature_today_activity_filtered_message),
                        icon = WaiveraIcons.Search,
                    )
                }
            }
            items(section.rows, key = { "activity-${it.id}" }, contentType = { "activity" }) { row ->
                ActivityItemRow(
                    row = row,
                    onClick = { openActivity(row, callbacks) },
                    modifier = Modifier.animateTodayItem(this, WaiveraTheme.motion),
                )
            }
            item(key = "activity-footer", contentType = "footer") {
                ActivityFooter(section, onRetry = { callbacks.onAction(TodayAction.LoadMoreActivity) })
            }
        }
    }
}

private fun LazyListScope.header(key: String, @StringRes title: Int) {
    item(key = key, contentType = "header") { TodaySectionHeader(title = stringResource(title)) }
}

/** Marks the activity read (optimistically) and opens the message, or its thread when it is a reply. */
private fun openActivity(row: ActivityRow, callbacks: TodayCallbacks) {
    callbacks.onAction(TodayAction.MarkActivityRead(row.id))
    callbacks.onOpenMessage(row.conversationId, row.messageId, row.threadRootMessageId)
}

private fun openMention(mention: NeedsYouItem.Mention, callbacks: TodayCallbacks) {
    callbacks.onAction(TodayAction.MarkActivityRead(mention.activityId))
    callbacks.onOpenMessage(mention.conversationId, mention.messageId, mention.threadRootMessageId)
}

/** Inserts, removals and moves use the layout and state motion tokens (snap/fade when reduced). */
private fun Modifier.animateTodayItem(scope: LazyItemScope, motion: WaiveraMotionScheme): Modifier = with(scope) {
    this@animateTodayItem.animateItem(
        fadeInSpec = motion.state(),
        placementSpec = motion.layout(),
        fadeOutSpec = motion.state(),
    )
}

/**
 * Drops a second navigation within [NAVIGATION_GUARD_MILLIS] of the first, so a fast double
 * tap opens one screen, not two (02 §6).
 */
@Composable
private fun guardNavigation(callbacks: TodayCallbacks): TodayCallbacks {
    var last by remember { mutableLongStateOf(0L) }
    return remember(callbacks) {
        fun guard(navigate: () -> Unit) {
            val now = SystemClock.uptimeMillis()
            if (now - last >= NAVIGATION_GUARD_MILLIS) {
                last = now
                navigate()
            }
        }
        TodayCallbacks(
            onAction = callbacks.onAction,
            onAccountClick = { guard(callbacks.onAccountClick) },
            onOpenConversation = { id -> guard { callbacks.onOpenConversation(id) } },
            onOpenMessage = { c, m, r -> guard { callbacks.onOpenMessage(c, m, r) } },
            onOpenSchedule = { guard(callbacks.onOpenSchedule) },
            onOpenEvent = { id -> guard { callbacks.onOpenEvent(id) } },
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 4
private const val NAVIGATION_GUARD_MILLIS = 600L
