package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.MessageThread
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.outbox.OutboxStore
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.dto.ChatReadThroughRequest
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.realtime.RealtimeClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Threads (04-threads.md) with the same actions as the main timeline: replies are sent through
 * [com.waivera.waivera.core.data.repository.MessageRepository.send] with `parentMessageId`, and
 * reactions, edits and deletes use the message repository too. Replies arrive on the parent
 * conversation's hub group, which collecting a thread joins.
 */
@Singleton
class LiveThreadRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val cache: ChatCache,
    private val sync: SyncTracker,
    private val ingest: MessageIngest,
    private val outbox: OutboxStore,
    private val realtime: RealtimeClient,
    private val session: SessionRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : ThreadRepository {
    private val active = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** Thread roots on screen, refetched after reconnects. */
    val activeRoots: Set<Long> get() = active.value.keys

    override fun observeThread(conversationId: Long, rootMessageId: Long): Flow<Synced<MessageThread>> = flow {
        val lease = realtime.attendConversation(conversationId)
        active.update { it + (rootMessageId to (it[rootMessageId] ?: 0) + 1) }
        scope.launch { refresh(rootMessageId) }
        try {
            val merged = combine(cache.observeThread(rootMessageId), outbox.observeConversation(conversationId)) { messages, pending ->
                val me = session.state.value.currentUser?.let {
                    com.waivera.waivera.core.data.model.Person(it.userId, it.firstName, it.lastName, it.displayName, it.email, it.avatarUrl)
                }
                val confirmed = messages.mapNotNull { it.clientMessageId }.toSet()
                val optimistic = pending.filter { it.parentMessageId == rootMessageId && it.clientMessageId !in confirmed }
                    .map { it.toMessage(me) }
                MessageThread(
                    root = messages.firstOrNull { it.id == rootMessageId },
                    replies = messages.filter { it.id != rootMessageId } + optimistic,
                )
            }
            emitAll(sync.synced(key(rootMessageId), merged))
        } finally {
            lease.close()
            active.update { current ->
                val count = (current[rootMessageId] ?: 1) - 1
                if (count <= 0) current - rootMessageId else current + (rootMessageId to count)
            }
        }
    }

    override suspend fun refresh(rootMessageId: Long): Result<Unit> = sync.track(key(rootMessageId)) {
        val messages = apiCall { chatApi.getThread(rootMessageId) }.map(Mappers::message)
        val replies = messages.count { it.id != rootMessageId }
        ingest.ingest(messages)
        cache.updateMessage(rootMessageId) { root -> root.copy(threadReplyCount = maxOf(root.threadReplyCount, replies)) }
    }

    override suspend fun markRead(conversationId: Long, rootMessageId: Long, lastVisibleMessageId: Long): Result<Unit> =
        catchingWaivera {
            apiCall { chatApi.readThrough(conversationId, ChatReadThroughRequest(lastVisibleMessageId, rootMessageId)) }
            Unit
        }

    /** A reply arrived: bump the root's count so the main timeline's "N replies" stays current. */
    suspend fun onNewReply(rootMessageId: Long) {
        cache.updateMessage(rootMessageId) { it.copy(threadReplyCount = it.threadReplyCount + 1) }
    }

    private fun key(rootMessageId: Long) = "thread:$rootMessageId"
}
