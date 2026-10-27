package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.AttachmentSource
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessageTimeline
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.outbox.OutboxAttachment
import com.waivera.waivera.core.data.outbox.OutboxDirectory
import com.waivera.waivera.core.data.outbox.OutboxItem
import com.waivera.waivera.core.data.outbox.OutboxProcessor
import com.waivera.waivera.core.data.outbox.OutboxScheduler
import com.waivera.waivera.core.data.outbox.OutboxStore
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.di.IoDispatcher
import com.waivera.waivera.core.network.dto.EditMessageRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.realtime.RealtimeClient
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Per-conversation paging state (G-06: there is no envelope, so has-more is inferred). */
@Serializable
data class TimelineMeta(val hasOlder: Boolean = true)

@Singleton
@Suppress("LongParameterList", "TooManyFunctions")
class LiveMessageRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
    private val ingest: MessageIngest,
    private val outbox: OutboxStore,
    private val processor: OutboxProcessor,
    private val scheduler: OutboxScheduler,
    private val realtime: RealtimeClient,
    private val session: SessionRepository,
    private val conversations: LiveConversationRepository,
    private val typing: TypingTracker,
    private val clock: Clock,
    @OutboxDirectory private val outboxDirectory: File,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) : MessageRepository {
    private val active = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val loadingOlder = MutableStateFlow<Set<Long>>(emptySet())

    /** Conversations with an open timeline, refetched after every reconnect or resume. */
    val activeConversations: Set<Long> get() = active.value.keys

    override fun observeTimeline(conversationId: Long): Flow<Synced<MessageTimeline>> = flow {
        val lease = realtime.attendConversation(conversationId)
        active.update { it + (conversationId to (it[conversationId] ?: 0) + 1) }
        scope.launch { refreshLatest(conversationId) }
        try {
            emitAll(timeline(conversationId))
        } finally {
            lease.close()
            active.update { current ->
                val count = (current[conversationId] ?: 1) - 1
                if (count <= 0) current - conversationId else current + (conversationId to count)
            }
        }
    }

    private fun timeline(conversationId: Long): Flow<Synced<MessageTimeline>> {
        val meta = cache.observeDocument(metaKey(conversationId), TimelineMeta.serializer())
        val merged = combine(
            cache.observeMessages(conversationId),
            outbox.observeConversation(conversationId),
            meta,
            loadingOlder.map { conversationId in it },
        ) { messages, pending, pageMeta, isLoadingOlder ->
            MessageTimeline(
                messages = merge(messages, pending),
                hasOlder = pageMeta?.hasOlder ?: true,
                isLoadingOlder = isLoadingOlder,
            )
        }
        return sync.synced(syncKey(conversationId), merged)
    }

    private fun merge(messages: List<Message>, pending: List<OutboxItem>): List<Message> {
        val confirmed = messages.mapNotNull { it.clientMessageId }.toSet()
        val author = session.state.value.currentUser?.let {
            com.waivera.waivera.core.data.model.Person(
                it.userId,
                it.firstName,
                it.lastName,
                it.displayName,
                it.email,
                it.avatarUrl,
            )
        }
        return messages + pending.filter { it.clientMessageId !in confirmed }.map { it.toMessage(author) }
    }

    override suspend fun refreshLatest(conversationId: Long): Result<Unit> = sync.track(syncKey(conversationId)) {
        val page = apiCall {
            chatApi.getMessagesPage(conversationId, ChatApi.MESSAGE_PAGE_SIZE, null)
        }.map(Mappers::message)
        cache.transactions.run {
            val newestCached = cache.messageDao.newestId(conversationId)
            val oldestFetched = page.minOfOrNull { it.id }
            val full = page.size >= ChatApi.MESSAGE_PAGE_SIZE
            // A full page entirely newer than the cache leaves a hole; drop the stale cache.
            val gap = newestCached != null && full && oldestFetched != null && oldestFetched > newestCached
            if (gap) cache.messageDao.deleteConversation(conversationId)
            ingest.ingest(page)
            if (newestCached == null || gap) {
                cache.putDocument(metaKey(conversationId), TimelineMeta.serializer(), TimelineMeta(hasOlder = full))
            }
        }
        page.filter { !it.isThreadReply }.maxByOrNull { it.id }?.let { conversations.onMessage(it) }
    }

    override suspend fun loadOlder(conversationId: Long): Result<Boolean> = catchingWaivera {
        val meta = cache.document(metaKey(conversationId), TimelineMeta.serializer())
        if (meta?.hasOlder == false) return@catchingWaivera false
        loadingOlder.update { it + conversationId }
        try {
            val oldest = cache.messageDao.getOldestId(conversationId)
            val page = apiCall {
                chatApi.getMessagesPage(conversationId, ChatApi.MESSAGE_PAGE_SIZE, oldest)
            }.map(Mappers::message)
            ingest.ingest(page)
            val newOldest = page.minOfOrNull { it.id }
            // G-06 stop rules: a short page, or an oldest id that did not move back.
            val hasOlder =
                page.size >= ChatApi.MESSAGE_PAGE_SIZE && newOldest != null && (oldest == null || newOldest < oldest)
            cache.putDocument(metaKey(conversationId), TimelineMeta.serializer(), TimelineMeta(hasOlder))
            hasOlder
        } finally {
            loadingOlder.update { it - conversationId }
        }
    }

    override suspend fun loadAround(conversationId: Long, messageId: Long): Result<Unit> = catchingWaivera {
        val page = apiCall {
            chatApi.getMessageContext(conversationId, messageId, ChatApi.CONTEXT_WINDOW, ChatApi.CONTEXT_WINDOW)
        }.map(Mappers::message)
        ingest.ingest(page)
    }

    override suspend fun send(
        conversationId: Long,
        text: String,
        doc: JsonElement?,
        attachment: AttachmentSource?,
        parentMessageId: Long?,
    ): String {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        val clientId = UUID.randomUUID().toString()
        val staged = attachment?.let { stage(clientId, it) }
        outbox.put(
            OutboxItem(
                clientMessageId = clientId,
                conversationId = conversationId,
                authorId = me,
                text = text,
                doc = doc,
                parentMessageId = parentMessageId,
                attachment = staged,
                createdAt = clock.instant(),
            ),
        )
        typing.onMessageSent(conversationId)
        kick()
        return clientId
    }

    private suspend fun stage(clientId: String, source: AttachmentSource): OutboxAttachment = withContext(io) {
        outboxDirectory.mkdirs()
        val target = File(outboxDirectory, clientId)
        source.open().use { input -> target.outputStream().use { input.copyTo(it) } }
        OutboxAttachment(target.absolutePath, source.fileName, source.contentType, source.size ?: target.length())
    }

    /** Sends now in-process, and leaves a durable retry with WorkManager if anything is left. */
    private fun kick() {
        scope.launch {
            val leftover = runCatching { processor.process() }.getOrDefault(true)
            if (leftover) scheduler.schedule()
        }
    }

    override suspend fun retry(clientMessageId: String): Result<Unit> = catchingWaivera {
        val item = outbox.get(clientMessageId) ?: throw WaiveraError.NotFound()
        outbox.put(item.copy(state = SendState.QUEUED, attempts = 0, failure = null))
        kick()
    }

    override suspend fun discard(clientMessageId: String) {
        outbox.get(clientMessageId)?.let { outbox.remove(it) }
    }

    override suspend fun edit(messageId: Long, text: String, doc: JsonElement?): Result<Message> = catchingWaivera {
        val updated = Mappers.message(
            apiCall {
                chatApi.editMessage(messageId, EditMessageRequest(text, doc))
            }.requireData(),
        )
        val merged = cache.message(messageId)?.let { cached ->
            cached.copy(
                text = updated.text,
                doc = updated.doc,
                isEdited = true,
                updatedAt =
                updated.updatedAt ?: clock.instant(),
            )
        } ?: updated
        cache.putMessages(listOf(merged))
        conversations.onMessageChanged(merged)
        merged
    }

    override suspend fun delete(messageId: Long): Result<Unit> = catchingWaivera {
        apiCall { chatApi.deleteMessage(messageId) }
        markDeleted(messageId)
    }

    suspend fun markDeleted(messageId: Long) {
        cache.updateMessage(messageId) {
            it.copy(isDeleted = true, text = null, doc = null, attachment = null, reactions = emptyList())
        }
        cache.message(messageId)?.let { conversations.onMessageChanged(it) }
    }

    /** Hub only (G-01); optimistic with rollback. */
    override suspend fun react(messageId: Long, emoji: String): Result<Unit> = catchingWaivera {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        if (realtime.state.value != ConnectionState.Connected) throw WaiveraError.RealtimeUnavailable()
        val before = cache.message(messageId) ?: throw WaiveraError.NotFound()
        cache.putMessages(listOf(before.copy(reactions = toggle(before.reactions, me, emoji))))
        try {
            realtime.sendReaction(messageId, emoji)
        } catch (e: WaiveraError) {
            cache.updateMessage(messageId) { it.copy(reactions = before.reactions) }
            throw e
        }
    }

    /** Server rules: one reaction per user; the same emoji removes it, another replaces it. */
    fun toggle(reactions: List<Reaction>, userId: String, emoji: String): List<Reaction> {
        val existing = reactions.firstOrNull { it.userId == userId }
        val others = reactions.filter { it.userId != userId }
        return if (existing?.emoji == emoji) others else others + Reaction(userId, emoji)
    }

    /** Applies a `ReceiveReaction` event; authoritative, so it also settles optimistic state. */
    suspend fun applyReaction(messageId: Long, userId: String, emoji: String?) {
        cache.updateMessage(messageId) { m ->
            val others = m.reactions.filter { it.userId != userId }
            m.copy(reactions = if (emoji == null) others else others + Reaction(userId, emoji))
        }
    }

    private fun metaKey(conversationId: Long) = "timeline:$conversationId"

    private fun syncKey(conversationId: Long) = "messages:$conversationId"
}
