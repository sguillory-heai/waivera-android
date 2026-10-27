package com.waivera.waivera.core.notifications.presentation

import com.waivera.waivera.core.notifications.channel.WaiveraChannel

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
