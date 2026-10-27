package com.waivera.waivera.feature.chat

import com.waivera.waivera.core.data.model.AttachmentSource
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.MessageThread
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.chat.message.AttachmentIo
import com.waivera.waivera.feature.chat.message.ChatClock
import com.waivera.waivera.feature.chat.message.ConversationCallLobby
import com.waivera.waivera.feature.chat.message.StagedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/** Content-resolver stand-in: every picked uri is a small PNG unless listed as unreadable. */
class FakeAttachmentIo(private val unreadable: Set<String> = emptySet()) : AttachmentIo {
    val saved = mutableListOf<Pair<File, String>>()

    override suspend fun describe(uri: String): StagedImage? =
        if (uri in unreadable) null else StagedImage(uri, "photo.png", "image/png", PNG_SIZE)

    override fun source(image: StagedImage): AttachmentSource =
        AttachmentSource(image.fileName ?: "photo.png", image.contentType, image.size) { ByteArrayInputStream(ByteArray(0)) }

    override suspend fun saveTo(file: File, destinationUri: String): Result<Unit> {
        saved += file to destinationUri
        return Result.success(Unit)
    }

    override suspend fun shareableUri(file: File, fileName: String): Result<String> = Result.success("content://test/$fileName")

    private companion object {
        const val PNG_SIZE = 1_024L
    }
}

class FakeCallLobby(private val result: Result<String> = Result.success("https://app.example.test/lobby/11")) : ConversationCallLobby {
    var calls = 0

    override suspend fun lobbyUrl(conversation: Conversation): Result<String> {
        calls++
        return result
    }
}

/** Records read-through calls; fails while [failing] is set. */
class RecordingConversations(private val delegate: ConversationRepository) : ConversationRepository by delegate {
    val markReadCalls = mutableListOf<Long>()
    val composerEvents = mutableListOf<Boolean>()
    var failing = false

    override suspend fun markRead(conversationId: Long, lastVisibleMessageId: Long): Result<Unit> {
        markReadCalls += lastVisibleMessageId
        return if (failing) Result.failure(WaiveraError.Network()) else delegate.markRead(conversationId, lastVisibleMessageId)
    }

    override fun onComposerChanged(conversationId: Long, isEmpty: Boolean) {
        composerEvents += isEmpty
        delegate.onComposerChanged(conversationId, isEmpty)
    }
}

class RecordingThreads(private val delegate: ThreadRepository) : ThreadRepository by delegate {
    val markReadCalls = mutableListOf<Pair<Long, Long>>()
    var observed = 0
    var failing = false

    /** Collecting the thread is what joins the conversation's hub group (data-layer contract). */
    override fun observeThread(conversationId: Long, rootMessageId: Long): Flow<Synced<MessageThread>> {
        observed++
        return delegate.observeThread(conversationId, rootMessageId)
    }

    override suspend fun markRead(conversationId: Long, rootMessageId: Long, lastVisibleMessageId: Long): Result<Unit> {
        markReadCalls += rootMessageId to lastVisibleMessageId
        return if (failing) Result.failure(WaiveraError.Network()) else delegate.markRead(conversationId, rootMessageId, lastVisibleMessageId)
    }
}

/** A message layer whose sends throw, as a refused hub send would. */
class ThrowingSends(private val delegate: MessageRepository, private val error: WaiveraError) : MessageRepository by delegate {
    override suspend fun send(
        conversationId: Long,
        text: String,
        doc: JsonElement?,
        attachment: AttachmentSource?,
        parentMessageId: Long?,
    ): String = throw error
}

/** Chat clocks pinned to the demo "now" and the demo employee zone. */
fun testChatClock(): ChatClock = ChatClock(Clock.fixed(DemoData.NOW, ZoneOffset.UTC)) { DemoData.ZONE }
