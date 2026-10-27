package com.waivera.waivera.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.waivera.waivera.core.notifications.channel.NotificationChannelInstaller
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationChannelInstallerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun `creates one channel per meaning with the agreed importance`() {
        NotificationChannelInstaller(context).install()
        val expected = mapOf(
            "messages" to NotificationManager.IMPORTANCE_HIGH,
            "group_activity" to NotificationManager.IMPORTANCE_DEFAULT,
            "schedule_reminders" to NotificationManager.IMPORTANCE_HIGH,
            "schedule_changes" to NotificationManager.IMPORTANCE_DEFAULT,
            "digest" to NotificationManager.IMPORTANCE_LOW,
        )
        val created = manager.notificationChannels.associate { it.id to it.importance }
        assertEquals(expected, created)
        WaiveraChannel.entries.forEach { channel ->
            assertEquals(context.getString(channel.nameRes), manager.getNotificationChannel(channel.id).name)
        }
    }

    @Test
    fun `removes the expo sound-named channels`() {
        WaiveraChannel.LegacyChannelIds.forEach { id ->
            manager.createNotificationChannel(NotificationChannel(id, id, NotificationManager.IMPORTANCE_HIGH))
        }
        NotificationChannelInstaller(context).install()
        WaiveraChannel.LegacyChannelIds.forEach { id -> assertNull(manager.getNotificationChannel(id), id) }
    }

    @Test
    fun `installing twice is harmless`() {
        val installer = NotificationChannelInstaller(context)
        installer.install()
        installer.install()
        assertEquals(WaiveraChannel.entries.size, manager.notificationChannels.size)
    }

    @Test
    fun `the FCM default channel id matches the messages channel`() {
        assertEquals(WaiveraChannel.Messages.id, context.getString(R.string.core_notifications_default_channel_id))
    }
}
