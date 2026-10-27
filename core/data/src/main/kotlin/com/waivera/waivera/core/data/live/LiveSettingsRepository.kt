package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.store.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Device settings. Notification changes reach the server through [PushRegistrationSync]. */
@Singleton
class LiveSettingsRepository @Inject constructor(private val store: SettingsStore) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.settings

    override suspend fun setTheme(theme: ThemePreference) = store.setTheme(theme)

    override suspend fun setHapticsEnabled(enabled: Boolean) = store.setHapticsEnabled(enabled)

    override suspend fun setNotificationsEnabled(enabled: Boolean) = store.updateNotifications { it.copy(enabled = enabled) }

    override suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) =
        store.updateNotifications { it.copy(categories = it.categories + (category to enabled)) }

    override suspend fun setSound(enabled: Boolean) = store.updateNotifications { it.copy(sound = enabled) }

    override suspend fun setVibration(enabled: Boolean) = store.updateNotifications { it.copy(vibration = enabled) }

    override suspend fun setPreviewContent(enabled: Boolean) = store.updateNotifications { it.copy(previewContent = enabled) }
}
