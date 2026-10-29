package com.waivera.waivera.feature.today.model

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.HandledObservation
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.LocalDate
import java.time.LocalTime

/**
 * Everything the Today screen draws, in priority order: what needs the person now, the next
 * three schedule items, today's celebrations, then the activity feed.
 */
@Immutable
data class TodayUiState(
    val accountName: String = "",
    /** "Good morning, Alex", in the employee's zone; null until the clock and zone are known. */
    val greeting: Greeting? = null,
    /** Today's date in the employee's saved zone, for the top bar. */
    val date: LocalDate? = null,
    /** Nothing is cached yet and the first load is still running: show the skeleton (after ~300 ms). */
    val isInitialLoad: Boolean = true,
    /** Nothing is cached and every source failed: show the full error state with Try again. */
    val loadError: WaiveraError? = null,
    val connection: ConnectionBannerState = ConnectionBannerState.Connected,
    /** Set only when the data on screen is stale, so the normal state stays calm. */
    val staleSince: PastLabel? = null,
    /** When the oldest data on screen was saved, in the employee's zone: the offline banner names it. */
    val savedAt: LocalTime? = null,
    val isRefreshing: Boolean = false,
    /** The celebrated person whose "Send wishes" chat is opening; their button shows progress. */
    val openingWishesFor: String? = null,
    val needsYou: NeedsYouSection = NeedsYouSection(),
    val nextUp: NextUpSection = NextUpSection(),
    val milestones: List<CelebrationItem> = emptyList(),
    val milestonesFailed: Boolean = false,
    val activity: ActivitySection = ActivitySection(),
    /** The hero ring: handled today (tallied on this device) and still open; null until Needs you loads. */
    val progress: TodayProgress? = null,
    val stats: TodayStats = TodayStats(),
    /** "Your day": today's timed events on a track; null until the schedule loads. */
    val day: DayTimeline? = null,
    /**
     * What needs the person right now, for the on-device tally; null while any source behind
     * Needs you is loading or failed, so a missing source never reads as everything handled.
     */
    val handledObservation: HandledObservation? = null,
) {
    /** The one "All caught up" moment: nothing needs the person and no activity is unread. */
    val isAllCaughtUp: Boolean
        get() = !isInitialLoad &&
            loadError == null &&
            needsYou.isReady &&
            needsYou.items.isEmpty() &&
            activity.isReady &&
            activity.unreadCount == 0
}

@Immutable
data class NeedsYouSection(
    val items: List<NeedsYouItem> = emptyList(),
    /** Every open item, including the ones collapsed out of [items]: the hero counts these. */
    val totalCount: Int = items.size,
    /** Rows beyond [NeedsYouSection.COLLAPSED_COUNT] are hidden until the person expands. */
    val hiddenCount: Int = 0,
    val isExpanded: Boolean = false,
    val isReady: Boolean = false,
    val failed: Boolean = false,
) {
    companion object {
        /** Concept A folds after three rows, so the day's first cards still show on a short phone. */
        const val COLLAPSED_COUNT = 3
    }
}

@Immutable
sealed interface NeedsYouItem {
    val key: String

    data class DirectMessage(
        val conversationId: Long,
        /** Null when the server sent no usable name. */
        val name: String?,
        val snippet: MessageSnippet,
        val unreadCount: Int,
        val at: PastLabel?,
        /** The other person, for the avatar image; null when it is not a one-to-one chat. */
        val otherUserId: String? = null,
    ) : NeedsYouItem {
        override val key: String get() = "dm-$conversationId"
    }

    data class Mention(
        val activityId: Long,
        val conversationId: Long,
        val messageId: Long,
        val threadRootMessageId: Long?,
        val actorName: String?,
        val summary: ActivitySummary,
        val at: PastLabel?,
    ) : NeedsYouItem {
        override val key: String get() = "mention-$activityId"
    }

    /** A message of mine in [conversationId] that the outbox gave up on; Retry lives in the chat. */
    data class NotSent(val conversationId: Long, val name: String) : NeedsYouItem {
        override val key: String get() = "not-sent-$conversationId"
    }

