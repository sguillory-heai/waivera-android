package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.live.MeetingHandoff
import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.AttachmentSource
import com.waivera.waivera.core.data.model.Bookmark
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessageThread
import com.waivera.waivera.core.data.model.MessageTimeline
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.DurableMessageSender
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement

/** Demo inbox. Messages typed into the composer also drive the demo typing indicator. */
@Singleton
@Suppress("TooManyFunctions")
class DemoConversationRepository @Inject constructor(private val backend: DemoBackend) : ConversationRepository {
    override fun observeConversations(): Flow<Synced<List<Conversation>>> = backend.synced(
        backend.conversations.map { all -> all.values.sortedByDescending { it.activityAt } },
    )

    override fun observeConversation(conversationId: Long): Flow<Conversation?> =
        backend.conversations.map { it[conversationId] }.distinctUntilChanged()

    override fun observeUnreadTotal(): Flow<Int> = backend.conversations.map { all ->
        all.values.sumOf { if (it.isUnreadFor(backend.myUserId)) it.unreadCount else 0 }
    }

    override fun observeFailedSends(): Flow<Set<Long>> = backend.messages.map { all ->
        all.filterValues { list -> list.any { it.sendState == SendState.FAILED } }.keys
    }.distinctUntilChanged()

    override suspend fun refresh(): Result<Unit> = backend.refresh()

    override suspend fun refreshConversation(conversationId: Long): Result<Conversation> =
        backend.refresh().mapCatching { backend.conversation(conversationId) ?: throw WaiveraError.NotFound() }

    @Suppress("ReturnCount")
    override suspend fun openConversation(participantIds: List<String>): Result<Long> {
        val me = backend.myUserId ?: return Result.failure(WaiveraError.NotSignedIn())
        val wanted = (participantIds + me).toSet()
        backend.conversations.value.values.firstOrNull { c ->
            c.kind != ConversationKind.CHANNEL && c.participants.map { it.id }.toSet() == wanted
        }?.let { return Result.success(it.id) }
        val id = backend.nextMessageId()
        val people = backend.people.value.filter { it.id in wanted } + listOfNotNull(backend.me())
        val conversation = Conversation(
            id,
            if (wanted.size >
                2
            ) {
                ConversationKind.GROUP
            } else {
                ConversationKind.DIRECT
            },
            people,
        )
        backend.conversations.update { it + (id to conversation) }
        return Result.success(id)
    }

    override suspend fun conversationForEvent(calendarEventId: String): Result<Long> = Result.success(DemoData.GROUP)

    override suspend fun leave(conversationId: Long): Result<Unit> {
        backend.conversations.update { it - conversationId }
        return Result.success(Unit)
    }

    override suspend fun markRead(conversationId: Long, lastVisibleMessageId: Long): Result<Unit> {
        backend.conversations.update { all ->
            all[conversationId]?.let { all + (conversationId to it.copy(unreadCount = 0, lastReadAt = backend.now())) }
                ?: all
        }
        return Result.success(Unit)
    }

    override fun observeTyping(conversationId: Long): Flow<Set<String>> = backend.typing.map {
        it[conversationId].orEmpty()
    }

    override fun observeAllTyping(): Flow<Map<Long, Set<String>>> =
        backend.typing.map { all -> all.filterValues { it.isNotEmpty() } }

    override fun onComposerChanged(conversationId: Long, isEmpty: Boolean) = Unit

    /** Same rules as the live repository, with a made-up room when none is live. */
    override suspend fun callLobbyUrl(conversationId: Long): Result<String> {
        val web = backend.webBase
            ?: return Result.failure(WaiveraError.Configuration(listOf("waivera.webBaseUrl")))
        val conversation = backend.conversation(conversationId) ?: return Result.failure(WaiveraError.NotFound())
        val liveRoom = conversation.ongoingCall?.takeIf { it.isLive }?.roomId?.takeIf(String::isNotBlank)
            ?: conversation.activeMeeting?.roomId?.takeIf(String::isNotBlank)
        val state = MeetingHandoff.State(
            calenderEventId = conversation.activeMeeting?.calendarEventId,
            relatedChatConversationId = conversationId.toString(),
            isPublisher = liveRoom == null,
            isOneToOneCall = conversation.participants.size == 2,
        )
        return Result.success(MeetingHandoff.lobbyUrl(web, liveRoom ?: "room_demo_$conversationId", state))
    }
}

/**
 * Demo timelines. Sends succeed at once, except a message containing "fail", which shows the
 * failed state so Retry can be tried; retrying it succeeds.
 */
