package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.network.dto.ChatActivityType
import java.time.Instant

/** Activity feed rules from 06 §A, with the Expo bugs fixed. */
object ActivityRules {
    /** The API's page size; has-more means a full page came back. */
    const val PAGE_SIZE = 30

    /**
     * One row per id across every loaded page, the later page winning, newest first by parsed
     * UTC instant (not by string, and zone-less values were already read as UTC by the parser).
     */
    fun merge(items: List<ActivityItem>): List<ActivityItem> =
        items.associateBy { it.id }.values.sortedWith(newestFirst)

    private val newestFirst: Comparator<ActivityItem> =
        compareByDescending<ActivityItem, Instant?>(nullsFirst<Instant>()) { it.createdAt }.thenByDescending { it.id }

    fun summary(item: ActivityItem): ActivitySummary = when {
        item.type == ChatActivityType.REACTION -> ActivitySummary.Reaction(
            actor = item.actorName?.trim()?.takeIf(String::isNotEmpty),
            emoji = item.reactionType?.trim()?.takeIf(String::isNotEmpty),
        )
        !item.summary.isNullOrBlank() -> ActivitySummary.Server(item.summary.orEmpty().trim())
        else -> ActivitySummary.Fallback
    }

    /** AND of the type chip and the Unread toggle. Unknown types appear only under All. */
    fun matches(type: ChatActivityType, read: Boolean, filter: ActivityFilter, unreadOnly: Boolean): Boolean {
        if (unreadOnly && read) return false
        return when (filter) {
            ActivityFilter.ALL -> true
            ActivityFilter.MENTIONS -> type == ChatActivityType.MENTION
            ActivityFilter.REPLIES -> type == ChatActivityType.THREAD_REPLY
            ActivityFilter.REACTIONS -> type == ChatActivityType.REACTION
        }
    }

    /**
     * The API has no unread total, so the badge can only count loaded pages. When the oldest
     * loaded page still holds unread items and more pages exist, older pages probably hold more:
     * the screen loads them (up to a cap) and, past the cap, shows the count as a lower bound.
     */
    fun olderPagesMayHoldUnread(
        merged: List<ActivityItem>,
        hasMore: Boolean,
        isRead: (ActivityItem) -> Boolean,
    ): Boolean = hasMore && merged.takeLast(PAGE_SIZE).any { !isRead(it) }
}
