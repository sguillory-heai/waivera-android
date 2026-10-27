package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import java.time.Instant

/** What changed in the outbox since the previous emission. */
data class SendEvents(val newFailures: Int = 0, val retrySucceeded: Boolean = false)

/**
 * Watches outbox rows across emissions so the screen can raise the send error banner (03 §16)
 * only for failures that happen while it is open, and buzz once when a retried message lands.
 * Rows already failed when the screen opens show their inline Retry without a banner.
 */
class SendTracker {
    private var initialized = false
    private var failed: Set<String> = emptySet()
    private val retried = mutableSetOf<String>()

    fun markRetried(clientMessageId: String) {
        retried += clientMessageId
    }

    fun update(messages: List<Message>): SendEvents {
        val nowFailed = messages.asSequence()
            .filter { it.sendState == SendState.FAILED }
            .mapNotNull { it.clientMessageId }
            .toSet()
        val fresh = nowFailed - failed
        val newFailures = if (initialized) fresh.size else 0
        val sent = messages.asSequence()
            .filter { it.sendState == SendState.SENT }
            .mapNotNull { it.clientMessageId }
            .filter { it in retried }
            .toList()
        retried -= sent.toSet()
        retried -= fresh
        failed = nowFailed
        initialized = true
        return SendEvents(newFailures = newFailures, retrySucceeded = sent.isNotEmpty())
    }
}

/**
 * Keys that appeared after the first emission and are not older than what was already shown, so
 * only live arrivals and fresh sends animate in (the first load and paging older stay still).
 * Keyed like rows, so an optimistic message does not animate again when its server copy lands.
 */
class ArrivalTracker {
    private var seen: Set<String>? = null
    private var newest: Instant? = null

    /** Returns the ids of the messages that just arrived. */
    fun update(messages: List<Message>): Set<Long> {
        val previous = seen
        val previousNewest = newest
        val ambiguous = TimelineBuilder.ambiguousClientIds(messages)
        seen = messages.mapTo(mutableSetOf()) { TimelineBuilder.keyOf(it, ambiguous) }
        newest = messages.mapNotNull { it.createdAt }.maxOrNull() ?: previousNewest
        if (previous == null) return emptySet()
        // The first page arriving after an empty cache is history, except for one's own send.
        if (previous.isEmpty()) return messages.filter { it.isPending }.mapTo(mutableSetOf()) { it.id }
        return messages.asSequence()
            .filter { TimelineBuilder.keyOf(it, ambiguous) !in previous }
            .filter { it.isPending || previousNewest == null || it.createdAt?.isBefore(previousNewest) != true }
            .mapTo(mutableSetOf()) { it.id }
    }

    companion object {
        /**
         * True when [arrivals] hold a sent message from someone other than [myUserId], which earns
         * the receive cue. Unknown identity never buzzes, so a cold start cannot misfire.
         */
        fun hasIncoming(messages: List<Message>, arrivals: Set<Long>, myUserId: String?): Boolean =
            myUserId != null &&
                arrivals.isNotEmpty() &&
                messages.any { it.id in arrivals && !it.isPending && it.authorId != myUserId }
    }
}
