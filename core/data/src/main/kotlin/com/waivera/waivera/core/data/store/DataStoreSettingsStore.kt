package com.waivera.waivera.core.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.network.WaiveraJson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "waivera_settings")

@Singleton
class DataStoreSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStore {
    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map(::read).distinctUntilChanged()

    override suspend fun setTheme(theme: ThemePreference) {
        context.settingsDataStore.edit { it[THEME] = theme.name }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[HAPTICS] = enabled }
    }

    override suspend fun updateNotifications(transform: (NotificationPreferences) -> NotificationPreferences) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(read(prefs).notifications)
            prefs[NOTIFICATIONS] = WaiveraJson.encodeToString(NotificationPreferences.serializer(), next)
        }
    }

    override suspend fun clear() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun read(prefs: Preferences): AppSettings = AppSettings(
        theme = prefs[THEME]?.let { raw -> ThemePreference.entries.firstOrNull { it.name == raw } } ?: ThemePreference.SYSTEM,
        hapticsEnabled = prefs[HAPTICS] ?: true,
        notifications = prefs[NOTIFICATIONS]?.let {
            runCatching { WaiveraJson.decodeFromString(NotificationPreferences.serializer(), it) }.getOrNull()
        } ?: NotificationPreferences(),
    )

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val HAPTICS = booleanPreferencesKey("haptics")
        val NOTIFICATIONS = stringPreferencesKey("notifications")
    }
}
