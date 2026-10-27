package com.waivera.waivera.notifications

import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.notifications.action.ChatNotificationActions
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.presentation.ClientNotificationPreferences
import com.waivera.waivera.core.notifications.presentation.ConversationInfoSource
import com.waivera.waivera.core.notifications.presentation.ConversationNotificationInfo
import com.waivera.waivera.core.notifications.presentation.NotificationPreferencesSource
import com.waivera.waivera.core.notifications.token.PushTokenSink
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Inline Reply and Mark read go through the outbox and the read pipeline like in-app actions. */
class DataLayerChatNotificationActions @Inject constructor(
    private val messages: MessageRepository,
    private val conversations: ConversationRepository,
) : ChatNotificationActions {
    override suspend fun reply(conversationId: Long, text: String, threadRootMessageId: Long?) {
        // send() queues in the outbox and returns the client id at once; the outbox retries.
        messages.send(conversationId = conversationId, text = text, parentMessageId = threadRootMessageId)
    }

    override suspend fun markRead(conversationId: Long, lastMessageId: Long?) {
        val through = lastMessageId
            ?: conversations.observeConversation(conversationId).first()?.lastMessage?.messageId
            ?: conversations.refreshConversation(conversationId).getOrThrow().lastMessage?.messageId
            ?: return
        conversations.markRead(conversationId, through).getOrThrow()
    }
}

/** Per-category switches from Settings, applied when a push arrives. */
class SettingsNotificationPreferences @Inject constructor(private val settings: SettingsRepository) :
    NotificationPreferencesSource {
    override suspend fun current(): ClientNotificationPreferences = settings.settings.first().notifications.toClient()
}

internal fun NotificationPreferences.toClient(): ClientNotificationPreferences = ClientNotificationPreferences(
    pushEnabled = enabled,
    enabledChannels = WaiveraChannel.entries.filterTo(mutableSetOf()) { channel ->
        val category = NotificationCategory.entries.firstOrNull { it.id == channel.id }
        category == null || categories[category] != false
    },
)

/** Titles and DM/group from the local store, so a push never waits on the network. */
class CachedConversationInfo @Inject constructor(
    private val conversations: ConversationRepository,
    private val session: SessionRepository,
) : ConversationInfoSource {
    override suspend fun find(conversationId: Long): ConversationNotificationInfo? {
        val conversation = conversations.observeConversation(conversationId).first() ?: return null
        return ConversationNotificationInfo(
            title = conversation.title(session.state.value.currentUserId).ifBlank { null },
            isGroup = conversation.kind != ConversationKind.DIRECT,
        )
    }
}

/** Token rotation stores and re-registers the token without touching the session or the hub. */
class RegistrationTokenSink @Inject constructor(private val registration: PushRegistrationRepository) : PushTokenSink {
    override suspend fun onNewToken(token: String) = registration.onNewToken(token)
}
