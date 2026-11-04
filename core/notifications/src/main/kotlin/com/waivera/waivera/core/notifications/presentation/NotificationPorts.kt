package com.waivera.waivera.core.notifications.presentation

import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import java.time.Instant

/**
 * What the app must provide so notifications can make local decisions. Implemented in `:app`
 * on top of the data layer, so this module never depends on repositories directly.
 */
interface NotificationPreferencesSource {
    suspend fun current(): ClientNotificationPreferences
}

/**
 * Preferences applied on the device at display time. The server only honours the master
 * switch, chat messages and preview text (push.md §1); per-category switches live here.
 */
data class ClientNotificationPreferences(
    val pushEnabled: Boolean = true,
    val enabledChannels: Set<WaiveraChannel> = WaiveraChannel.entries.toSet(),
) {
    fun allows(channel: WaiveraChannel): Boolean = pushEnabled && channel in enabledChannels
}

/**
 * The data layer's foreground check for one conversation: true only when the chat hub is
 * connected **and** that conversation is on screen (`RealtimeStatusRepository.shouldSuppressNotification`).
 */
fun interface LiveConversationMonitor {
    fun isLiveOnScreen(conversationId: Long): Boolean
}

/**
 * The sender's profile photo for a chat notification, or null when they have none or it takes
 * too long: the notification never waits more than a moment for a picture.
 */
fun interface SenderPhotoSource {
    suspend fun photo(userId: String): ByteArray?
}

/** Cached conversation facts used to title and categorize chat notifications. */
data class ConversationNotificationInfo(val title: String?, val isGroup: Boolean)

/** Looks a conversation up in the local store; null when it is not cached yet. */
interface ConversationInfoSource {
    suspend fun find(conversationId: Long): ConversationNotificationInfo?
}

/**
 * Whether a push may reach the shade: only while someone is signed in (AS-02). A push that
 * arrives after sign-out, or for the previous person on a handed-over phone, is dropped.
 */
fun interface PushSessionGate {
    suspend fun allowsNotifications(): Boolean
}

/**
 * The thread a cached message belongs to, or null when it is a main-timeline message or not
 * cached. Chat pushes do not carry the thread yet (AF-12), so suppression asks the local store.
 */
fun interface ThreadRootLookup {
    suspend fun threadRootOf(messageId: Long): Long?
}

/**
 * Applies a read made on another device (a silent read push) to the local store: the
 * conversation's badge or the notification's dot clears, and its tray entry closes with it.
 */
interface ReadSyncSink {
    /** [readAt] null when the push did not say; then the read is applied as it arrives. */
    suspend fun conversationRead(conversationId: Long, threadRootMessageId: Long?, readAt: Instant?)

    /** Null means every notification. */
    suspend fun noticeRead(notificationId: String?)
}
