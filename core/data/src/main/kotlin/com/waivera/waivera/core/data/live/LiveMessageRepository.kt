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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Per-conversation paging state (G-06: there is no envelope, so has-more is inferred).
 * [hasOlder] is about the oldest cached message; [gaps] are holes inside the cache (AF-03).
 */
@Serializable
data class TimelineMeta(val hasOlder: Boolean = true, val gaps: List<TimelineGap> = emptyList())

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
            val gaps = pageMeta?.gaps.orEmpty()
            val floor = TimelineGaps.windowFloor(gaps, messages.filter { !it.isThreadReply }.map { it.id })
            // Only the newest segment is shown, so nobody scrolls across a hole without noticing;
            // reaching its top pages into the hole (loadOlder) until it joins the next segment.
            val window = if (floor == null) messages else messages.filter { it.isThreadReply || it.id >= floor }
            MessageTimeline(
                messages = merge(window, pending),
                hasOlder = floor != null || (pageMeta?.hasOlder ?: true),
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
            val meta = meta(conversationId)
            val newestCached = cache.messageDao.newestId(conversationId)
            val oldestFetched = page.minOfOrNull { it.id }
            val full = page.size >= ChatApi.MESSAGE_PAGE_SIZE
            val hole = newestCached != null && full && oldestFetched != null && oldestFetched > newestCached
            // A stale cache with no jump in it is simply dropped. A cache holding a context page
            // (a jump to an old message) keeps it and records the hole, which paging fills.
            val dropStale = hole && meta.gaps.isEmpty()
            if (dropStale) cache.messageDao.deleteConversation(conversationId)
            ingest.ingest(page)
            val updated = when {
                newestCached == null || dropStale -> TimelineMeta(hasOlder = full)
                oldestFetched == null -> meta
                !full -> meta.copy(gaps = TimelineGaps.cover(meta.gaps, Long.MIN_VALUE, Long.MAX_VALUE))
                else -> {
                    val covered = TimelineGaps.cover(meta.gaps, oldestFetched, Long.MAX_VALUE)
                    val withHole = if (hole && newestCached != null) {
                        TimelineGaps.add(covered, TimelineGap(newestCached, oldestFetched))
                    } else {
                        covered
                    }
                    meta.copy(gaps = withHole)
                }
            }
            if (updated != meta || newestCached == null) putMeta(conversationId, updated)
        }
        page.filter { !it.isThreadReply }.maxByOrNull { it.id }?.let { conversations.onMessage(it) }
    }

    override suspend fun loadOlder(conversationId: Long): Result<Boolean> = catchingWaivera {
        val meta = meta(conversationId)
        val floor = TimelineGaps.windowFloor(meta.gaps, timelineIds(conversationId))
        if (floor == null && !meta.hasOlder) return@catchingWaivera false
        loadingOlder.update { it + conversationId }
        try {
            val before = floor ?: cache.messageDao.getOldestId(conversationId)
            val page = apiCall {
                chatApi.getMessagesPage(conversationId, ChatApi.MESSAGE_PAGE_SIZE, before)
            }.map(Mappers::message)
            cache.transactions.run {
                ingest.ingest(page)
                val latest = meta(conversationId)
                val updated = if (floor == null) {
                    olderPageMeta(latest, page, before)
                } else {
                    gapPageMeta(latest, page, floor)
                }
                putMeta(conversationId, updated)
                TimelineGaps.windowFloor(updated.gaps, timelineIds(conversationId)) != null || updated.hasOlder
            }
        } finally {
            loadingOlder.update { it - conversationId }
        }
    }

    /** Paging below the oldest cached message. G-06 stop rules: a short page, or an id that did not move back. */
    private fun olderPageMeta(meta: TimelineMeta, page: List<Message>, before: Long?): TimelineMeta {
        val newOldest = page.minOfOrNull { it.id }
        val movedBack = newOldest != null && (before == null || newOldest < before)
        val hasOlder = page.size >= ChatApi.MESSAGE_PAGE_SIZE && movedBack
        return meta.copy(hasOlder = hasOlder)
    }

    /** Paging into the hole below the shown segment: the page and the cursor message are contiguous. */
    private fun gapPageMeta(meta: TimelineMeta, page: List<Message>, floor: Long): TimelineMeta {
        val newOldest = page.minOfOrNull { it.id }
        return if (page.size < ChatApi.MESSAGE_PAGE_SIZE || newOldest == null || newOldest >= floor) {
            // Nothing older than the page exists, so every hole below the cursor is closed.
            TimelineMeta(hasOlder = false, gaps = TimelineGaps.cover(meta.gaps, Long.MIN_VALUE, floor))
        } else {
            meta.copy(gaps = TimelineGaps.cover(meta.gaps, newOldest, floor))
        }
    }

    /**
     * Loads the context page around [messageId], records any hole between it and the rest of
     * the cache, then pages forward from it until it joins the cached tail or reaches the newest
     * message (bounded; whatever is left is filled by [loadOlder] from the tail).
     */
    override suspend fun loadAround(conversationId: Long, messageId: Long): Result<Unit> = catchingWaivera {
        val page = apiCall {
            chatApi.getMessageContext(conversationId, messageId, ChatApi.CONTEXT_WINDOW, ChatApi.CONTEXT_WINDOW)
        }.map(Mappers::message)
        val ids = page.filter { !it.isThreadReply }.map { it.id }
        val lo = ids.minOrNull() ?: return@catchingWaivera
        val hi = ids.max()
        cache.transactions.run {
            val cachedIds = timelineIds(conversationId)
            ingest.ingest(page)
            val meta = meta(conversationId)
            val reachesStart = ids.count { it < messageId } < ChatApi.CONTEXT_WINDOW
            val reachesLatest = ids.count { it > messageId } < ChatApi.CONTEXT_WINDOW
            var gaps = TimelineGaps.cover(
                meta.gaps,
                if (reachesStart) Long.MIN_VALUE else lo,
                if (reachesLatest) Long.MAX_VALUE else hi,
            )
            if (!reachesLatest) {
                // Nothing cached above yet: the tail is unknown, so the hole is open-ended.
                val above = cachedIds.filter { it > hi }.minOrNull() ?: Long.MAX_VALUE
                gaps = TimelineGaps.add(gaps, TimelineGap(hi, above))
            }
            val below = cachedIds.filter { it < lo }.maxOrNull()
            if (!reachesStart && below != null) gaps = TimelineGaps.add(gaps, TimelineGap(below, lo))
            // With nothing cached below, the context page holds the oldest cached message.
            val hasOlder = if (below == null) !reachesStart else meta.hasOlder
            putMeta(conversationId, TimelineMeta(hasOlder = hasOlder, gaps = gaps))
        }
        fillForward(conversationId, hi)
    }

    private suspend fun fillForward(conversationId: Long, from: Long) {
        var edge = from
        repeat(MAX_FILL_PAGES) {
            if (TimelineGaps.gapAbove(meta(conversationId).gaps, edge) == null) return
            val page = apiCall {
                chatApi.getMessageContext(conversationId, edge, 0, ChatApi.MAX_CONTEXT_WINDOW)
            }.map(Mappers::message)
            val newer = page.filter { !it.isThreadReply && it.id > edge }.map { it.id }
            cache.transactions.run {
                ingest.ingest(page)
                val meta = meta(conversationId)
                val top = if (newer.size < ChatApi.MAX_CONTEXT_WINDOW) Long.MAX_VALUE else newer.max()
                putMeta(conversationId, meta.copy(gaps = TimelineGaps.cover(meta.gaps, edge, top)))
            }
            edge = newer.maxOrNull() ?: return
        }
    }

    private suspend fun meta(conversationId: Long): TimelineMeta =
        cache.document(metaKey(conversationId), TimelineMeta.serializer()) ?: TimelineMeta()

    private suspend fun putMeta(conversationId: Long, meta: TimelineMeta) =
        cache.putDocument(metaKey(conversationId), TimelineMeta.serializer(), meta)

    private suspend fun timelineIds(conversationId: Long): List<Long> =
        cache.observeMessages(conversationId).first().filter { !it.isThreadReply }.map { it.id }

    override suspend fun send(
        conversationId: Long,
        text: String,
        doc: JsonElement?,
        attachment: AttachmentSource?,
        parentMessageId: Long?,
    ): String {
        // A reply from a notification can arrive while a cold start is still bootstrapping (AF-07).
        val me = session.state.value.currentUserId
            ?: session.settledUserId(SESSION_WAIT_MILLIS)
            ?: throw WaiveraError.NotSignedIn()
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

    override suspend fun cachedThreadRoot(messageId: Long): Long? =
        cache.message(messageId)?.takeIf { it.isThreadReply }?.threadRootMessageId

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

    private companion object {
        /** Forward pages of up to 75 messages when joining a context page to the tail (600 messages). */
        const val MAX_FILL_PAGES = 8

        /** Inside the notification receiver's 8 s budget; the outbox sends once signed in. */
        const val SESSION_WAIT_MILLIS = 5_000L
    }

    private fun syncKey(conversationId: Long) = "messages:$conversationId"
}
