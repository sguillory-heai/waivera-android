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

/** Whether the chat hub is connected right now. */
fun interface HubConnectionMonitor {
    fun isConnected(): Boolean
}

/** Cached conversation facts used to title and categorize chat notifications. */
data class ConversationNotificationInfo(val title: String?, val isGroup: Boolean)

/** Looks a conversation up in the local store; null when it is not cached yet. */
interface ConversationInfoSource {
    suspend fun find(conversationId: Long): ConversationNotificationInfo?
}
