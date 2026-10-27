package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import com.waivera.waivera.core.notifications.payload.PushMessage
import com.waivera.waivera.core.notifications.payload.PushPayloadParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PushPayloadParserTest {
    private fun parse(name: String): PushMessage? {
        val fixture = PushFixtures.load(name)
        return PushPayloadParser.parse(fixture.data, fixture.title, fixture.body)
    }

    @Test
    fun `android chat push carries ids title and body`() {
        val chat = assertIs<PushMessage.Chat>(parse("push-chat-message-android.json"))
        assertEquals(7L, chat.conversationId)
        assertEquals(9312L, chat.messageId)
        assertNull(chat.threadRootMessageId)
        assertEquals("Alex Rivera", chat.title)
        assertEquals("can you cover Friday?", chat.body)
        assertEquals(DeepLinkTarget.Conversation(7, 9312), chat.target)
    }

    @Test
    fun `data-only map parses the same as the full request body`() {
        assertEquals(parse("push-chat-message-android.json"), parse("push-chat-message-android-data.json"))
    }

    @Test
    fun `attachment-only and no-preview bodies are kept as the server rendered them`() {
        assertEquals(
            "Sent an attachment",
            assertIs<PushMessage.Chat>(parse("push-chat-message-android-attachment-only.json")).body,
        )
        assertEquals(
            "New message",
            assertIs<PushMessage.Chat>(parse("push-chat-message-android-silent-no-preview.json")).body,
        )
    }

    @Test
    fun `server channel id is ignored in favour of channels by meaning`() {
        val silent = assertIs<PushMessage.Chat>(parse("push-chat-message-android-silent-no-preview.json"))
        assertEquals(7L, silent.conversationId)
    }

    @Test
    fun `ios data map has no title or body but still routes`() {
        val chat = assertIs<PushMessage.Chat>(parse("push-chat-message-ios.json"))
        assertEquals(DeepLinkTarget.Conversation(7, 9312), chat.target)
        assertNull(chat.title)
        assertNull(chat.body)
        assertIs<PushMessage.Chat>(parse("push-chat-message-ios-no-preview-no-sound.json"))
    }

    @Test
    fun `expo payload with numeric ids decodes defensively`() {
        val chat = assertIs<PushMessage.Chat>(parse("push-chat-message-expo.json"))
        assertEquals(7L, chat.conversationId)
        assertEquals(9312L, chat.messageId)
    }

    @Test
    fun `thread reply routes to the thread with the reply highlighted`() {
        val chat = assertIs<PushMessage.Chat>(parse("chat-message-thread-reply.json"))
        assertEquals(DeepLinkTarget.Thread(conversationId = 7, rootMessageId = 9330, messageId = 9332), chat.target)
    }

    @Test
    fun `legacy meeting push opens the event and uses the notification block`() {
        val meeting = assertIs<PushMessage.Meeting>(parse("push-legacy-meeting.json"))
        assertEquals("evt_8f2b19c4", meeting.eventId)
        assertEquals("Meeting scheduled", meeting.title)
        assertEquals("Priya Raman scheduled North team check-in.", meeting.body)
        assertEquals(DeepLinkTarget.CalendarEvent("evt_8f2b19c4"), meeting.target)
    }

    @Test
    fun `legacy call push opens its conversation`() {
        val call = assertIs<PushMessage.IncomingCall>(parse("push-legacy-call.json"))
        assertEquals("room_7f3c91", call.roomId)
        assertEquals(7L, call.conversationId)
        assertEquals("Priya Raman", call.callerName)
        assertEquals(DeepLinkTarget.Conversation(7), call.target)
    }

    @Test
    fun `legacy survey push opens Today`() {
        val survey = assertIs<PushMessage.Survey>(parse("push-legacy-survey.json"))
        assertEquals("svy_inst_9931", survey.instanceId)
        assertEquals(DeepLinkTarget.Today, survey.target)
    }

    @Test
    fun `unknown type opens the inbox instead of being dropped`() {
        val unknown = assertIs<PushMessage.Unknown>(parse("unknown-type.json"))
        assertEquals("announcement", unknown.type)
        assertEquals("Office closed Monday", unknown.title)
        assertEquals(DeepLinkTarget.Inbox, unknown.target)
    }

    @Test
    fun `a payload with neither type nor NotificationType is unknown`() {
        assertIs<PushMessage.Unknown>(PushPayloadParser.parse(mapOf("title" to "Hi")))
    }

    @Test
    fun `chat push without a usable conversation id is dropped`() {
        listOf("0", "-4", "seven", "", "7.5").forEach { bad ->
            assertNull(PushPayloadParser.parse(mapOf("type" to "chat", "conversationId" to bad)), "conversationId=$bad")
        }
        assertNull(PushPayloadParser.parse(mapOf("type" to "chat")))
    }

    @Test
    fun `chat push falls back to the url when the id field is missing`() {
        val chat = assertIs<PushMessage.Chat>(PushPayloadParser.parse(mapOf("type" to "chat", "url" to "/chats/42")))
        assertEquals(42L, chat.conversationId)
        assertNull(PushPayloadParser.parse(mapOf("type" to "chat", "url" to "/calendar/42")))
    }

    @Test
    fun `keys and type are matched case-insensitively`() {
        val chat = assertIs<PushMessage.Chat>(
            PushPayloadParser.parse(mapOf("Type" to "CHAT", "ConversationId" to "12", "MessageID" to "5")),
        )
        assertEquals(DeepLinkTarget.Conversation(12, 5), chat.target)
    }

    @Test
    fun `invalid message id is ignored but the conversation still opens`() {
        val chat = assertIs<PushMessage.Chat>(
            PushPayloadParser.parse(mapOf("type" to "chat", "conversationId" to "7", "messageId" to "0")),
        )
        assertEquals(DeepLinkTarget.Conversation(7, null), chat.target)
    }
}
