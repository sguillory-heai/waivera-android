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
    fun `a push without any type but with a conversation is shown as a chat`() {
        val chat =
            assertIs<PushMessage.Chat>(PushPayloadParser.parse(mapOf("conversationId" to "7", "messageId" to "8")))
        assertEquals(7L, chat.conversationId)
        assertNull(chat.title)
    }

    @Test
    fun `a map whose iterator reuses one entry object still parses every field`() {
        val chat = assertIs<PushMessage.Chat>(
            PushPayloadParser.parse(
                ReusedEntryMap(
                    listOf(
                        "type" to "chat",
                        "conversationId" to "7",
                        "title" to "Alex",
                        "body" to "hi",
                        "channelId" to "c",
                    ),
                ),
            ),
        )
        assertEquals(7L, chat.conversationId)
        assertEquals("Alex", chat.title)
        assertEquals("hi", chat.body)
    }

    /** Mirrors android.util.ArrayMap, whose entry iterator hands back the same mutable entry each step. */
    private class ReusedEntryMap(private val pairs: List<Pair<String, String>>) : AbstractMap<String, String>() {
        override val entries: Set<Map.Entry<String, String>> = object : AbstractSet<Map.Entry<String, String>>() {
            override val size get() = pairs.size

            override fun iterator(): Iterator<Map.Entry<String, String>> =
                object : Iterator<Map.Entry<String, String>>, Map.Entry<String, String> {
                    private var index = -1
                    override val key get() = pairs[index].first
                    override val value get() = pairs[index].second

                    override fun hasNext() = index + 1 < pairs.size

                    override fun next(): Map.Entry<String, String> {
                        if (!hasNext()) throw NoSuchElementException()
                        index++
                        return this
                    }
                }
        }
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

    @Test
    fun `an inbox push carries its notification and task ids and opens the inbox`() {
        val notice = assertIs<PushMessage.Notice>(
            PushPayloadParser.parse(
                mapOf(
                    "type" to "notification",
                    "notificationId" to "6f1c2b9e-4d1a-4a8e-9a51-2a1f0c3b7d10",
                    "resourceType" to "task",
                    "resourceId" to "42",
                    "title" to "Task 42 is ready for your approval",
                    "body" to "Priya Raman submitted task 42",
                ),
            ),
        )
        assertEquals("6f1c2b9e-4d1a-4a8e-9a51-2a1f0c3b7d10", notice.notificationId)
        assertEquals("42", notice.taskId)
        assertEquals("Task 42 is ready for your approval", notice.title)
        assertEquals(DeepLinkTarget.Notices, notice.target)
    }

    @Test
    fun `an inbox push without a usable id is not trusted as one`() {
        assertIs<PushMessage.Unknown>(
            PushPayloadParser.parse(mapOf("type" to "notification", "notificationId" to "../x")),
        )
        val noTask = assertIs<PushMessage.Notice>(
            PushPayloadParser.parse(
                mapOf(
                    "type" to "notification",
                    "notificationId" to "esign-request-12",
                    "resourceType" to "task",
                    "resourceId" to "-3",
                ),
            ),
        )
        assertNull(noTask.taskId)
    }
}
