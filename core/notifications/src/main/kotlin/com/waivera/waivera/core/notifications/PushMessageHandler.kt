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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one path from a push payload to the notification shade, shared by the FCM service and the
 * debug broadcast receiver. Never logs payload contents.
 */
@Singleton
class PushMessageHandler @Inject constructor(
    private val notifier: Notifier,
    private val preferences: NotificationPreferencesSource,
    private val conversations: ConversationInfoSource,
    private val hub: LiveConversationMonitor,
    private val tracker: ActiveConversationTracker,
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
        val message = PushPayloadParser.parse(data, notificationTitle, notificationBody) ?: return null
        val chat = message as? PushMessage.Chat
        val conversation = chat?.let { conversations.find(it.conversationId) }
        // The data layer confirms hub + conversation; the tracker narrows it to the exact thread.
        val liveOnScreen = chat != null && hub.isLiveOnScreen(chat.conversationId)
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
}
