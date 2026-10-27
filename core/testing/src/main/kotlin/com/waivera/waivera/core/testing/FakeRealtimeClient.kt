package com.waivera.waivera.core.testing

import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.realtime.HubEvent
import com.waivera.waivera.core.realtime.RealtimeClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.serialization.json.JsonElement

/**
 * A [RealtimeClient] driven by the test: set [connected], push events with [emit], simulate a
 * reconnect with [reconnect]. Calls are recorded; [sendResult] answers `SendMessage`.
 */
class FakeRealtimeClient(connected: Boolean = true) : RealtimeClient {
    val mutableState = MutableStateFlow<ConnectionState>(if (connected) ConnectionState.Connected else ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = mutableState

    private val epoch = MutableStateFlow(if (connected) 1L else 0L)
    override val connectionEpoch: StateFlow<Long> = epoch
    override val resyncRequests: Flow<Long> = epoch.drop(1).filter { it > 1 }

    private val _events = MutableSharedFlow<HubEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    val calls = mutableListOf<Pair<String, List<Any?>>>()
    val attended = mutableMapOf<Long, Int>()
    var startedFor: String? = null
    var stopped = 0
    var foreground: Boolean? = null
    var retries = 0
    var failNext: WaiveraError? = null
    var sendResult: (conversationId: Long, text: String, clientMessageId: String?, parentMessageId: Long?) -> MessageSendResponse =
        { conversationId, text, clientId, parent ->
            MessageSendResponse(
                id = nextId++,
                conversationId = conversationId,
                clientMessageId = clientId,
                userId = startedFor,
                messageContent = text,
                parentMessageId = parent,
            )
        }
    private var nextId = 90_000L

    var connected: Boolean
        get() = mutableState.value == ConnectionState.Connected
        set(value) {
            mutableState.value = if (value) ConnectionState.Connected else ConnectionState.Disconnected
        }

    suspend fun emit(event: HubEvent) = _events.emit(event)

    /** A drop and reconnect: bumps the epoch, which asks repositories to resync. */
    fun reconnect() {
        connected = true
        epoch.value += 1
    }

    override fun start(userId: String) {
        startedFor = userId
    }

    override suspend fun stop() {
        stopped++
        startedFor = null
        attended.clear()
    }

    override fun setForeground(foreground: Boolean) {
        this.foreground = foreground
    }

    override fun retryNow() {
        retries++
    }

    override fun attendConversation(conversationId: Long): AutoCloseable {
        attended[conversationId] = (attended[conversationId] ?: 0) + 1
        return AutoCloseable {
            val count = (attended[conversationId] ?: 1) - 1
            if (count <= 0) attended.remove(conversationId) else attended[conversationId] = count
        }
    }

    private fun record(method: String, args: List<Any?>) {
        if (!connected) throw WaiveraError.RealtimeUnavailable()
        calls += method to args
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun sendMessage(
        conversationId: Long,
        text: String,
        doc: JsonElement?,
        parentMessageId: Long?,
        clientMessageId: String?,
        attachmentUrl: String?,
    ): MessageSendResponse {
        record("SendMessage", listOf(conversationId, text, doc, parentMessageId, clientMessageId, attachmentUrl))
        return sendResult(conversationId, text, clientMessageId, parentMessageId)
    }

    override suspend fun sendTypingStatus(conversationId: Long, isTyping: Boolean) =
        record("SendTypingStatus", listOf(conversationId, isTyping))

    override suspend fun markAsRead(conversationId: Long) = record("MarkAsRead", listOf(conversationId))

    override suspend fun sendReaction(messageId: Long, reactionType: String) = record("SendReaction", listOf(messageId, reactionType))
}
