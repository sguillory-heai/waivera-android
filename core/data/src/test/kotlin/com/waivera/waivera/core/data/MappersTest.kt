package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.ConversationMerge
import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.decodeNormalized
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.UnifiedConversationResponse
import com.waivera.waivera.core.testing.Fixtures
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer

class MappersTest {
    private fun messages(name: String) = WaiveraJson.decodeNormalized(
        ListSerializer(MessageSendResponse.serializer()),
        Fixtures.text(name),
    ).map(Mappers::message)

    @Test
    fun everyPaginationFixtureMaps() {
        Fixtures.names("rest-chat-get-messages-pagination").forEach { name ->
            messages(name).forEach { assertTrue(it.id > 0 && it.conversationId > 0, name) }
        }
    }

    @Test
    fun deletedMessagesDropTheServerBody() {
        val deleted = messages("rest-chat-get-messages-pagination-deleted.json").single()
        assertTrue(deleted.isDeleted)
        assertNull(deleted.text, "G-11: never show the server's English placeholder or the original text")
        assertNull(deleted.attachment)
    }

    @Test
    fun repliesPointAtTheirThreadRoot() {
        val thread = messages("rest-chat-get-thread.json")
        assertFalse(thread.first().isThreadReply)
        assertEquals(9330L, thread.first().navigationRootId)
        thread.drop(1).forEach {
            assertTrue(it.isThreadReply)
            assertEquals(9330L, it.threadRootMessageId)
        }
    }

    @Test
    fun attachmentsAndReactionsMap() {
        val withAttachment = messages("rest-chat-get-messages-pagination-with-attachment.json").first {
            it.attachment !=
                null
        }
        assertNotNull(withAttachment.attachment?.url)
        val withReaction = messages("rest-chat-get-messages-pagination-with-reaction.json").first {
            it.reactions.isNotEmpty()
        }
        assertTrue(withReaction.reactions.all { it.userId.isNotBlank() && it.emoji.isNotBlank() })
    }

    @Test
    fun zonelessTimestampsAreUtc() {
        val first = messages("rest-chat-get-messages-pagination.json").first()
        assertEquals(Instant.parse("2026-10-20T13:02:44.120055Z"), first.createdAt)
    }

    @Test
    fun conversationsFromBothListEndpointsMergePerG05() {
        val mine = WaiveraJson.decodeNormalized(
            ListSerializer(ConversationResponse.serializer()),
            Fixtures.text("rest-chat-get-my-conversations.json"),
        ).map(Mappers::conversation).associateBy { it.id }
        val all = WaiveraJson.decodeNormalized(
            ApiResponse.serializer(ListSerializer(UnifiedConversationResponse.serializer())),
            Fixtures.text("rest-chat-get-all.json"),
        ).data.orEmpty().map(Mappers::conversation).associateBy { it.id }

        val seven = assertNotNull(ConversationMerge.combine(mine[7], all[7]))
        assertEquals("Alex Rivera", seven.lastMessage?.authorName, "author comes from /my-conversations")
        assertEquals(ConversationKind.GROUP, mine.getValue(11).kind)

        val channel = assertNotNull(ConversationMerge.combine(null, all[41]))
        assertEquals(ConversationKind.CHANNEL, channel.kind)
        assertTrue(channel.lastMessage?.hasAttachment == true, "attachment survives from /all")
        assertNotNull(channel.lastMessage?.authorName, "author resolved from participants when /all has none")
        assertNull(ConversationMerge.combine(null, all[58])?.lastMessage)
    }

    @Test
    fun mergeNeverReplacesRicherPreviewFieldsWithNull() {
        val rich =
            MessagePreview(
                9301,
                authorId = "u1",
                authorName = "Priya",
                text = null,
                attachmentName = "plan.pdf",
                hasAttachment = true,
            )
        val poor = MessagePreview(9301, authorId = "u1", authorName = null, text = null)
        val merged = assertNotNull(ConversationMerge.mergePreview(rich, poor))
        assertEquals("Priya", merged.authorName)
        assertEquals("plan.pdf", merged.attachmentName)
        assertTrue(merged.hasAttachment)

        val newer = MessagePreview(9302, text = "next")
        assertEquals(newer, ConversationMerge.mergePreview(rich, newer))
        assertEquals(rich, ConversationMerge.mergePreview(rich, MessagePreview(9200)))
    }

    @Test
    fun singleConversationRefreshKeepsTheListUnreadCount() {
        val listed = Mappers.conversation(ConversationResponse(id = 7, unreadCount = 4))
        val single = Mappers.conversation(ConversationResponse(id = 7, unreadCount = 0))
        assertEquals(4, ConversationMerge.refresh(listed, single, keepUnread = true).unreadCount)
        assertEquals(0, ConversationMerge.refresh(listed, single).unreadCount)
    }
}
