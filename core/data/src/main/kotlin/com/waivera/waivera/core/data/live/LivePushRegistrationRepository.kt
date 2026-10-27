package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.api.MobilePushApi
import com.waivera.waivera.core.network.dto.MobilePushRegistrationRequest
import com.waivera.waivera.core.network.dto.MobilePushUnregisterRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `api/mobile-push` registration (09-push.md, G-08). Native builds register FCM tokens
 * (`provider: "fcm"`), which needs the matching sprout_api change. Re-registering resets the
 * server's `lastRegisteredAt` and can drop an in-flight push, so a fingerprint of everything
 * sent is kept and identical registrations are skipped.
 */
@Singleton
class LivePushRegistrationRepository @Inject constructor(
    private val pushApi: MobilePushApi,
    private val secureStore: SecureStore,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
) : PushRegistrationRepository {
    private val lock = Mutex()

    suspend fun deviceId(): String = lock.withLock { deviceIdLocked() }

    private suspend fun deviceIdLocked(): String = secureStore.get(SecureStore.DEVICE_ID)
        ?: UUID.randomUUID().toString().also { secureStore.put(SecureStore.DEVICE_ID, it) }

    override suspend fun register(token: String, preferences: NotificationPreferences): Result<Unit> = catchingWaivera {
        val userId = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        lock.withLock {
            val request = request(deviceIdLocked(), token, preferences)
            val fingerprint = fingerprint(userId, request)
            if (secureStore.get(SecureStore.PUSH_FINGERPRINT) == fingerprint) return@withLock
            val result = apiCall { pushApi.register(request) }
            if (!result.success) throw WaiveraError.Unexpected()
            secureStore.put(SecureStore.PUSH_FINGERPRINT, fingerprint)
        }
    }

    override suspend fun unregister(): Result<Unit> = catchingWaivera {
        lock.withLock {
            val deviceId = secureStore.get(SecureStore.DEVICE_ID) ?: return@withLock
            secureStore.put(SecureStore.PUSH_FINGERPRINT, null)
            apiCall { pushApi.unregister(MobilePushUnregisterRequest(deviceId)) }
            Unit
        }
    }

    override suspend fun onNewToken(token: String) {
        secureStore.put(SecureStore.FCM_TOKEN, token)
        if (session.state.value.currentUserId != null) register(token, settings.settings.first().notifications)
    }

    suspend fun storedToken(): String? = secureStore.get(SecureStore.FCM_TOKEN)

    companion object {
        /**
         * Maps device categories onto the server's flags. The server only reads `pushEnabled` and
         * `chatMessagesEnabled` today (G-08); category filtering also happens on the device.
         */
        fun request(deviceId: String, token: String, prefs: NotificationPreferences) = MobilePushRegistrationRequest(
            deviceId = deviceId,
            pushToken = token,
            pushEnabled = prefs.enabled,
            chatMessagesEnabled =
            prefs.isEnabled(NotificationCategory.MESSAGES) || prefs.isEnabled(NotificationCategory.GROUP_ACTIVITY),
            mentionsEnabled = prefs.isEnabled(NotificationCategory.MESSAGES),
            reactionsEnabled = prefs.isEnabled(NotificationCategory.GROUP_ACTIVITY),
            activeCallsEnabled = prefs.isEnabled(NotificationCategory.MESSAGES),
            soundEnabled = prefs.sound,
            vibrationEnabled = prefs.vibration,
            previewContentEnabled = prefs.previewContent,
        )

        fun fingerprint(userId: String, request: MobilePushRegistrationRequest): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("$userId|$request".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
