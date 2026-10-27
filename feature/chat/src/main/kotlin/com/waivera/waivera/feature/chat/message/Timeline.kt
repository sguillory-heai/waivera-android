package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Reaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One row of a message list. [key] is stable across updates and unique within a list. */
@Immutable
sealed interface TimelineItem {
    val key: String

    data class DaySeparator(override val key: String, val day: LocalDate) : TimelineItem

    /**
     * A quiet clock line inside a day, before a message that comes [TimelineBuilder.TIME_GAP] or
     * more after the one before it, so a reader can tell "this morning" from "just now" at a glance.
     */
    data class TimeSeparator(override val key: String, val at: Instant) : TimelineItem

    data class MessageRow(
        override val key: String,
        val message: Message,
        val isMine: Boolean,
        val isBot: Boolean,
        val groupedWithPrevious: Boolean,
        val groupedWithNext: Boolean,
        val rich: RichDocument?,
        val ticket: TicketNotification?,
        val reactions: List<ReactionSummary>,
        /** True for a message that arrived while the list was on screen (drives the arrival motion). */
        val isLiveArrival: Boolean = false,
        /**
         * True on the last bubble of a run whose clock line above already shows this minute, so
         * the footer drops its time and keeps only "Sent" or "Edited" (UI3-04).
         */
        val timeShownAbove: Boolean = false,
    ) : TimelineItem

    /** A plain system line (not a call): centred, italic, never grouped or long-pressable. */
    data class SystemRow(override val key: String, val message: Message, val text: String) : TimelineItem

    /** Every system message of one call record, oldest first, as one card (11 §7). */
    data class CallLog(
        override val key: String,
        val callRecordId: Long,
        val messages: List<Message>,
        val summary: CallLogSummary,
    ) : TimelineItem
}

/** One reaction pill: the emoji, how many people used it and whether I did. */
@Immutable
data class ReactionSummary(val emoji: String, val count: Int, val mine: Boolean, val userIds: List<String>)

object Reactions {
    /** The fixed set offered anywhere in the app (03 §13). */
    val QUICK: List<String> = listOf("👍", "❤️", "😂", "😮", "😢", "😠")

    /** Grouped by trimmed emoji in first-seen order. */
    fun summarize(reactions: List<Reaction>, myUserId: String?): List<ReactionSummary> {
        val groups = LinkedHashMap<String, MutableList<String>>()
        reactions.forEach { r ->
            val emoji = r.emoji.trim()
            if (emoji.isNotEmpty()) groups.getOrPut(emoji) { mutableListOf() } += r.userId
        }
        return groups.map { (emoji, users) ->
            ReactionSummary(emoji, users.size, myUserId != null && myUserId in users, users.toList())
        }
    }
}

object MessageGrouping {
    val WINDOW: Duration = Duration.ofMinutes(5)

    /** `canVisuallyGroupMessages` (03 §4): same author, same thread, same local day, within 5 minutes. */
    fun canGroup(previous: Message?, current: Message?, zone: ZoneId): Boolean =
        previous != null && current != null && sameSender(previous, current) && closeInTime(previous, current, zone)

    /** Same author and thread, and both ordinary live messages. */
    private fun sameSender(previous: Message, current: Message): Boolean = previous.authorId != null &&
        current.authorId == previous.authorId &&
        !previous.isDeleted &&
        !current.isDeleted &&
        !SystemMessages.isSystem(previous) &&
        !SystemMessages.isSystem(current) &&
        threadKey(previous) == threadKey(current)

    /** Same local day and at most [WINDOW] apart, in order; unknown or epoch times never group. */
    private fun closeInTime(previous: Message, current: Message, zone: ZoneId): Boolean {
        val a = previous.createdAt?.takeIf { it.toEpochMilli() > 0 } ?: return false
        val b = current.createdAt?.takeIf { it.toEpochMilli() > 0 } ?: return false
        val diff = Duration.between(a, b)
        return ChatTime.day(a, zone) == ChatTime.day(b, zone) && !diff.isNegative && diff <= WINDOW
    }

    private fun threadKey(message: Message): Long? = if (message.isThreadReply) message.threadRootMessageId else null
}

/** Deterministic order: server messages by time then id, unsent messages last by time (03 §3, §5). */
val MessageOrder: Comparator<Message> = compareBy<Message>({ it.isPending }, { it.createdAt }, { it.id })

object TimelineBuilder {
    /** A pause this long inside one day gets a [TimelineItem.TimeSeparator]. */
    val TIME_GAP: Duration = Duration.ofHours(1)

