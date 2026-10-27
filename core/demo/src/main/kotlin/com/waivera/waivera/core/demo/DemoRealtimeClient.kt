@file:Suppress("MagicNumber")

package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.repository.DataLayerStarter
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.dto.AttentionReason
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.ReactionAction
import com.waivera.waivera.core.network.dto.UserInfoResponse
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.realtime.HubEvent
import com.waivera.waivera.core.realtime.RealtimeClient
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * A hub that never opens a socket. It reports Connected, and [DemoHub] pushes scripted events
 * through it (and into [DemoBackend]) so realtime UI can be seen and screenshot.
 */
@Singleton
@Suppress("TooManyFunctions")
class DemoRealtimeClient @Inject constructor() : RealtimeClient {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val epoch = MutableStateFlow(1L)
    override val connectionEpoch: StateFlow<Long> = epoch.asStateFlow()
    override val resyncRequests: Flow<Long> = epoch.drop(1).filter { it > 1 }

    private val _events = MutableSharedFlow<HubEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    suspend fun emit(event: HubEvent) = _events.emit(event)

    /** Shows the reconnecting banner until [retryNow] (or [reconnect]) is called. */
    fun drop() {
        _state.value = ConnectionState.Reconnecting(attempt = 2, delayMillis = 4_000)
    }

    fun reconnect() {
        _state.value = ConnectionState.Connected
        epoch.update { it + 1 }
    }

    override fun start(userId: String) = Unit

    override suspend fun stop() {
        _state.value = ConnectionState.Disconnected
    }

    override fun setForeground(foreground: Boolean) = Unit

    override fun retryNow() = reconnect()

    override fun attendConversation(conversationId: Long): AutoCloseable = AutoCloseable { }

    override suspend fun sendMessage(
        conversationId: Long,
        text: String,
        doc: JsonElement?,
        parentMessageId: Long?,
        clientMessageId: String?,
        attachmentUrl: String?,
    ): MessageSendResponse = throw WaiveraError.RealtimeUnavailable()

    override suspend fun sendTypingStatus(conversationId: Long, isTyping: Boolean) = Unit

    override suspend fun markAsRead(conversationId: Long) = Unit

    override suspend fun sendReaction(messageId: Long, reactionType: String) = Unit
}

/**
 * The scripted realtime sequence, played once per process after start: Priya types in the DM,
 * sends a message that mentions Alex, then reacts to Alex's last message.
 */
@Singleton
class DemoHub @Inject constructor(
    private val backend: DemoBackend,
    private val client: DemoRealtimeClient,
    @ApplicationScope private val scope: CoroutineScope,
) : DataLayerStarter {
    private val played = AtomicBoolean(false)

    override fun start() {
        if (!played.compareAndSet(false, true)) return
        scope.launch { play() }
    }

    suspend fun play() {
        delay(START_DELAY_MILLIS)
        setTyping(DemoData.DM_PRIYA, DemoData.PRIYA, true)
        delay(TYPING_MILLIS)
        setTyping(DemoData.DM_PRIYA, DemoData.PRIYA, false)
        val dto = MessageSendResponse(
            id = backend.nextMessageId(),
            conversationId = DemoData.DM_PRIYA,
            author = UserInfoResponse(
                id = DemoData.PRIYA,
                firstName = "Priya",
                lastName = "Raman",
                displayName = "Priya Raman",
            ),
            userId = DemoData.PRIYA,
            messageContent = "@Alex Rivera can you confirm Friday by noon?",
            createDate = backend.now(),
        )
        backend.append(com.waivera.waivera.core.data.live.Mappers.message(dto))
        client.emit(HubEvent.MessageReceived(dto))
        client.emit(HubEvent.ChatAttention(dto, AttentionReason.MENTION))
        delay(REACTION_DELAY_MILLIS)
        backend.updateMessage(REACTED_MESSAGE) { m ->
            m.copy(
                reactions =
                m.reactions.filter { it.userId != DemoData.PRIYA } +
                    com.waivera.waivera.core.data.model.Reaction(DemoData.PRIYA, "🙌"),
            )
        }
        client.emit(HubEvent.Reaction(DemoData.DM_PRIYA, REACTED_MESSAGE, DemoData.PRIYA, "🙌", ReactionAction.ADDED))
    }

    private suspend fun setTyping(conversationId: Long, userId: String, typing: Boolean) {
        backend.typing.update { all ->
            val users = if (typing) all[conversationId].orEmpty() + userId else all[conversationId].orEmpty() - userId
            all + (conversationId to users)
        }
        client.emit(HubEvent.TypingStatus(conversationId, userId, typing))
    }

    private companion object {
        const val START_DELAY_MILLIS = 4_000L
        const val TYPING_MILLIS = 3_000L
        const val REACTION_DELAY_MILLIS = 5_000L
        const val REACTED_MESSAGE = 9312L
    }
}