    data class Invitation(val eventId: String, val title: String?, val organizer: String?, val time: UpcomingLabel?) :
        NeedsYouItem {
        override val key: String get() = "$KEY_PREFIX$eventId"

        companion object {
            const val KEY_PREFIX = "invite-"
        }
    }

    /**
     * Unread updates in the automated Waivera conversation (ticket cards). [ticketId] and
     * [ticketTitle] come from the last message's `ticket.update` payload when it has one.
     */
    data class Ticket(
        val conversationId: Long,
        val snippet: MessageSnippet,
        val unreadCount: Int,
        val at: PastLabel?,
        val ticketId: String? = null,
        val ticketTitle: String? = null,
    ) : NeedsYouItem {
        override val key: String get() = "ticket-$conversationId"
    }
}

/** The first line of a message preview, or what stands in for it. */
@Immutable
sealed interface MessageSnippet {
    data class Text(val text: String) : MessageSnippet

    data object Photo : MessageSnippet

    data class File(val name: String?) : MessageSnippet

    data object Deleted : MessageSnippet

    data object None : MessageSnippet
}

@Immutable
data class NextUpSection(
    val items: List<NextUpItem> = emptyList(),
    val isReady: Boolean = false,
    val failed: Boolean = false,
) {
    /**
     * The item the large Up next card and the hero's Next row feature: the first with a start
     * time, since an all-day item ("Daylight saving ends") is nothing to get to; else the first.
     */
    val featured: NextUpItem? get() = items.firstOrNull { it.start != null } ?: items.firstOrNull()

    /** Every item but [featured], in order, for the rows under the card. */
    val others: List<NextUpItem> get() = featured?.let { first ->
        items.filterNot { it.eventId == first.eventId }
    }.orEmpty()

    companion object {
        const val COUNT = 3
    }
}

@Immutable
data class NextUpItem(
    val eventId: String,
    val title: String?,
    val time: UpcomingLabel,
    /** Local clock times in the employee's zone; null for all-day items. */
    val start: LocalTime?,
    val end: LocalTime?,
    val endsOnLaterDay: Boolean,
    val location: String?,
    val isOnline: Boolean,
    /** It shows on today's track: it starts today (or is on now) in the employee's zone. */
    val startsToday: Boolean = false,
) {
    val isHappeningNow: Boolean get() = time is UpcomingLabel.Now
}

@Immutable
data class CelebrationItem(
    val userId: String,
    val name: String?,
    val isBirthday: Boolean,
    /** Set when today is a work anniversary of one year or more. */
    val anniversaryYears: Int?,
    /** The signed-in person's own day: no "Send wishes" to yourself. */
    val isMe: Boolean = false,
)

enum class ActivityFilter { ALL, MENTIONS, REPLIES, REACTIONS }

@Immutable
data class ActivitySection(
    val rows: List<ActivityRow> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.ALL,
    val unreadOnly: Boolean = false,
    /** Unread across every loaded page, including mentions pinned under Needs you. */
    val unreadCount: Int = 0,
    /** True when older pages may still hold unread items, so the badge reads "N+". */
    val unreadIsLowerBound: Boolean = false,
    /** How many activities exist across loaded pages before filtering. */
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val isMarkingAllRead: Boolean = false,
    val isReady: Boolean = false,
    val failed: Boolean = false,
) {
    /** Nothing at all, not even with filters off. */
    val isEmpty: Boolean get() = isReady && totalCount == 0

    /** Activities exist but the chips hide all of them. */
    val isFilteredEmpty: Boolean get() = isReady && totalCount > 0 && rows.isEmpty()
}

@Immutable
data class ActivityRow(
    val id: Long,
    val type: ChatActivityType,
    val summary: ActivitySummary,
    val at: PastLabel?,
    val read: Boolean,
    val conversationId: Long,
    val messageId: Long,
    val threadRootMessageId: Long?,
)

/** Row text. Reactions are rewritten on the client (06 §A2); everything else uses the server summary. */
@Immutable
sealed interface ActivitySummary {
    data class Server(val text: String) : ActivitySummary

    /** "{actor} reacted {emoji} to one of your messages."; a blank actor reads "Someone". */
    data class Reaction(val actor: String?, val emoji: String?) : ActivitySummary

    /** The server sent no summary. */
    data object Fallback : ActivitySummary
}
