package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.outbox.OutboxStore
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.api.VideoApi
import com.waivera.waivera.core.network.dto.ChatReadStateDto
import com.waivera.waivera.core.network.dto.ChatReadThroughRequest
import com.waivera.waivera.core.network.dto.ConversationCallStateDto
import com.waivera.waivera.core.network.dto.CreateConversationRequest
import com.waivera.waivera.core.network.dto.CreateMeetingRoomRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import com.waivera.waivera.core.network.log.WaiveraLog
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
@Suppress("TooManyFunctions", "LongParameterList")
class LiveConversationRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val videoApi: VideoApi,
    private val config: NetworkConfig,
    private val cache: ChatCache,
    private val sync: SyncTracker,
    private val session: SessionRepository,
    private val typing: TypingTracker,
    private val visible: VisibleConversation,
    private val clock: Clock,
    private val outbox: OutboxStore,
    private val reads: ConversationReadListener,
) : ConversationRepository {
    private val myUserId: String? get() = session.state.value.currentUserId

    override fun observeConversations(): Flow<Synced<List<Conversation>>> =
        sync.synced(KEY, cache.observeConversations())

    override fun observeConversation(conversationId: Long): Flow<Conversation?> =
        cache.observeConversation(conversationId)

    override fun observeUnreadTotal(): Flow<Int> = cache.observeConversations()
        .map { list -> list.sumOf { if (it.isUnreadFor(myUserId)) it.unreadCount else 0 } }
        .distinctUntilChanged()

    override fun observeFailedSends(): Flow<Set<Long>> = outbox.observeAll()
        .map { items -> items.filter { it.state == SendState.FAILED }.mapTo(mutableSetOf()) { it.conversationId } }
        .distinctUntilChanged()

    /**
     * G-05: `/my-conversations` (author, meetings) and `/all` (channels, attachments) are fetched
     * together and merged per conversation; `/all` failing is tolerated. G-13: both parameters
     * are always sent explicitly.
     */
    override suspend fun refresh(): Result<Unit> = sync.track(KEY) {
        val (mine, all) = coroutineScope {
            val mine = async { apiCall { chatApi.getMyConversations(includeMeetings = true) } }
            val all = async {
                catchingWaivera {
                    apiCall {
                        chatApi.getAllConversations(includeMeetings = true, includeArchived = false)
                    }.requireData()
                }.onFailure {
                    WaiveraLog.w(TAG, "/all failed; inbox built from /my-conversations only", it)
                }.getOrNull()
            }
            mine.await() to all.await()
        }
        val mineById = mine.map(Mappers::conversation).associateBy { it.id }
        val allById = all.orEmpty().map(Mappers::conversation).associateBy { it.id }
        val merged = (mineById.keys + allById.keys).mapNotNull { id ->
            ConversationMerge.combine(mineById[id], allById[id])
        }
        cache.transactions.run {
            val cached = cache.conversations().associateBy { it.id }
            val fresh = merged.map { ConversationMerge.refresh(cached[it.id], it) }
            cache.putConversations(fresh)
            val gone = cached.keys - fresh.map { it.id }.toSet()
            if (gone.isNotEmpty()) cache.conversationDao.delete(gone.toList())
        }
        cache.putPeople(merged.flatMap { it.participants }.distinctBy { it.id })
    }

    override suspend fun refreshConversation(conversationId: Long): Result<Conversation> = catchingWaivera {
        val fresh = Mappers.conversation(apiCall { chatApi.getConversation(conversationId) })
        if (fresh.id <= 0) throw WaiveraError.NotFound()
        upsertKeepingUnread(fresh)
        cache.conversation(conversationId) ?: fresh
    }

    /** Fetches conversations the cache has never seen (hub events for new chats), six at a time. */
    suspend fun hydrate(ids: Collection<Long>) {
        val missing = ids.filter { cache.conversation(it) == null }
        missing.chunked(HYDRATE_BATCH).forEach { batch ->
            coroutineScope { batch.map { id -> async { refreshConversation(id) } }.awaitAll() }
        }
    }

    /**
     * A conversation first seen through a hub message. `/conversation/{id}` never fills
     * `unreadCount` (G-14), so someone else's top-level message counts as at least one unread
     * unless that conversation is on screen (AF-09).
     */
    suspend fun hydrateFor(message: Message) {
        hydrate(listOf(message.conversationId))
        if (message.isThreadReply || message.isMine(myUserId)) return
        cache.updateConversation(message.conversationId) { c ->
            if (visible.id.value == c.id || c.unreadCount > 0) null else c.copy(unreadCount = 1)
        }
    }

    private suspend fun upsertKeepingUnread(fresh: Conversation) {
        cache.transactions.run {
            val cached = cache.conversation(fresh.id)
            // G-14: `/conversation/{id}` never fills unreadCount; keep the list's value.
            cache.putConversations(listOf(ConversationMerge.refresh(cached, fresh, keepUnread = cached != null)))
        }
        cache.putPeople(fresh.participants)
    }

    /** Exact participant-set reuse, then create, then a re-fetch when create fails. */
    override suspend fun openConversation(participantIds: List<String>): Result<Long> = catchingWaivera {
        val me = myUserId ?: throw WaiveraError.NotSignedIn()
        val wanted = (participantIds + me).toSet()
        findExisting(wanted)?.let { return@catchingWaivera it }
        val created = catchingWaivera {
            apiCall {
                chatApi.createConversation(CreateConversationRequest(participantIds.distinct().filter { it != me }))
            }
                .requireData()
        }
        created.getOrNull()?.let { dto ->
            val conversation = Mappers.conversation(dto)
            upsertKeepingUnread(conversation)
            return@catchingWaivera conversation.id
        }
        refresh()
        findExisting(wanted) ?: throw WaiveraError.from(created.exceptionOrNull() ?: WaiveraError.Unexpected())
    }

    private suspend fun findExisting(participants: Set<String>): Long? = cache.conversations().firstOrNull { c ->
        c.kind != ConversationKind.CHANNEL &&
            c.kind != ConversationKind.BOT &&
            c.participants.map { it.id }.toSet() == participants
    }?.id

    override suspend fun conversationForEvent(calendarEventId: String): Result<Long> = catchingWaivera {
        val conversation = Mappers.conversation(
            apiCall {
                chatApi.getOrCreateConversationForEvent(calendarEventId)
            }.requireData(),
        )
        upsertKeepingUnread(conversation)
        conversation.id
    }

    override suspend fun leave(conversationId: Long): Result<Unit> = catchingWaivera {
        apiCall { chatApi.leaveConversation(conversationId) }
        cache.conversationDao.delete(listOf(conversationId))
        cache.messageDao.deleteConversation(conversationId)
    }

    /** Optimistic; on failure the badge and read position come back so Retry has something to do (AF-08). */
    override suspend fun markRead(conversationId: Long, lastVisibleMessageId: Long): Result<Unit> = catchingWaivera {
        val now = clock.instant()
        val before = cache.conversation(conversationId)
        cache.updateConversation(conversationId) { it.copy(unreadCount = 0, lastReadAt = now) }
        try {
            apiCall { chatApi.readThrough(conversationId, ChatReadThroughRequest(lastVisibleMessageId)) }
        } catch (error: WaiveraError) {
            if (before != null) {
                // Only undo our own change: a read state that arrived meanwhile stays.
                cache.updateConversation(conversationId) { c ->
                    if (c.unreadCount == 0 && c.lastReadAt == now) {
                        c.copy(unreadCount = before.unreadCount, lastReadAt = before.lastReadAt)
                    } else {
                        null
                    }
                }
            }
            throw error
        }
        reads.onRead(conversationId)
    }

    override fun observeTyping(conversationId: Long): Flow<Set<String>> = typing.observe(conversationId)

    override fun observeAllTyping(): Flow<Map<Long, Set<String>>> = typing.observeAll()

    /**
     * 03 §19: join the live room (`ongoingCall`, then `activeMeeting`) without publishing, or
     * create one through `createmeetingroom`, which reuses a room the server knows about. That
     * also covers a cached conversation that says a call is live but carries no room id.
     */
    override suspend fun callLobbyUrl(conversationId: Long): Result<String> = catchingWaivera {
        val web = config.webBase ?: throw WaiveraError.Configuration(listOf("waivera.webBaseUrl"))
        val conversation = cache.conversation(conversationId) ?: refreshConversation(conversationId).getOrThrow()
        // 03 §19: the ongoing call first, then the meeting; only a meeting carries an event id.
        val callRoom = conversation.ongoingCall?.takeIf { it.isLive }?.roomId?.takeIf(String::isNotBlank)
        val meetingRoom = conversation.activeMeeting?.roomId?.takeIf(String::isNotBlank)
        val liveRoom = callRoom ?: meetingRoom
        val state = MeetingHandoff.State(
            calenderEventId = if (callRoom == null) conversation.activeMeeting?.calendarEventId else null,
            relatedChatConversationId = conversationId.toString(),
            isPublisher = false,
            isOneToOneCall = conversation.participants.size == 2,
        )
        if (liveRoom != null) return@catchingWaivera MeetingHandoff.lobbyUrl(web, liveRoom, state)
        val meeting = apiCall {
            videoApi.createMeetingRoom(
                CreateMeetingRoomRequest(calendarEventId = null, conversationId = conversationId),
            )
        }.requireData()
        val roomId = meeting.roomId?.takeIf(String::isNotBlank) ?: throw WaiveraError.Unexpected()
        MeetingHandoff.lobbyUrl(
            web,
            roomId,
            state.copy(calenderEventId = meeting.calendarEventId, isPublisher = meeting.isPublisher),
        )
    }

    override fun onComposerChanged(conversationId: Long, isEmpty: Boolean) =
        typing.onComposerChanged(conversationId, isEmpty)

    // Event application, called by RealtimeSync.

    /**
     * A new message updates the preview and, for someone else's top-level message in a
     * conversation that is not on screen, the badge. Both `ReceiveMessage` and
     * `ReceiveConversationListMessage` can deliver the same message, so the badge only moves
     * when the preview actually advances.
     */
    @Suppress("ReturnCount")
    suspend fun onMessage(message: Message): Boolean {
        if (cache.conversation(message.conversationId) == null) return false
        if (message.isThreadReply) return true
        val mine = message.isMine(myUserId)
        cache.updateConversation(message.conversationId) { c ->
            val advances = (c.lastMessage?.messageId ?: 0) < message.id
            val preview = ConversationMerge.mergePreview(c.lastMessage, Mappers.preview(message))
            val bump = advances && !mine && visible.id.value != c.id
            c.copy(lastMessage = preview, unreadCount = if (bump) c.unreadCount + 1 else c.unreadCount)
        }
        return true
    }

    suspend fun onConversation(conversation: Conversation) = upsertKeepingUnread(conversation)

    suspend fun onReadState(state: ChatReadStateDto) {
        val readAt = state.readAt ?: clock.instant()
        if (state.conversationId == 0L) {
            val cleared = cache.transactions.run {
                val unread = cache.conversations().filter { it.unreadCount > 0 }
                cache.putConversations(unread.map { it.copy(unreadCount = 0, lastReadAt = readAt) })
                unread.map { it.id }
            }
            cleared.forEach(reads::onRead)
        } else if (state.threadRootMessageId == null) {
            cache.updateConversation(state.conversationId) { it.copy(unreadCount = 0, lastReadAt = readAt) }
            reads.onRead(state.conversationId)
        }
    }

    suspend fun onCallState(state: ConversationCallStateDto) {
        cache.updateConversation(state.conversationId) { c ->
            c.copy(
                ongoingCall = if (state.status.isLive) {
                    com.waivera.waivera.core.data.model.OngoingCall(
                        state.roomId,
                        state.startTime,
                        state.status,
                        c.ongoingCall?.callRecordId,
                    )
                } else {
                    null
                },
            )
        }
    }

    suspend fun onAdHocRemoved(conversationId: Long) {
        cache.updateConversation(conversationId) { it.copy(activeMeeting = null, ongoingCall = null) }
    }

    /** Message edits and deletes keep the inbox preview in step. */
    suspend fun onMessageChanged(message: Message) {
        cache.updateConversation(message.conversationId) { c ->
            if (c.lastMessage?.messageId != message.id) return@updateConversation null
            c.copy(
                lastMessage = Mappers.preview(message).copy(
                    authorName =
                    message.author?.name ?: c.lastMessage?.authorName,
                ),
            )
        }
    }

    fun isSignedIn(): Boolean = session.state.value is SessionState.SignedIn

    companion object {
        const val KEY = "conversations"
        private const val TAG = "Conversations"
        private const val HYDRATE_BATCH = 6
    }
}
