package com.waivera.waivera.core.notifications.channel

import android.app.NotificationManager
import androidx.annotation.StringRes
import com.waivera.waivera.core.notifications.R

/**
 * Notification channels by meaning (UX research), not by sound. The person picks sound and
 * vibration per channel in system settings; the app never fakes that with extra channels.
 */
enum class WaiveraChannel(
    val id: String,
    val importance: Int,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
) {
    /** Direct messages and @mentions. */
    Messages(
        id = "messages",
        importance = NotificationManager.IMPORTANCE_HIGH,
        nameRes = R.string.core_notifications_channel_messages,
        descriptionRes = R.string.core_notifications_channel_messages_description,
    ),

    /** Group chat and channel activity. */
    GroupActivity(
        id = "group_activity",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        nameRes = R.string.core_notifications_channel_group_activity,
        descriptionRes = R.string.core_notifications_channel_group_activity_description,
    ),

    /** A shift or event starting within the hour. */
    ScheduleReminders(
        id = "schedule_reminders",
        importance = NotificationManager.IMPORTANCE_HIGH,
        nameRes = R.string.core_notifications_channel_schedule_reminders,
        descriptionRes = R.string.core_notifications_channel_schedule_reminders_description,
    ),

    /** An event added, moved or cancelled. */
    ScheduleChanges(
        id = "schedule_changes",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        nameRes = R.string.core_notifications_channel_schedule_changes,
        descriptionRes = R.string.core_notifications_channel_schedule_changes_description,
    ),

    /** Milestones, announcements and anything without its own category. */
    Digest(
        id = "digest",
        importance = NotificationManager.IMPORTANCE_LOW,
        nameRes = R.string.core_notifications_channel_digest,
        descriptionRes = R.string.core_notifications_channel_digest_description,
    ),
    ;

    companion object {
        /**
         * Channels the Expo app created on this same install (same application id). They are
         * deleted on first launch so the system settings list only the channels above.
         */
        val LegacyChannelIds: Set<String> = setOf(
            "chat-messages",
            "chat-messages-silent",
            "chat-messages-sound-only",
            "chat-messages-quiet",
        )

        fun fromId(id: String?): WaiveraChannel? = entries.firstOrNull { it.id == id }
    }
}
