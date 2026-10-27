package com.waivera.waivera.core.data.sync

import com.waivera.waivera.core.data.live.ChatCache
import com.waivera.waivera.core.data.live.LiveActivityRepository
import com.waivera.waivera.core.data.live.LiveBookmarkRepository
import com.waivera.waivera.core.data.live.LiveConversationRepository
import com.waivera.waivera.core.data.live.LiveMessageRepository
import com.waivera.waivera.core.data.live.LiveThreadRepository
import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.data.live.MessageIngest
import com.waivera.waivera.core.data.live.TypingTracker
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.dto.ReactionAction
import com.waivera.waivera.core.network.log.WaiveraLog
import com.waivera.waivera.core.realtime.HubEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Applies hub events to the cache, and resyncs after reconnects. Hub events are hints layered on
 * REST state: anything missed while disconnected is recovered by [resync].
 */
@Singleton
@Suppress("LongParameterList")
class RealtimeSync @Inject constructor(
    private val cache: ChatCache,
    private val ingest: MessageIngest,
    private val conversations: LiveConversationRepository,
    private val messages: LiveMessageRepository,
    private val threads: LiveThreadRepository,
    private val bookmarks: LiveBookmarkRepository,
    private val activity: LiveActivityRepository,
    private val typing: TypingTracker,
    private val session: SessionRepository,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun apply(event: HubEvent) {
        try {
            applyOrThrow(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // One bad event must not stop the stream; REST resync repairs the cache.
            WaiveraLog.w(TAG, "Failed to apply ${event.javaClass.simpleName}", error)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun applyOrThrow(event: HubEvent) {
        when (event) {
            is HubEvent.MessageReceived -> {
                val message = Mappers.message(event.message)
                val isNew = cache.message(message.id) == null
                ingest.ingest(listOf(message))
                typing.onMessageFrom(message.conversationId, message.authorId)
                if (!conversations.onMessage(message)) conversations.hydrate(listOf(message.conversationId))
                val root = message.threadRootMessageId
                if (isNew && message.isThreadReply && root != null) threads.onNewReply(root)
            }
            // List-only update: the timeline cache is not touched so a stale cache cannot hide a gap.
            is HubEvent.ConversationListMessage -> {
                val message = Mappers.message(event.message)
                if (!conversations.onMessage(message)) conversations.hydrate(listOf(message.conversationId))
            }
            is HubEvent.ChatAttention -> Unit
            is HubEvent.ChatActivity -> activity.onActivity(Mappers.activity(event.activity))
            is HubEvent.ChatActivityRemoved -> activity.onRemoved(event.activityId)
            is HubEvent.ChatReadState -> {
                conversations.onReadState(event.state)
                activity.markLocally(event.state.readActivityIds)
            }
            is HubEvent.TypingStatus ->
                typing.onRemoteTyping(
                    event.conversationId,
                    event.userId,
                    event.isTyping,
                    session.state.value.currentUserId,
                )
            is HubEvent.ReadReceipt -> Unit
            is HubEvent.Reaction -> messages.applyReaction(
                event.messageId,
                event.userId,
                event.reactionType.takeUnless { event.action == ReactionAction.REMOVED },
            )
            is HubEvent.BookmarkUpdate -> {
                val id = event.update.messageId
                val flag = event.update.isBookmarked
                if (id != null && flag != null) bookmarks.apply(id, flag) else bookmarks.refresh()
            }
            is HubEvent.MessageUpdated -> {
                val update = event.update
                cache.updateMessage(update.messageId) {
                    it.copy(
                        text = update.newContent ?: update.messagePlaintext ?: it.text,
                        doc = update.messageDoc ?: it.doc,
                        contentFormat = update.contentFormat,
                        isEdited = true,
                        updatedAt = update.updateDate ?: it.updatedAt,
                    )
                }
                cache.message(update.messageId)?.let { conversations.onMessageChanged(it) }
            }
            is HubEvent.MessageDeleted -> messages.markDeleted(event.delete.messageId)
            is HubEvent.ConversationCreated -> conversations.onConversation(Mappers.conversation(event.conversation))
            is HubEvent.CallStateChanged -> conversations.onCallState(event.state)
            is HubEvent.AdHocRemoved -> conversations.onAdHocRemoved(event.removal.conversationId)
            // G-07: notification feeds target the web app; employees get chat pushes only.
            is HubEvent.Notification -> Unit
        }
    }

    /** After a reconnect or resume: inbox, every open timeline's tail, open threads, activity. */
    suspend fun resync() = coroutineScope {
        val jobs = buildList {
            add(async { conversations.refresh() })
            messages.activeConversations.forEach { id -> add(async { messages.refreshLatest(id) }) }
            threads.activeRoots.forEach { root -> add(async { threads.refresh(root) }) }
            add(async { activity.refresh() })
        }
        jobs.awaitAll()
        Unit
    }

    private companion object {
        const val TAG = "RealtimeSync"
    }
}
