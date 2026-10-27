package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.payload.PushMessage
import com.waivera.waivera.core.notifications.payload.PushPayloadParser
import com.waivera.waivera.core.notifications.presentation.ActiveConversationTracker
import com.waivera.waivera.core.notifications.presentation.ConversationInfoSource
import com.waivera.waivera.core.notifications.presentation.LiveConversationMonitor
import com.waivera.waivera.core.notifications.presentation.NotificationPreferencesSource
import com.waivera.waivera.core.notifications.presentation.NotificationPresentationPolicy
import com.waivera.waivera.core.notifications.presentation.Notifier
import com.waivera.waivera.core.notifications.presentation.PresenceSnapshot
import com.waivera.waivera.core.notifications.presentation.PresentationDecision
import com.waivera.waivera.core.notifications.presentation.PushSessionGate
import com.waivera.waivera.core.notifications.presentation.ThreadRootLookup
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one path from a push payload to the notification shade, shared by the FCM service and the
 * debug broadcast receiver. Never logs payload contents. Nothing is shown while signed out.
 */
@Singleton
@Suppress("LongParameterList")
class PushMessageHandler @Inject constructor(
    private val notifier: Notifier,
    private val preferences: NotificationPreferencesSource,
    private val conversations: ConversationInfoSource,
    private val hub: LiveConversationMonitor,
    private val tracker: ActiveConversationTracker,
    private val session: PushSessionGate,
    private val threads: ThreadRootLookup,
) {
    /** @return what was decided, for tests and debug tooling; null when the payload was unusable. */
    suspend fun handle(
        data: Map<
            String,
            String,
            >,
        notificationTitle: String? = null,
        notificationBody: String? = null,
    ): PresentationDecision? {
        val parsed = PushPayloadParser.parse(data, notificationTitle, notificationBody) ?: return null
        if (!session.allowsNotifications()) {
            return PresentationDecision.Suppress(PresentationDecision.SuppressReason.SignedOut)
        }
        val parsedChat = parsed as? PushMessage.Chat
        // The data layer confirms hub + conversation; the tracker narrows it to the exact thread.
        val liveOnScreen = parsedChat != null && hub.isLiveOnScreen(parsedChat.conversationId)
        val message = if (liveOnScreen && parsedChat != null) withThread(parsedChat) else parsed
        val chat = message as? PushMessage.Chat
        val conversation = chat?.let { conversations.find(it.conversationId) }
        val channel = NotificationPresentationPolicy.channelFor(message, conversation)
        val decision = NotificationPresentationPolicy.decide(
            message = message,
            channel = channel,
            preferences = preferences.current(),
            presence = PresenceSnapshot(hubConnected = liveOnScreen, visibleChat = tracker.visible.value),
        )
        if (decision is PresentationDecision.Show && notifier.canPost()) {
            notifier.show(message, decision.channel, conversation)
        }
        return decision
    }

    /**
     * A thread reply must not be hidden because its main conversation is open (the reply is not
     * in the main timeline), so the thread comes from the cache when the payload lacks it.
     */
    private suspend fun withThread(chat: PushMessage.Chat): PushMessage.Chat {
        if (chat.threadRootMessageId != null) return chat
        val root = chat.messageId?.let { threads.threadRootOf(it) } ?: return chat
        return chat.copy(threadRootMessageId = root)
    }
}
