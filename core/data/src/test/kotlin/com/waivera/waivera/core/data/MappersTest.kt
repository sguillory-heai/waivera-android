package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.ConversationMerge
import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.data.live.MeetingHandoff
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.MeetingInfo
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.data.model.OngoingCall
import com.waivera.waivera.core.data.model.SendFailure
import com.waivera.waivera.core.data.model.TicketUpdate
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.decodeNormalized
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.UnifiedConversationResponse
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.Fixtures
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    private val ticketJson = buildJsonObject {
        put("ticketId", 4182)
        put("title", "Laptop will not boot")
        put("url", "https://tickets.example.test/4182")
        put("priority", "High")
        put("dueDate", "2026-10-30")
    }

    @Test
    fun previewsCarryBotFormatAndTicketFields() {
        val dto = MessageSendResponse(
            id = 9500,
            userId = "bot:helpdesk",
            conversationId = 7,
            messageContent = "{\"type\":\"doc\"}",
            messagePlaintext = "Ticket updated",
            contentFormat = Message.CONTENT_FORMAT_RICH,
            systemEventType = TicketUpdate.EVENT_TYPE,
            systemEventData = JsonPrimitive(ticketJson.toString()),
        )
        val preview = assertNotNull(Mappers.preview(dto, emptyList()))
        assertTrue(preview.isBot, "a bot: author is a bot even without the flag")
        assertEquals("Ticket updated", preview.plainText)
        assertEquals(Message.CONTENT_FORMAT_RICH, preview.contentFormat)
        assertEquals("4182", preview.ticket?.ticketId)
        assertEquals("High", preview.ticket?.priority)

        val deleted = assertNotNull(Mappers.preview(dto.copy(isDeleted = true), emptyList()))
        assertNull(deleted.plainText)
        assertNull(deleted.ticket)
        assertTrue(Mappers.isBot(flag = false, botId = "17", authorId = "usr_priya_raman"))
        assertFalse(Mappers.isBot(flag = false, botId = " ", authorId = "usr_priya_raman"))
    }

    @Test
    fun mergeKeepsBotAndTicketFieldsFromEitherSide() {
        val rich = MessagePreview(
            messageId = 1,
            isBot = true,
            plainText = "plain",
            systemEventType = TicketUpdate.EVENT_TYPE,
            ticket = TicketUpdate("1", "t", "https://x.test/1"),
            contentFormat = Message.CONTENT_FORMAT_RICH,
        )
        val bare = MessagePreview(messageId = 1, text = "body")
        val merged = ConversationMerge.mergePreview(rich, bare)
        assertTrue(merged?.isBot == true)
        assertEquals("plain", merged?.plainText)
        assertEquals("1", merged?.ticket?.ticketId)
        assertEquals(Message.CONTENT_FORMAT_RICH, merged?.contentFormat)
    }

    @Test
    fun ticketPayloadsUnwrapStringLayersAndRejectUnsafeLinks() {
        val twice = JsonPrimitive(JsonPrimitive(ticketJson.toString()).toString())
        assertEquals("Laptop will not boot", TicketUpdate.parse(twice)?.title)
        assertEquals("4182", TicketUpdate.parse(ticketJson)?.ticketId, "numbers become text")
        assertNull(TicketUpdate.from("other.event", ticketJson))
        val unsafe = buildJsonObject {
            put("ticketId", "1")
            put("title", "x")
            put("url", "javascript:alert(1)")
        }
        assertNull(TicketUpdate.parse(unsafe))
        assertNull(TicketUpdate.parse(JsonPrimitive("not json")))
        assertNull(TicketUpdate.parse(buildJsonObject { put("title", "no id") }))
        assertFalse(TicketUpdate.isWebUrl("https://"))
        assertTrue(TicketUpdate.isWebUrl(" https://tickets.example.test/1 "))
    }

    @Test
    fun sendFailureRoundTripsTheErrorKind() {
        SendFailure.entries.forEach { failure -> assertEquals(failure, SendFailure.of(failure.toError())) }
        assertIs<WaiveraError.ChannelArchived>(SendFailure.of(WaiveraError.ChannelArchived()).toError())
        val failed = Message(id = -1, conversationId = 7, sendFailure = SendFailure.RATE_LIMITED)
        assertIs<WaiveraError.RateLimited>(failed.sendError)
        assertNull(failed.copy(sendFailure = null).sendError)
    }

    @Test
    fun inboxActivityFallsBackFromTheLastMessageToCallsAndMeetings() {
        val callStart = Instant.parse("2026-10-20T14:28:00Z")
        val meetingStart = Instant.parse("2026-10-20T15:00:00Z")
        val bare = Conversation(id = 1, kind = ConversationKind.GROUP)
        assertNull(bare.activityAt)
        val meeting = bare.copy(activeMeeting = MeetingInfo(roomId = "r", startAt = meetingStart))
        assertEquals(meetingStart, meeting.activityAt)
        val call = meeting.copy(ongoingCall = OngoingCall(roomId = "r", startedAt = callStart))
        assertEquals(callStart, call.activityAt)
        val message = Instant.parse("2026-10-20T16:00:00Z")
        assertEquals(message, call.copy(lastMessage = MessagePreview(1, createdAt = message)).activityAt)
    }

    @Test
    fun lobbyStateSendsTheConversationIdAsAString() {
        val url = MeetingHandoff.lobbyUrl(
            okhttp3.HttpUrl.Builder().scheme("https").host("app.example.test").build(),
            "room 1",
            MeetingHandoff.State(relatedChatConversationId = "7"),
        )
        assertTrue(url.startsWith("https://app.example.test/lobby/room%201?state="))
        val decoded = java.net.URLDecoder.decode(
            String(java.util.Base64.getDecoder().decode(java.net.URLDecoder.decode(url.substringAfter("="), "UTF-8"))),
            "UTF-8",
        )
        assertTrue("\"relatedChatConversationId\":\"7\"" in decoded)
    }
}
