package com.waivera.waivera.feature.today.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraPullToRefreshBox
import com.waivera.waivera.core.designsystem.component.WaiveraSectionAction
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.exit
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.TodayAction
import com.waivera.waivera.feature.today.model.ActivityRow
import com.waivera.waivera.feature.today.model.NeedsYouItem
import com.waivera.waivera.feature.today.model.NeedsYouSection
import com.waivera.waivera.feature.today.model.TodayUiState
import java.time.LocalTime

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
 * The stateless Today screen (concept A): one pull-to-refresh list that opens on the plum hero
 * (bar, greeting, summary card and stats) and continues on the rounded sheet with the connection
 * banner and the sections in priority order. Loads under ~300 ms show nothing rather than
 * flashing a skeleton.
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
    val guarded = guardNavigation(callbacks)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
    ) {
        WaiveraPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { callbacks.onAction(TodayAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            TodayList(state, guarded, listState)
        }
        WaiveraSnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Today's offline copy names what is on screen, not messages Today doesn't send (UI2-17). */
@Composable
private fun offlineMessage(savedAt: LocalTime?): String {
    val formats = rememberTodayFormats()
    return savedAt?.let { stringResource(R.string.feature_today_offline_saved_at, formats.time(it)) }
        ?: stringResource(R.string.feature_today_offline_saved)
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
        val canLoadMore = activity.hasMore && !activity.isLoadingMore && !activity.loadMoreFailed
        if (nearEnd && canLoadMore) {
            callbacks.onAction(TodayAction.LoadMoreActivity)
        }
    }
    // The offline banner already says how old the data is, so the quiet line waits until it's gone.
    val staleLine = state.staleSince
        ?.takeIf { state.connection == ConnectionBannerState.Connected }
        ?.let { staleText(it, formats) }
    val showSkeleton = rememberSkeletonVisible(state.isInitialLoad)
    val offline = offlineMessage(state.savedAt)
    val error = state.loadError
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = WaiveraSpacing.ExtraLarge),
    ) {
        item(key = "hero", contentType = "hero") { TodayHero(state, callbacks) }
        item(key = "connection", contentType = "banner") {
            ConnectionBanner(
                state = state.connection,
                onRetry = { callbacks.onAction(TodayAction.RetryConnection) },
                offlineMessage = offline,
            )
        }
        when {
            error != null -> item(key = "error", contentType = "error") {
                WaiveraErrorState(
                    title = stringResource(R.string.feature_today_error_title),
                    message = stringResource(error.messageRes),
                    onRetry = { callbacks.onAction(TodayAction.Refresh) },
                )
            }
            state.isInitialLoad -> if (showSkeleton) {
                item(key = "skeleton", contentType = "skeleton") {
                    // The skeleton leaves faster than the day fades in over it, so loaded rows
                    // settle in place instead of cutting (M1-16).
                    Box(
                        Modifier.animateItem(
                            fadeInSpec = null,
                            placementSpec = null,
                            fadeOutSpec = WaiveraTheme.motion.exit(),
                        ),
                    ) { TodaySkeleton() }
                }
            }
            else -> {
                if (staleLine != null) item(key = "stale", contentType = "quiet") { QuietLine(staleLine) }
                needsYouItems(state, callbacks)
                // All caught up leads with what is next and who to celebrate, as concept A's
                // reward does; a working day leads with the day's track.
                if (state.isAllCaughtUp) {
                    nextUpItems(state, callbacks)
                    celebrationItems(state, callbacks)
                    yourDayItem(state, callbacks)
                } else {
                    yourDayItem(state, callbacks)
                    nextUpItems(state, callbacks)
                    celebrationItems(state, callbacks)
                }
                activityItems(state, callbacks)
            }
        }
    }
}

