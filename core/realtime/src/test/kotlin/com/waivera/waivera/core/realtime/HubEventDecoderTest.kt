package com.waivera.waivera.core.realtime

import com.waivera.waivera.core.network.dto.AttentionReason
import com.waivera.waivera.core.network.dto.CallRecordStatus
import com.waivera.waivera.core.network.dto.ChannelType
import com.waivera.waivera.core.network.dto.ReactionAction
import com.waivera.waivera.core.testing.Fixtures
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HubEventDecoderTest {
    private fun chat(name: String): HubEvent {
        val invocation = Fixtures.hub(name)
        return assertNotNull(HubEventDecoder.decode(invocation.target, invocation.arguments), name)
    }

    @Test
    fun everyChatHubFixtureDecodes() {
        val names = Fixtures.names("hub-").filterNot { it.startsWith("hub-video-") }
        assertTrue(names.size >= 25, "expected the full hub fixture set, got ${names.size}")
        names.forEach { chat(it) }
    }

    @Test
    fun everyVideoHubFixtureDecodes() {
        val names = Fixtures.names("hub-video-")
        assertTrue(names.isNotEmpty())
        names.forEach { name ->
            val invocation = Fixtures.hub(name)
            assertNotNull(HubEventDecoder.decodeVideo(invocation.target, invocation.arguments), name)
        }
    }

    @Test
    fun everyFixtureTargetIsRegistered() {
        Fixtures.names("hub-").filterNot { it.startsWith("hub-video-") }.forEach { name ->
            val invocation = Fixtures.hub(name)
            assertEquals(
                ChatHubContract.Events.argumentCounts[invocation.target],
                invocation.arguments.size,
                "argument count for ${invocation.target}",
            )
        }
    }

    @Test
    fun messageEventCarriesIdsAndZonelessUtcTime() {
        val event = assertIs<HubEvent.MessageReceived>(chat("hub-receive-message.json"))
        assertTrue(event.message.id > 0)
        assertTrue(event.message.conversationId > 0)
        assertNotNull(event.message.createDate)
    }

    @Test
    fun conversationCreatedDecodesIntegerChannelType() {
        val event = assertIs<HubEvent.ConversationCreated>(chat("hub-conversation-created-channel.json"))
        assertEquals(ChannelType.TEAM, event.conversation.channel?.channelType)
    }

    @Test
    fun reactionRemovedHasNullType() {
        val event = assertIs<HubEvent.Reaction>(chat("hub-receive-reaction-removed.json"))
        assertEquals(ReactionAction.REMOVED, event.action)
        assertNull(event.reactionType)
        assertEquals(9312L, event.messageId)
        assertEquals("usr_jordan_lee", event.userId)
    }

    @Test
    fun reactionAddedAndUpdated() {
        assertEquals(ReactionAction.ADDED, assertIs<HubEvent.Reaction>(chat("hub-receive-reaction-added.json")).action)
        assertEquals(
            ReactionAction.UPDATED,
            assertIs<HubEvent.Reaction>(chat("hub-receive-reaction-updated.json")).action,
        )
    }

    @Test
    fun removeChatActivityIsABareId() {
        assertEquals(4410L, assertIs<HubEvent.ChatActivityRemoved>(chat("hub-remove-chat-activity.json")).activityId)
    }

    @Test
    fun attentionReasons() {
        assertEquals(
            AttentionReason.MENTION,
            assertIs<HubEvent.ChatAttention>(chat("hub-receive-chat-attention.json")).reason,
        )
        assertEquals(
            AttentionReason.THREAD_REPLY,
            assertIs<HubEvent.ChatAttention>(chat("hub-receive-chat-attention-thread-reply.json")).reason,
        )
    }

    @Test
    fun readReceiptWithNullTime() {
        assertNull(assertIs<HubEvent.ReadReceipt>(chat("hub-receive-read-receipt-null.json")).lastReadAt)
        assertNotNull(assertIs<HubEvent.ReadReceipt>(chat("hub-receive-read-receipt.json")).lastReadAt)
    }

    @Test
    fun readAllUsesConversationZero() {
        assertEquals(
            0L,
            assertIs<HubEvent.ChatReadState>(chat("hub-receive-chat-read-state-read-all.json")).state.conversationId,
        )
    }

    @Test
    fun callStateEndIsNotLive() {
        val ended = assertIs<HubEvent.CallStateChanged>(chat("hub-conversation-call-state-changed-end.json"))
        assertEquals(CallRecordStatus.END, ended.state.status)
        assertTrue(!ended.state.status.isLive)
    }

    @Test
    fun webNotificationIsFlagged() {
        assertTrue(assertIs<HubEvent.Notification>(chat("hub-receive-notification-web.json")).isWebFeed)
    }

    @Test
    fun typingStatus() {
        val event = assertIs<HubEvent.TypingStatus>(chat("hub-receive-typing-status-for-user.json"))
        assertTrue(event.conversationId > 0)
    }

    @Test
    fun pascalCasePayloadDecodesCaseInsensitively() {
        val payload = buildJsonObject {
            put("Id", 77)
            put("ConversationId", 7)
            put("MessageContent", "hi")
            put("CreateDate", "2026-11-01T06:30:00")
        }
        val event = assertIs<HubEvent.MessageReceived>(
            HubEventDecoder.decode(ChatHubContract.Events.RECEIVE_MESSAGE, JsonArray(listOf(payload))),
        )
        assertEquals(77L, event.message.id)
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"), event.message.createDate)
    }

    @Test
    fun messageWithoutIdsIsDropped() {
        val payload = buildJsonObject { put("messageContent", "orphan") }
        assertNull(HubEventDecoder.decode(ChatHubContract.Events.RECEIVE_MESSAGE, JsonArray(listOf(payload))))
    }

    @Test
    fun unknownTargetAndGarbageAreDropped() {
        assertNull(HubEventDecoder.decode("SomethingNew", JsonArray(emptyList())))
        assertNull(
            HubEventDecoder.decode(
                ChatHubContract.Events.RECEIVE_REACTION,
                JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("y"))),
            ),
        )
    }
}
