package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.payload.PushMessage
import com.waivera.waivera.core.notifications.payload.PushPayloadParser
import com.waivera.waivera.core.notifications.presentation.ActiveConversationTracker
import com.waivera.waivera.core.notifications.presentation.ConversationInfoSource
import com.waivera.waivera.core.notifications.presentation.HubConnectionMonitor
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
    private val hub: HubConnectionMonitor,
    private val tracker: ActiveConversationTracker,
) {
    /** @return what was decided, for tests and debug tooling; null when the payload was unusable. */
    suspend fun handle(
        data: Map<String,
        String>,
        notificationTitle: String? = null,
        notificationBody: String? = null,
    ): PresentationDecision? {
        val message = PushPayloadParser.parse(data, notificationTitle, notificationBody) ?: return null
        val conversation = (message as? PushMessage.Chat)?.let { conversations.find(it.conversationId) }
        val channel = NotificationPresentationPolicy.channelFor(message, conversation)
        val decision = NotificationPresentationPolicy.decide(
            message = message,
            channel = channel,
            preferences = preferences.current(),
            presence = PresenceSnapshot(hubConnected = hub.isConnected(), visibleChat = tracker.visible.value),
        )
        if (decision is PresentationDecision.Show && notifier.canPost()) {
            notifier.show(message, decision.channel, conversation)
        }
        return decision
    }
}
