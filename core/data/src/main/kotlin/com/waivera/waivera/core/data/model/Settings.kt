package com.waivera.waivera.core.data.model

import kotlinx.serialization.Serializable

enum class ThemePreference { SYSTEM, LIGHT, DARK }

/** Notification categories from PLAN.md; Android channel ids use [id]. */
enum class NotificationCategory(val id: String) {
    MESSAGES("messages"),
    GROUP_ACTIVITY("group_activity"),
    SCHEDULE_REMINDERS("schedule_reminders"),
    SCHEDULE_CHANGES("schedule_changes"),
    DIGEST("digest"),
}

/**
 * The device's notification preferences. The server stores a write-only copy inside each push
 * registration (G-08), so these are authoritative on the device and sent in full on register.
 */
@Serializable
data class NotificationPreferences(
    val enabled: Boolean = true,
    val categories: Map<NotificationCategory, Boolean> = NotificationCategory.entries.associateWith { true },
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val previewContent: Boolean = true,
) {
    fun isEnabled(category: NotificationCategory): Boolean = enabled && categories[category] != false
}

data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val notifications: NotificationPreferences = NotificationPreferences(),
)

/** Hub connection as shown in the UI. */
data class RealtimeStatus(
    val state: com.waivera.waivera.core.realtime.ConnectionState,
    /** True when the device has a validated network. */
    val isOnline: Boolean,
) {
    val isConnected: Boolean get() = state == com.waivera.waivera.core.realtime.ConnectionState.Connected
}
