package com.waivera.waivera.core.notifications.channel

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates every [WaiveraChannel] and removes the Expo app's sound-named channels. Safe to call
 * on every app start: creating an existing channel only refreshes its name and description,
 * and never overrides the sound or vibration the person chose.
 */
@Singleton
class NotificationChannelInstaller @Inject constructor(@ApplicationContext private val context: Context) {

    fun install() {
        val manager = NotificationManagerCompat.from(context)
        WaiveraChannel.LegacyChannelIds.forEach(manager::deleteNotificationChannel)
        manager.createNotificationChannelsCompat(
            WaiveraChannel.entries.map { channel ->
                NotificationChannelCompat.Builder(channel.id, channel.importance)
                    .setName(context.getString(channel.nameRes))
                    .setDescription(context.getString(channel.descriptionRes))
                    .build()
            },
        )
    }
}
