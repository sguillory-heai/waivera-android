package com.waivera.waivera.core.realtime

import com.waivera.waivera.core.network.AuthTokenProvider
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.error.ErrorMapper
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.json.withCamelCaseKeys
import com.waivera.waivera.core.network.log.WaiveraLog
import com.waivera.waivera.core.realtime.ChatHubContract.Methods
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * [RealtimeClient] that keeps one [HubTransport] alive while signed in and in the foreground.
 *
 * Each attempt gets a fresh transport and a generation number; callbacks from an older
 * transport are ignored, so a late `onClosed` can never tear down its replacement. After a
 * failed attempt the next handshake force-refreshes the ID token, which covers a token that
 * expired while the app slept.
 */
@Singleton
class DefaultRealtimeClient @Inject constructor(
    private val config: NetworkConfig,
    private val tokens: AuthTokenProvider,
    private val transportFactory: HubTransportFactory,
    @ApplicationScope private val scope: CoroutineScope,
    private val policy: ReconnectPolicy,
) : RealtimeClient {
    private val userId = MutableStateFlow<String?>(null)
    private val foreground = MutableStateFlow(false)
    private val retrySignal = Channel<Unit>(Channel.CONFLATED)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _epoch = MutableStateFlow(0L)
    override val connectionEpoch: StateFlow<Long> = _epoch.asStateFlow()
    override val resyncRequests: Flow<Long> = _epoch.drop(1).filter { it > 1 }

    // Unlimited inbox keeps the SignalR callback thread non-blocking while preserving order.
    private val inbox = Channel<HubEvent>(Channel.UNLIMITED)
    private val _events = MutableSharedFlow<HubEvent>(extraBufferCapacity = EVENT_BUFFER)
    override val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    private val lock = Mutex()
    private val attended = mutableMapOf<Long, Int>()
    private var current: Live? = null
    private var generation = 0L

    private class Live(val generation: Long, val transport: HubTransport, val closed: CompletableDeferred<Throwable?>)

    init {
        scope.launch { for (event in inbox) _events.emit(event) }
        scope.launch {
            combine(userId, foreground) { id, fg -> id.takeIf { fg } }
                .distinctUntilChanged()
                .collectLatest { id ->
                    if (id == null) {
                        _state.value = ConnectionState.Disconnected
                    } else {
                        runConnection()
                    }
                }
        }
    }

    override fun start(userId: String) {
        val previous = this.userId.value
        if (previous != null && previous != userId) {
            scope.launch { lock.withLock { attended.clear() } }
            _epoch.value = 0
        }
        this.userId.value = userId
    }

    override suspend fun stop() {
        userId.value = null
        lock.withLock {
            attended.clear()
            current?.let { live ->
                current = null
                runCatching { live.transport.stop() }
            }
        }
        _epoch.value = 0
        _state.value = ConnectionState.Disconnected
    }

    override fun setForeground(foreground: Boolean) {
        this.foreground.value = foreground
    }

    override fun retryNow() {
        retrySignal.trySend(Unit)
    }

    /** Runs until cancelled by sign-out or backgrounding; never gives up on its own. */
    private suspend fun runConnection() {
        val url = config.chatHubUrl
        if (url == null) {
            WaiveraLog.w(TAG, "Hub not configured; staying disconnected")
            _state.value = ConnectionState.Disconnected
            return
        }
        var attempt = 0
        var forceRefresh = false
        try {
            while (shouldRun()) {
                attempt++
                waitBeforeAttempt(attempt)
                _state.value = ConnectionState.Connecting
                val live = open(url, forceRefresh)
                val connected = runCatching { live.transport.start() }
                    .onFailure { if (it is CancellationException) throw it }
                    .isSuccess
                if (connected) {
                    attempt = 0
                    forceRefresh = false
                    onConnected(live)
                    val cause = live.closed.await()
                    WaiveraLog.d(TAG, "Hub closed (${cause?.javaClass?.simpleName ?: "clean"})")
                    // stop() closes the transport before collectLatest cancels this loop.
                    if (!shouldRun()) return
                } else {
                    forceRefresh = true
                    lock.withLock { if (current === live) current = null }
                }
            }
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    current?.let { runCatching { it.transport.stop() } }
                    current = null
                }
                _state.value = ConnectionState.Disconnected
            }
        }
    }

    private fun shouldRun(): Boolean = userId.value != null && foreground.value

    private suspend fun waitBeforeAttempt(attempt: Int) {
        // Drain a stale retry tap so it cannot skip a later wait.
        retrySignal.tryReceive()
        val delayMillis = policy.delayFor(attempt)
        if (delayMillis <= 0) return
        _state.value = ConnectionState.Reconnecting(attempt, delayMillis)
        withTimeoutOrNull(delayMillis) { retrySignal.receive() }
    }

    private suspend fun open(url: String, forceRefresh: Boolean): Live = lock.withLock {
        val gen = ++generation
        val closed = CompletableDeferred<Throwable?>()
        val listener = object : HubTransportListener {
            override fun onInvocation(target: String, arguments: JsonArray) {
                if (gen != generation) return
                val event = HubEventDecoder.decode(target, arguments)
                if (event == null) {
                    WaiveraLog.w(TAG, "Dropped hub event $target (${arguments.size} args)")
                } else {
                    inbox.trySend(event)
                }
            }

            override fun onClosed(error: Throwable?) {
                closed.complete(error)
            }
        }
        val transport = transportFactory.create(url, tokens::idToken, forceRefresh, listener)
        Live(gen, transport, closed).also { current = it }
    }

    private suspend fun onConnected(live: Live) {
        val toJoin = lock.withLock {
            if (current !== live) return
            attended.keys.toList()
        }
        _state.value = ConnectionState.Connected
        toJoin.forEach { id -> join(live, id) }
        _epoch.value += 1
    }

    private suspend fun join(live: Live, conversationId: Long) {
        try {
            live.transport.invokeUnit(Methods.JOIN_CONVERSATION, listOf(conversationId))
        } catch (e: HubInvocationException) {
            WaiveraLog.w(TAG, "JoinConversation failed for $conversationId", e)
        }
    }

    override fun attendConversation(conversationId: Long): AutoCloseable {
        scope.launch {
            val live = lock.withLock {
                val count = attended.getOrElse(conversationId) { 0 } + 1
                attended[conversationId] = count
                current.takeIf { count == 1 && _state.value == ConnectionState.Connected }
            }
            live?.let { join(it, conversationId) }
        }
        return Lease(conversationId)
    }

    private inner class Lease(private val conversationId: Long) : AutoCloseable {
        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            scope.launch {
                val live = lock.withLock {
                    val count = attended.getOrElse(conversationId) { 0 } - 1
                    if (count > 0) attended[conversationId] = count else attended.remove(conversationId)
                    current.takeIf { count <= 0 && _state.value == ConnectionState.Connected }
                }
                live?.let {
                    runCatching { it.transport.invokeUnit(Methods.LEAVE_CONVERSATION, listOf(conversationId)) }
                }
            }
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
        val result = call { transport ->
            transport.invoke(
                Methods.SEND_MESSAGE,
                listOf(conversationId, text, doc, parentMessageId, clientMessageId, attachmentUrl),
            )
        }
        return try {
            WaiveraJson.decodeFromJsonElement(MessageSendResponse.serializer(), result.withCamelCaseKeys())
        } catch (e: IllegalArgumentException) {
            throw WaiveraError.Unexpected(cause = e)
        }
    }

    override suspend fun sendTypingStatus(conversationId: Long, isTyping: Boolean) = call { transport ->
        transport.invokeUnit(Methods.SEND_TYPING_STATUS, listOf(conversationId, isTyping))
    }

    override suspend fun markAsRead(conversationId: Long) = call { transport ->
        transport.invokeUnit(Methods.MARK_AS_READ, listOf(conversationId))
    }

    override suspend fun sendReaction(messageId: Long, reactionType: String) = call { transport ->
        transport.invokeUnit(Methods.SEND_REACTION, listOf(messageId, reactionType))
    }

    private suspend fun <T> call(block: suspend (HubTransport) -> T): T {
        val live = lock.withLock { current.takeIf { _state.value == ConnectionState.Connected } }
            ?: throw WaiveraError.RealtimeUnavailable()
        return try {
            block(live.transport)
        } catch (e: HubInvocationException) {
            throw if (live.closed.isCompleted) WaiveraError.RealtimeUnavailable() else ErrorMapper.fromHubMessage(e.message)
        }
    }

    private companion object {
        const val TAG = "Realtime"
        const val EVENT_BUFFER = 256
    }
}