    /**
     * Builds rows oldest first. Call log messages collapse into one card placed where the newest of
     * them sits; a day separator precedes the first row of each local day.
     */
    @Suppress("LongParameterList")
    fun build(
        messages: List<Message>,
        myUserId: String?,
        zone: ZoneId,
        botConversation: Boolean,
        daySeparators: Boolean = true,
        liveArrivals: Set<Long> = emptySet(),
    ): List<TimelineItem> {
        val ambiguous = ambiguousClientIds(messages)
        val serverClientIds = messages.filter { !it.isPending }.mapNotNullTo(mutableSetOf()) { it.clientId() }
        // While a send lands, its outbox row and server copy can both be present: keep the copy.
        val sorted = messages
            .filter { !(it.isPending && it.clientId() in serverClientIds) }
            .sortedWith(MessageOrder)
        val calls = sorted.filter {
            SystemMessages.callRecordId(it) != null
        }.groupBy { SystemMessages.callRecordId(it) }
        val callPosition = calls.mapValues { (_, group) -> group.last().id }
        val items = mutableListOf<TimelineItem>()
        var lastDay: LocalDate? = null
        // The clock line the current run of bubbles started under, if any.
        var runSeparatorAt: Instant? = null
        sorted.forEachIndexed { index, message ->
            val callId = SystemMessages.callRecordId(message)
            if (callId != null && callPosition[callId] != message.id) return@forEachIndexed
            val day = message.createdAt?.let { ChatTime.day(it, zone) }
            var separatorAt: Instant? = null
            if (daySeparators && day != null && day != lastDay) {
                items += TimelineItem.DaySeparator("day-$day", day)
                lastDay = day
            } else if (daySeparators) {
                separatorAt = gapAfter(sorted.getOrNull(index - 1), message)
                separatorAt?.let { at -> items += TimelineItem.TimeSeparator("time-${message.id}", at) }
            }
            val previous = sorted.getOrNull(index - 1)
            if (separatorAt != null || !MessageGrouping.canGroup(previous, message, zone)) runSeparatorAt = separatorAt
            items += when {
                callId != null -> {
                    val group = calls.getValue(callId)
                    TimelineItem.CallLog("call-$callId", callId, group, CallLogs.summarize(group))
                }
                else -> row(
                    message = message,
                    key = keyOf(message, ambiguous),
                    previous = sorted.getOrNull(index - 1),
                    next = sorted.getOrNull(index + 1),
                    myUserId = myUserId,
                    zone = zone,
                    botConversation = botConversation,
                    liveArrivals = liveArrivals,
                    runSeparatorAt = runSeparatorAt,
                )
            }
        }
        // A LazyColumn key must be unique; this is the last guard if the rules above ever miss.
        return items.distinctBy { it.key }
    }

    /**
     * [current]'s time when it comes [TIME_GAP] or more after [previous] (callers only ask within
     * a day: a new day gets the day line instead); null when either time is unknown.
     */
    private fun gapAfter(previous: Message?, current: Message): Instant? {
        val before = previous?.createdAt?.takeIf { it.toEpochMilli() > 0 } ?: return null
        val at = current.createdAt?.takeIf { it.toEpochMilli() > 0 } ?: return null
        return at.takeIf { Duration.between(before, at) >= TIME_GAP }
    }

    @Suppress("LongParameterList")
    private fun row(
        message: Message,
        key: String,
        previous: Message?,
        next: Message?,
        myUserId: String?,
        zone: ZoneId,
        botConversation: Boolean,
        liveArrivals: Set<Long>,
        runSeparatorAt: Instant?,
    ): TimelineItem {
        val ticket = TicketNotifications.from(message, botConversation)
        if (ticket == null && SystemMessages.isSystem(message)) {
            return TimelineItem.SystemRow(key, message, SystemMessages.parse(message.text).text)
        }
        val groupedWithNext = MessageGrouping.canGroup(message, next, zone)
        return TimelineItem.MessageRow(
            key = key,
            message = message,
            isMine = message.isMine(myUserId),
            isBot = TicketNotifications.isBot(message, botConversation),
            groupedWithPrevious = MessageGrouping.canGroup(previous, message, zone),
            groupedWithNext = groupedWithNext,
            rich = if (message.isDeleted || ticket != null) null else TipTapParser.documentFor(message),
            ticket = ticket,
            reactions = Reactions.summarize(message.reactions, myUserId),
            isLiveArrival = message.id in liveArrivals,
            timeShownAbove = !groupedWithNext && sameMinute(runSeparatorAt, message.createdAt, zone),
        )
    }

    /** Both times read the same on a clock in [zone] ("12:00 PM"). */
    private fun sameMinute(a: Instant?, b: Instant?, zone: ZoneId): Boolean {
        if (a == null || b == null) return false
        return a.atZone(zone).truncatedTo(ChronoUnit.MINUTES) == b.atZone(zone).truncatedTo(ChronoUnit.MINUTES)
    }

    /**
     * Keyed by client id when there is one, so an optimistic row keeps its identity (and its place
     * on screen) when the server copy replaces it. Client ids that several server messages share
     * (seen in real payloads) fall back to the server id, since keys must be unique.
     */
    fun keyOf(message: Message, ambiguousClientIds: Set<String> = emptySet()): String =
        message.clientId()?.takeIf { it !in ambiguousClientIds }?.let { "client-$it" } ?: "message-${message.id}"

    /** Client ids carried by more than one server message. */
    fun ambiguousClientIds(messages: List<Message>): Set<String> = messages.asSequence()
        .filter { !it.isPending }
        .mapNotNull { it.clientId() }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    private fun Message.clientId(): String? = clientMessageId?.takeIf(String::isNotBlank)
}
