package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.realtime.RealtimeClient
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Typing indicators (PLAN parity fix). Outgoing: at most one `isTyping=true` per 3 s per
 * conversation, and `false` after 1.8 s idle or when the composer empties. Incoming: the server
 * has no expiry, so a user stops "typing" 5 s after their last event.
 */
@Singleton
class TypingTracker @Inject constructor(
    private val realtime: RealtimeClient,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val typing = MutableStateFlow<Map<Long, Set<String>>>(emptyMap())
    private val expiryJobs = mutableMapOf<Pair<Long, String>, Job>()
    private val lastSentAt = mutableMapOf<Long, Long>()
    private val idleJobs = mutableMapOf<Long, Job>()
    private val sentTyping = mutableSetOf<Long>()

    fun observe(conversationId: Long): Flow<Set<String>> =
        typing.map { it[conversationId].orEmpty() }.distinctUntilChanged()

    fun onRemoteTyping(conversationId: Long, userId: String, isTyping: Boolean, myUserId: String?) {
        if (userId == myUserId) return
        val key = conversationId to userId
        synchronized(expiryJobs) {
            expiryJobs.remove(key)?.cancel()
            if (isTyping) {
                expiryJobs[key] = scope.launch {
                    delay(INCOMING_EXPIRY_MILLIS)
                    remove(conversationId, userId)
                }
            }
        }
        if (isTyping) {
            typing.update { it + (conversationId to (it[conversationId].orEmpty() + userId)) }
        } else {
            remove(conversationId, userId)
        }
    }

    /** A new message from [userId] ends their typing indicator at once. */
    fun onMessageFrom(conversationId: Long, userId: String?) {
        if (userId != null) remove(conversationId, userId)
    }

    private fun remove(conversationId: Long, userId: String) {
        typing.update { current ->
            val remaining = current[conversationId].orEmpty() - userId
            if (remaining.isEmpty()) current - conversationId else current + (conversationId to remaining)
        }
    }

    fun onComposerChanged(conversationId: Long, isEmpty: Boolean) {
        synchronized(sentTyping) {
            idleJobs.remove(conversationId)?.cancel()
            if (isEmpty) {
                if (sentTyping.remove(conversationId)) send(conversationId, false)
                return
            }
            val now = clock.millis()
            val last = lastSentAt[conversationId]
            // Re-sending while typing keeps receivers inside their 5 s expiry.
            if (last == null || now - last >= OUTGOING_THROTTLE_MILLIS) {
                lastSentAt[conversationId] = now
                sentTyping += conversationId
                send(conversationId, true)
            }
            idleJobs[conversationId] = scope.launch {
                delay(OUTGOING_IDLE_MILLIS)
                synchronized(sentTyping) { sentTyping.remove(conversationId) }
                send(conversationId, false)
            }
        }
    }

    /** Called after sending a message: the server shows no typing once the message lands. */
    fun onMessageSent(conversationId: Long) {
        synchronized(sentTyping) {
            idleJobs.remove(conversationId)?.cancel()
            sentTyping.remove(conversationId)
            lastSentAt.remove(conversationId)
        }
    }

    private fun send(conversationId: Long, isTyping: Boolean) {
        if (realtime.state.value != ConnectionState.Connected) return
        scope.launch { runCatching { realtime.sendTypingStatus(conversationId, isTyping) } }
    }

    fun clear() {
        synchronized(expiryJobs) {
            expiryJobs.values.forEach(Job::cancel)
            expiryJobs.clear()
        }
        typing.value = emptyMap()
    }

    companion object {
        const val OUTGOING_THROTTLE_MILLIS = 3_000L
        const val OUTGOING_IDLE_MILLIS = 1_800L
        const val INCOMING_EXPIRY_MILLIS = 5_000L
    }
}