@Singleton
class DemoMessageRepository @Inject constructor(private val backend: DemoBackend) :
    MessageRepository,
    DurableMessageSender {
    override suspend fun sendDurably(conversationId: Long, text: String, parentMessageId: Long?): String =
        send(conversationId = conversationId, text = text, parentMessageId = parentMessageId)

    override fun observeTimeline(conversationId: Long): Flow<Synced<MessageTimeline>> = backend.synced(
        backend.messages.map { all ->
            MessageTimeline(all[conversationId].orEmpty().sortedWith(ORDER), hasOlder = false)
        },
    )

    override suspend fun refreshLatest(conversationId: Long): Result<Unit> = backend.refresh()

    override suspend fun loadOlder(conversationId: Long): Result<Boolean> = backend.refresh().map { false }

    override suspend fun loadAround(conversationId: Long, messageId: Long): Result<Unit> = backend.refresh()

    override suspend fun send(
        conversationId: Long,
        text: String,
        doc: JsonElement?,
        attachment: AttachmentSource?,
        parentMessageId: Long?,
    ): String {
        val clientId = UUID.randomUUID().toString()
        val failed = text.contains(FAIL_WORD, ignoreCase = true)
        backend.append(
            Message(
                id = if (failed) -backend.nextMessageId() else backend.nextMessageId(),
                conversationId = conversationId,
                clientMessageId = clientId,
                author = backend.me(),
                authorId = backend.myUserId,
                text = text,
                doc = doc,
                createdAt = backend.now(),
                attachment = attachment?.let {
                    Attachment(
                        url = "demo://${it.fileName}",
                        originalName = it.fileName,
                        contentType = it.contentType,
                        size = it.size,
                    )
                },
                parentMessageId = parentMessageId,
                threadRootMessageId = parentMessageId,
                sendState = if (failed) SendState.FAILED else SendState.SENT,
            ),
        )
        return clientId
    }

    override suspend fun retry(clientMessageId: String): Result<Unit> {
        val pending = backend.messages.value.values.flatten().firstOrNull { it.clientMessageId == clientMessageId }
            ?: return Result.failure(WaiveraError.NotFound())
        discard(clientMessageId)
        backend.append(
            pending.copy(id = backend.nextMessageId(), sendState = SendState.SENT, createdAt = backend.now()),
        )
        return Result.success(Unit)
    }

    override suspend fun discard(clientMessageId: String) {
        backend.messages.update { all ->
            all.mapValues { (_, list) ->
                list.filter {
                    it.clientMessageId !=
                        clientMessageId ||
                        !it.isPending
                }
            }
        }
    }

    override suspend fun edit(messageId: Long, text: String, doc: JsonElement?): Result<Message> {
        backend.updateMessage(messageId) { it.copy(text = text, doc = doc, isEdited = true, updatedAt = backend.now()) }
        return backend.message(messageId)?.let { Result.success(it) } ?: Result.failure(WaiveraError.NotFound())
    }

    override suspend fun delete(messageId: Long): Result<Unit> {
        backend.updateMessage(messageId) {
            it.copy(isDeleted = true, text = null, doc = null, attachment = null, reactions = emptyList())
        }
        return Result.success(Unit)
    }

    override suspend fun react(messageId: Long, emoji: String): Result<Unit> {
        val me = backend.myUserId ?: return Result.failure(WaiveraError.NotSignedIn())
        backend.updateMessage(messageId) { m ->
            val existing = m.reactions.firstOrNull { it.userId == me }
            val others = m.reactions.filter { it.userId != me }
            m.copy(reactions = if (existing?.emoji == emoji) others else others + Reaction(me, emoji))
        }
        return Result.success(Unit)
    }

    private companion object {
        const val FAIL_WORD = "fail"
        val ORDER = compareBy<Message>({ it.isPending }, { it.createdAt }, { it.id })
    }
}

@Singleton
class DemoThreadRepository @Inject constructor(private val backend: DemoBackend) : ThreadRepository {
    override fun observeThread(conversationId: Long, rootMessageId: Long): Flow<Synced<MessageThread>> = backend.synced(
        backend.messages.map { all ->
            val list = all[conversationId].orEmpty()
            MessageThread(
                root = list.firstOrNull { it.id == rootMessageId },
                replies = list.filter {
                    it.threadRootMessageId == rootMessageId && it.id != rootMessageId
                }.sortedBy { it.createdAt },
            )
        },
    )

    override suspend fun refresh(rootMessageId: Long): Result<Unit> = backend.refresh()

    override suspend fun markRead(conversationId: Long, rootMessageId: Long, lastVisibleMessageId: Long): Result<Unit> =
        Result.success(Unit)
}

@Singleton
class DemoBookmarkRepository @Inject constructor(private val backend: DemoBackend) : BookmarkRepository {
    override fun observeBookmarks(): Flow<Synced<List<Bookmark>>> = backend.synced(
        combine(backend.bookmarks, backend.messages) { ids, all ->
            all.values.flatten().filter {
                it.id in ids
            }.sortedByDescending { it.createdAt }.map { Bookmark(it.copy(isBookmarked = true)) }
        },
    )

    override fun observeBookmarkedIds(): Flow<Set<Long>> = backend.bookmarks

    override suspend fun refresh(): Result<Unit> = backend.refresh()

    override suspend fun toggle(messageId: Long): Result<Boolean> {
        val now = messageId !in backend.bookmarks.value
        backend.bookmarks.update { if (now) it + messageId else it - messageId }
        backend.updateMessage(messageId) { it.copy(isBookmarked = now) }
        return Result.success(now)
    }
}

/** Demo attachments never touch the network; downloads produce a small placeholder file. */
@Singleton
class DemoAttachmentRepository @Inject constructor() : AttachmentRepository {
    override suspend fun download(messageId: Long, fileName: String?): Result<File> = runCatching {
        File.createTempFile("demo-$messageId-", "-${fileName ?: "attachment"}".replace('/', '_')).apply {
            writeText("Demo attachment")
            deleteOnExit()
        }
    }

    override fun resolve(attachmentUrl: String): String? = null
}
