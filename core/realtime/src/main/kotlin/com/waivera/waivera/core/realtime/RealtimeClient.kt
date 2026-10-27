package com.waivera.waivera.core.realtime

import com.waivera.waivera.core.network.dto.MessageSendResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/** Connection state shown to the user as a quiet banner, never as a blocking error. */
sealed interface ConnectionState {
    /** Signed out, in the background, or not configured. */
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data object Connected : ConnectionState

    /**
     * Waiting [delayMillis] before reconnect attempt number [attempt]. Retries never stop while
     * the app is in the foreground; [RealtimeClient.retryNow] skips the wait.
     */
    data class Reconnecting(val attempt: Int, val delayMillis: Long) : ConnectionState
}

/**
 * The app's only door to the chat hub (`{apiBase}/api/chatHub`).
 *
 * The connection runs only while a user is signed in ([start]) and the app is in the
 * foreground ([setForeground]); backgrounded apps rely on push. The SignalR Java client has no
 * automatic reconnect, so implementations retry with [ReconnectPolicy], rejoin every attended
 * conversation after each connect, and bump [connectionEpoch] so repositories resync their
 * tails over REST (hub.md §4, gaps.md G-07).
 */
interface RealtimeClient {
    val state: StateFlow<ConnectionState>

    /** Increments on every successful connect; 0 before the first one of this sign-in. */
    val connectionEpoch: StateFlow<Long>

    /** Emits the epoch after each reconnect (every connect but the first of a sign-in). */
    val resyncRequests: Flow<Long>

    /** Decoded server events, in arrival order. No replay: collect before connecting. */
    val events: SharedFlow<HubEvent>

    /** Allows the connection for [userId] (the backend id). Switching users drops joins and restarts. */
    fun start(userId: String)

    /** Disconnects and forgets attended conversations, for sign-out. */
    suspend fun stop()

    fun setForeground(foreground: Boolean)

    /** Skips the current backoff wait, for a "Retry" tap or connectivity coming back. */
    fun retryNow()

    /**
     * Joins `conversation_{id}` while at least one lease is open, and again after every
     * reconnect. Thread screens attend their parent conversation, which carries thread replies.
     * Close the lease when the screen leaves.
     */
    fun attendConversation(conversationId: Long): AutoCloseable

    /** Throws [com.waivera.waivera.core.network.error.WaiveraError] (RealtimeUnavailable when disconnected). */
    suspend fun sendMessage(
        conversationId: Long,
        text: String,
        doc: JsonElement? = null,
        parentMessageId: Long? = null,
        clientMessageId: String? = null,
        attachmentUrl: String? = null,
    ): MessageSendResponse

    suspend fun sendTypingStatus(conversationId: Long, isTyping: Boolean)

    suspend fun markAsRead(conversationId: Long)

    /** Toggle semantics on the server: the same emoji removes, a different one replaces. */
    suspend fun sendReaction(messageId: Long, reactionType: String)
}
