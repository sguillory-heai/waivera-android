package com.waivera.waivera.feature.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * What Android itself allows, which the in-app switches cannot override: the app-wide
 * notification permission and each channel's importance. Read again on every resume, since the
 * person can change both in system settings at any time.
 */
internal interface SystemNotificationStatus {
    fun appNotificationsAllowed(): Boolean

    /** Channel ids (the [com.waivera.waivera.core.data.model.NotificationCategory] ids) turned off in Android. */
    fun blockedChannelIds(): Set<String>
}

internal class AndroidSystemNotificationStatus @Inject constructor(@ApplicationContext private val context: Context) :
    SystemNotificationStatus {
    private val manager get() = NotificationManagerCompat.from(context)

    override fun appNotificationsAllowed(): Boolean = manager.areNotificationsEnabled()

    override fun blockedChannelIds(): Set<String> = manager.notificationChannels
        .filter { it.importance == NotificationManager.IMPORTANCE_NONE }
        .map { it.id }
        .toSet()
}

/** System settings screens; channels exist from API 26, which is the app's minimum. */
internal object SystemNotificationIntents {
    fun app(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun channel(context: Context, channelId: String): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Module
@InstallIn(SingletonComponent::class)
internal interface SettingsSystemModule {
    @Binds
    fun systemNotificationStatus(status: AndroidSystemNotificationStatus): SystemNotificationStatus
}
