package com.waivera.waivera.core.data.store

import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow

/**
 * Small encrypted key-value store for secrets and identifiers: device id, FCM token, the push
 * registration fingerprint, the cached session and a pending push unregister. Values never
 * appear in logs.
 */
interface SecureStore {
    suspend fun get(key: String): String?

    suspend fun put(key: String, value: String?)

    /** Removes everything except keys in [keep] (the device id survives sign-out). */
    suspend fun clear(keep: Set<String> = emptySet())

    companion object {
        const val DEVICE_ID = "device_id"
        const val FCM_TOKEN = "fcm_token"
        const val PUSH_FINGERPRINT = "push_fingerprint"
        const val CACHED_SESSION = "cached_session"

        /** A push unregister that failed while signed in, retried after sign-out (AF-05). */
        const val PENDING_UNREGISTER = "pending_unregister"
    }
}

/** Plain (unencrypted) preferences: nothing here is sensitive. */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun updateNotifications(transform: (NotificationPreferences) -> NotificationPreferences)

    suspend fun clear()
}
