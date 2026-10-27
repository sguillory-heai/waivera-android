package com.waivera.waivera.core.notifications.presentation

import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.payload.PushMessage

/**
 * What the app knows about the foreground at the moment a push arrives. [hubConnected] is the
 * data layer's answer for the pushed conversation: hub connected and that conversation on screen.
 */
data class PresenceSnapshot(val hubConnected: Boolean, val visibleChat: VisibleChat?)

sealed interface PresentationDecision {
    data class Show(val channel: WaiveraChannel) : PresentationDecision

    enum class SuppressReason { PushDisabled, ChannelDisabled, ChatOnScreen }

    data class Suppress(val reason: SuppressReason) : PresentationDecision
}

/**
 * Decides whether a push becomes a system notification.
 *
 * Foreground suppression applies only when the hub is connected **and** the exact chat the push
 * targets (conversation, and thread when it is a thread reply) is on screen: the live hub is
 * already showing that message. In every other case (hub down, another chat, another screen,
 * app in background) the notification is shown, fixing the Expo app, which hid every chat push
 * in the foreground even when the hub was disconnected.
 */
object NotificationPresentationPolicy {

    /** Direct conversations and unknown ones use [WaiveraChannel.Messages]; groups use group activity. */
    fun channelFor(message: PushMessage, conversation: ConversationNotificationInfo?): WaiveraChannel = when (message) {
        is PushMessage.Chat -> when (conversation?.isGroup) {
            true -> WaiveraChannel.GroupActivity
            else -> WaiveraChannel.Messages
        }
        is PushMessage.IncomingCall -> WaiveraChannel.Messages
        is PushMessage.Meeting -> WaiveraChannel.ScheduleChanges
        is PushMessage.Survey, is PushMessage.Unknown -> WaiveraChannel.Digest
    }

    fun decide(
        message: PushMessage,
        channel: WaiveraChannel,
        preferences: ClientNotificationPreferences,
        presence: PresenceSnapshot,
    ): PresentationDecision = when {
        !preferences.pushEnabled -> PresentationDecision.Suppress(PresentationDecision.SuppressReason.PushDisabled)
        !preferences.allows(
            channel,
        ) -> PresentationDecision.Suppress(PresentationDecision.SuppressReason.ChannelDisabled)
        message is PushMessage.Chat && presence.hubConnected && presence.visibleChat == message.visibleChat() ->
            PresentationDecision.Suppress(PresentationDecision.SuppressReason.ChatOnScreen)
        else -> PresentationDecision.Show(channel)
    }

    private fun PushMessage.Chat.visibleChat() = VisibleChat(conversationId, threadRootMessageId)
}