private fun LazyListScope.needsYouItems(state: TodayUiState, callbacks: TodayCallbacks) {
    val section = state.needsYou
    // All caught up is the hero's reward; the sheet goes straight on to the day.
    if (state.isAllCaughtUp || (!section.failed && section.items.isEmpty())) return
    item(key = "needs-header", contentType = "header") {
        WaiveraSectionHeader(
            title = stringResource(R.string.feature_today_section_needs_you),
            count = section.totalCount.takeIf { !section.failed },
            countDescription = pluralStringResource(
                R.plurals.feature_today_brand_needs_you_count_a11y,
                section.totalCount,
                section.totalCount,
            ),
            action = needsYouToggle(section, onToggle = { callbacks.onAction(TodayAction.ToggleNeedsYouExpanded) }),
            modifier = Modifier.sheetHeader(first = true),
        )
    }
    if (section.failed) {
        item(key = "needs-error", contentType = "error") {
            SectionError(
                message = stringResource(R.string.feature_today_needs_you_failed),
                onRetry = { callbacks.onAction(TodayAction.Refresh) },
            )
        }
    } else {
        needsYouCards(section, callbacks)
    }
}

private fun LazyListScope.needsYouCards(section: NeedsYouSection, callbacks: TodayCallbacks) {
    val rowCallbacks = NeedsYouCallbacks(
        onOpenConversation = callbacks.onOpenConversation,
        onOpenMention = { mention -> openMention(mention, callbacks) },
        onOpenEvent = callbacks.onOpenEvent,
        onRespond = { id, accept -> callbacks.onAction(TodayAction.RespondToInvitation(id, accept)) },
    )
    items(section.items, key = { it.key }, contentType = { it::class.simpleName }) { item ->
        NeedsYouRow(
            item = item,
            callbacks = rowCallbacks,
            modifier = Modifier
                .animateTodayItem(this, WaiveraTheme.motion)
                .testTag(NEEDS_YOU_CARD_TEST_TAG),
        )
    }
}

/** "Show all" (TalkBack: "Show 2 more") while items are folded away, "Show less" once open. */
@Composable
private fun needsYouToggle(section: NeedsYouSection, onToggle: () -> Unit): WaiveraSectionAction? = when {
    section.isExpanded -> WaiveraSectionAction(
        label = stringResource(R.string.feature_today_needs_you_show_less),
        onClick = onToggle,
    )
    section.hiddenCount > 0 -> WaiveraSectionAction(
        label = stringResource(R.string.feature_today_brand_show_all),
        onClick = onToggle,
        onClickLabel = pluralStringResource(
            R.plurals.feature_today_needs_you_show_more,
            section.hiddenCount,
            section.hiddenCount,
        ),
    )
    else -> null
}

private fun LazyListScope.yourDayItem(state: TodayUiState, callbacks: TodayCallbacks) {
    val day = state.day ?: return
    val next = state.nextUp.items.firstOrNull { it.startsToday && it.start != null }
    item(key = "your-day", contentType = "your-day") {
        YourDayCard(
            day = day,
            next = next,
            onOpenEvent = callbacks.onOpenEvent,
            modifier = Modifier.padding(top = WaiveraSpacing.MediumLarge, bottom = WaiveraSpacing.ExtraSmall),
        )
    }
}

