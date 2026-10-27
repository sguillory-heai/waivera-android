package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.payload.PushMessage
import com.waivera.waivera.core.notifications.presentation.ClientNotificationPreferences
import com.waivera.waivera.core.notifications.presentation.ConversationNotificationInfo
import com.waivera.waivera.core.notifications.presentation.NotificationPresentationPolicy
import com.waivera.waivera.core.notifications.presentation.PresenceSnapshot
import com.waivera.waivera.core.notifications.presentation.PresentationDecision
import com.waivera.waivera.core.notifications.presentation.PresentationDecision.SuppressReason
import com.waivera.waivera.core.notifications.presentation.VisibleChat
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationPresentationPolicyTest {
    private val chat = PushMessage.Chat(
        conversationId = 42,
        messageId = 1,
        threadRootMessageId = null,
        title = "Alex",
        body = "Hi",
    )
    private val threadReply = chat.copy(threadRootMessageId = 900)
    private val allOn = ClientNotificationPreferences()

    private fun decide(
        message: PushMessage = chat,
        hubConnected: Boolean,
        visible: VisibleChat?,
        preferences: ClientNotificationPreferences = allOn,
    ) = NotificationPresentationPolicy.decide(
        message = message,
        channel = NotificationPresentationPolicy.channelFor(message, conversation = null),
        preferences = preferences,
        presence = PresenceSnapshot(hubConnected = hubConnected, visibleChat = visible),
    )

    @Test
    fun `suppressed only when connected and that conversation is on screen`() {
        assertEquals(
            PresentationDecision.Suppress(SuppressReason.ChatOnScreen),
            decide(hubConnected = true, visible = VisibleChat(42)),
        )
    }

    @Test
    fun `shown when the hub is disconnected even if the conversation is on screen`() {
        assertEquals(
            PresentationDecision.Show(WaiveraChannel.Messages),
            decide(hubConnected = false, visible = VisibleChat(42)),
        )
    }

    @Test
    fun `shown when another conversation is on screen`() {
        assertEquals(
            PresentationDecision.Show(WaiveraChannel.Messages),
            decide(hubConnected = true, visible = VisibleChat(7)),
        )
    }

    @Test
    fun `shown when no chat is on screen or the app is in the background`() {
        assertEquals(PresentationDecision.Show(WaiveraChannel.Messages), decide(hubConnected = true, visible = null))
        assertEquals(PresentationDecision.Show(WaiveraChannel.Messages), decide(hubConnected = false, visible = null))
    }

    @Test
    fun `thread replies are suppressed only when that thread is on screen`() {
        assertEquals(
            PresentationDecision.Show(WaiveraChannel.Messages),
            decide(message = threadReply, hubConnected = true, visible = VisibleChat(42)),
        )
        assertEquals(
            PresentationDecision.Suppress(SuppressReason.ChatOnScreen),
            decide(message = threadReply, hubConnected = true, visible = VisibleChat(42, 900)),
        )
        assertEquals(
            PresentationDecision.Show(WaiveraChannel.Messages),
            decide(message = chat, hubConnected = true, visible = VisibleChat(42, 900)),
        )
    }

    @Test
    fun `master switch off suppresses everything`() {
        val off = ClientNotificationPreferences(pushEnabled = false)
        assertEquals(
            PresentationDecision.Suppress(SuppressReason.PushDisabled),
            decide(hubConnected = false, visible = null, preferences = off),
        )
    }

    @Test
    fun `a disabled category suppresses only that category`() {
        val noDigest = ClientNotificationPreferences(
            enabledChannels = WaiveraChannel.entries.toSet() - WaiveraChannel.Digest,
        )
        val unknown = PushMessage.Unknown(type = "announcement", title = null, body = null)
        assertEquals(
            PresentationDecision.Suppress(SuppressReason.ChannelDisabled),
            decide(message = unknown, hubConnected = false, visible = null, preferences = noDigest),
        )
        assertEquals(
            PresentationDecision.Show(WaiveraChannel.Messages),
            decide(hubConnected = false, visible = null, preferences = noDigest),
        )
    }

    @Test
    fun `channels follow meaning`() {
        val group = ConversationNotificationInfo(title = "North team", isGroup = true)
        val direct = ConversationNotificationInfo(title = null, isGroup = false)
        assertEquals(WaiveraChannel.GroupActivity, NotificationPresentationPolicy.channelFor(chat, group))
        assertEquals(WaiveraChannel.Messages, NotificationPresentationPolicy.channelFor(chat, direct))
        assertEquals(WaiveraChannel.Messages, NotificationPresentationPolicy.channelFor(chat, null))
        assertEquals(
            WaiveraChannel.ScheduleChanges,
            NotificationPresentationPolicy.channelFor(
                PushMessage.Meeting(eventId = "evt_1", title = null, body = null),
                null,
            ),
        )
        assertEquals(
            WaiveraChannel.Messages,
            NotificationPresentationPolicy.channelFor(PushMessage.IncomingCall(null, 7, null, null, null), null),
        )
        assertEquals(
            WaiveraChannel.Digest,
            NotificationPresentationPolicy.channelFor(PushMessage.Survey(null, null, null), null),
        )
    }
}
