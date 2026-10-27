package com.waivera.waivera.notifications

import com.waivera.waivera.core.data.live.PushTokenRevoker
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.ConversationTitleText
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.DurableMessageSender
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.notifications.action.ChatNotificationActions
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import com.waivera.waivera.core.notifications.presentation.ClientNotificationPreferences
import com.waivera.waivera.core.notifications.presentation.ConversationInfoSource
import com.waivera.waivera.core.notifications.presentation.ConversationNotificationInfo
import com.waivera.waivera.core.notifications.presentation.NotificationPreferencesSource
import com.waivera.waivera.core.notifications.presentation.PushSessionGate
import com.waivera.waivera.core.notifications.presentation.ThreadRootLookup
import com.waivera.waivera.core.notifications.token.PushTokenProvider
import com.waivera.waivera.core.notifications.token.PushTokenSink
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Inline Reply and Mark read go through the outbox and the read pipeline like in-app actions.
 * After the process was killed, `send` waits for the session's bootstrap (bounded) and then
 * queues, so a reply from the shade is not lost to a cold start (AF-07).
 */
class DataLayerChatNotificationActions @Inject constructor(
    private val replies: DurableMessageSender,
    private val conversations: ConversationRepository,
) : ChatNotificationActions {
    override suspend fun reply(conversationId: Long, text: String, threadRootMessageId: Long?) {
        // The receiver finishes right after this returns and the process may go with it, so the
        // reply is persisted, given a durable retry and tried once before returning (IF-03).
        replies.sendDurably(conversationId = conversationId, text = text, parentMessageId = threadRootMessageId)
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
    private val titles: ConversationTitleText,
) : ConversationInfoSource {
    override suspend fun find(conversationId: Long): ConversationNotificationInfo? {
        val conversation = conversations.observeConversation(conversationId).first() ?: return null
        return ConversationNotificationInfo(
            title = ConversationTitles.format(conversation, session.state.value.currentUserId, titles),
            isGroup = conversation.kind != ConversationKind.DIRECT,
        )
    }
}

/** Token rotation stores and re-registers the token without touching the session or the hub. */
class RegistrationTokenSink @Inject constructor(private val registration: PushRegistrationRepository) : PushTokenSink {
    override suspend fun onNewToken(token: String) = registration.onNewToken(token)
}

/** Pushes reach the shade only while someone is signed in (AS-02); see [SessionRepository.acceptsPushes]. */
class SessionPushGate @Inject constructor(private val session: SessionRepository) : PushSessionGate {
    override suspend fun allowsNotifications(): Boolean = session.acceptsPushes(SETTLE_TIMEOUT_MILLIS)

    private companion object {
        /** Well inside FCM's processing window for a high-priority message. */
        const val SETTLE_TIMEOUT_MILLIS = 5_000L
    }
}

/** Thread replies from the local store, for suppression while a chat is on screen (AF-12). */
class CachedThreadRoots @Inject constructor(private val threads: ThreadRepository) : ThreadRootLookup {
    override suspend fun threadRootOf(messageId: Long): Long? = threads.cachedRootOf(messageId)
}

/** Every session end deletes the FCM token, so a stale server registration cannot deliver. */
class FirebasePushTokenRevoker @Inject constructor(private val tokens: PushTokenProvider) : PushTokenRevoker {
    override suspend fun revoke() = tokens.deleteToken()
}