private fun LazyListScope.nextUpItems(state: TodayUiState, callbacks: TodayCallbacks) {
    val section = state.nextUp
    item(key = "next-header", contentType = "header") {
        WaiveraSectionHeader(
            title = stringResource(R.string.feature_today_section_next_up),
            action = WaiveraSectionAction(
                label = stringResource(R.string.feature_today_see_schedule),
                onClick = callbacks.onOpenSchedule,
                onClickLabel = stringResource(R.string.feature_today_open_schedule),
            ),
            modifier = Modifier.sheetHeader(first = state.isAllCaughtUp),
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
        else -> {
            section.featured?.let { first ->
                item(key = "next-${first.eventId}", contentType = "next-first") {
                    UpNextCard(
                        item = first,
                        onClick = { callbacks.onOpenEvent(first.eventId) },
                        modifier = Modifier.animateTodayItem(this, WaiveraTheme.motion),
                    )
                }
            }
            items(section.others, key = { "next-${it.eventId}" }, contentType = { "next" }) { item ->
                NextUpRow(
                    item = item,
                    onClick = { callbacks.onOpenEvent(item.eventId) },
                    modifier = Modifier.animateTodayItem(this, WaiveraTheme.motion),
                )
            }
        }
    }
}

private fun LazyListScope.celebrationItems(state: TodayUiState, callbacks: TodayCallbacks) {
    if (state.milestones.isEmpty() && !state.milestonesFailed) return
    item(key = "celebrations-header", contentType = "header") {
        WaiveraSectionHeader(
            title = stringResource(R.string.feature_today_section_celebrations),
            modifier = Modifier.sheetHeader(),
        )
    }
    if (state.milestonesFailed) {
        item(key = "celebrations-error", contentType = "error") {
            SectionError(
                message = stringResource(R.string.feature_today_milestones_failed),
                onRetry = { callbacks.onAction(TodayAction.Refresh) },
            )
        }
    }
    itemsIndexed(
        items = state.milestones,
        key = { _, item -> "celebration-${item.userId}" },
        contentType = { _, _ -> "celebration" },
    ) { index, item ->
        CelebrationCard(
            item = item,
            onOpen = callbacks.onOpenSchedule,
            onSendWishes = { callbacks.onAction(TodayAction.SendWishes(item.userId)) },
            burstSeed = index,
            openingWishes = state.openingWishesFor == item.userId,
            modifier = Modifier.animateTodayItem(this, WaiveraTheme.motion),
        )
    }
}

private fun LazyListScope.activityItems(state: TodayUiState, callbacks: TodayCallbacks) {
    val section = state.activity
    item(key = "activity-header", contentType = "header") {
        ActivityHeader(
            section = section,
            onMarkAllRead = { callbacks.onAction(TodayAction.MarkAllActivityRead) },
            modifier = Modifier.padding(top = WaiveraSpacing.MediumLarge),
        )
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
                illustration = WaiveraIllustration.Activity,
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
                        illustration = WaiveraIllustration.Search,
                        actionLabel = stringResource(R.string.feature_today_activity_show_all),
                        onAction = { callbacks.onAction(TodayAction.ShowAllActivity) },
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

/**
 * A section header on the sheet: lined up with the cards' text, with room above it. The first
 * one sits right under the sheet's curve, which is its top margin (UI10-01). Below, the padding
 * and the card's own inset leave room for the link's 48dp target
 * ([WaiveraSizes.SectionActionOverhang]), so the card never takes "Show all"'s taps.
 */
private fun Modifier.sheetHeader(first: Boolean = false): Modifier = padding(
    start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
    end = WaiveraSpacing.Small,
    top = if (first) WaiveraSpacing.None else WaiveraSpacing.ExtraLarge,
    bottom = if (first) WaiveraSpacing.Small else WaiveraSpacing.MediumSmall,
)

/** Marks the activity read (optimistically) and opens the message, or its thread when it is a reply. */
private fun openActivity(row: ActivityRow, callbacks: TodayCallbacks) {
    callbacks.onAction(TodayAction.MarkActivityRead(row.id))
    callbacks.onOpenMessage(row.conversationId, row.messageId, row.threadRootMessageId)
}

private fun openMention(mention: NeedsYouItem.Mention, callbacks: TodayCallbacks) {
    callbacks.onAction(TodayAction.MarkActivityRead(mention.activityId))
    callbacks.onOpenMessage(mention.conversationId, mention.messageId, mention.threadRootMessageId)
}

/**
 * Inserts, removals and moves use the layout and state motion tokens (snap/fade when reduced).
 * A leaving row fades on the quicker exit, so it is gone before the rows below slide through its
 * place (M1-14).
 */
private fun Modifier.animateTodayItem(scope: LazyItemScope, motion: WaiveraMotionScheme): Modifier = with(scope) {
    this@animateTodayItem.animateItem(
        fadeInSpec = motion.state(),
        placementSpec = motion.layout(),
        fadeOutSpec = motion.exit(),
    )
}

/**
 * Drops a second navigation within [NAVIGATION_GUARD_MILLIS] of the first, so a fast double
 * tap opens one screen, not two (02 §6).
 */
@Composable
private fun guardNavigation(callbacks: TodayCallbacks): TodayCallbacks {
    // No earlier navigation: starting at 0 would drop every tap in the first 600 ms of uptime.
    var last by remember { mutableLongStateOf(-NAVIGATION_GUARD_MILLIS) }
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

/** Tags each Needs you card, for the small-phone fold check. */
internal const val NEEDS_YOU_CARD_TEST_TAG = "todayNeedsYouCard"

private const val LOAD_MORE_THRESHOLD = 4
private const val NAVIGATION_GUARD_MILLIS = 600L
