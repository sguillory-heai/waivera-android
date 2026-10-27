package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.AuthTokenProvider
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.api.MobilePushApi
import com.waivera.waivera.core.network.di.IoDispatcher
import com.waivera.waivera.core.network.dto.MobilePushRegistrationRequest
import com.waivera.waivera.core.network.dto.MobilePushUnregisterRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Schedules [LivePushRegistrationRepository.retryPendingUnregister] (WorkManager in the app). */
fun interface PushUnregisterScheduler {
    fun schedule()
}

/**
 * `api/mobile-push` registration (09-push.md, G-08). Native builds register FCM tokens
 * (`provider: "fcm"`), which needs the matching sprout_api change. Re-registering resets the
 * server's `lastRegisteredAt` and can drop an in-flight push, so a fingerprint of everything
 * sent is kept and identical registrations are skipped.
 *
 * An unregister that fails for a transient reason (offline sign-out) is kept with the ID token
 * captured while signed in and retried in the background until that token expires (AF-05). The
 * FCM token is revoked on every session end as well, so a stale server row cannot deliver.
 */
@Singleton
@Suppress("LongParameterList")
class LivePushRegistrationRepository @Inject constructor(
    private val pushApi: MobilePushApi,
    private val secureStore: SecureStore,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
    private val tokens: AuthTokenProvider,
    private val retryScheduler: PushUnregisterScheduler,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PushRegistrationRepository {
    private val lock = Mutex()

    suspend fun deviceId(): String = lock.withLock { deviceIdLocked() }

    private suspend fun deviceIdLocked(): String = secureStore.get(SecureStore.DEVICE_ID)
        ?: UUID.randomUUID().toString().also { secureStore.put(SecureStore.DEVICE_ID, it) }

    override suspend fun register(token: String, preferences: NotificationPreferences): Result<Unit> = catchingWaivera {
        val userId = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        lock.withLock {
            // A new registration supersedes an old unregister; retrying it would deactivate this one.
            secureStore.put(SecureStore.PENDING_UNREGISTER, null)
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
            val bearer = withContext(io) { runCatching { tokens.idToken(forceRefresh = false) }.getOrNull() }
            try {
                apiCall { pushApi.unregister(MobilePushUnregisterRequest(deviceId)) }
            } catch (error: WaiveraError) {
                if (error.isTransient && bearer != null) {
                    val pending = PendingUnregister(deviceId, bearer, clock.instant())
                    val encoded = WaiveraJson.encodeToString(PendingUnregister.serializer(), pending)
                    secureStore.put(SecureStore.PENDING_UNREGISTER, encoded)
                    retryScheduler.schedule()
                }
                throw error
            }
            Unit
        }
    }

    /**
     * Retries a queued unregister with the token captured at sign-out. Returns true when it
     * should be tried again later (still offline); an expired token or a server answer ends it.
     */
    suspend fun retryPendingUnregister(): Boolean = lock.withLock {
        val pending = secureStore.get(SecureStore.PENDING_UNREGISTER)?.let { raw ->
            runCatching { WaiveraJson.decodeFromString(PendingUnregister.serializer(), raw) }.getOrNull()
        }
        if (pending == null || Duration.between(pending.capturedAt, clock.instant()) > TOKEN_LIFETIME) {
            secureStore.put(SecureStore.PENDING_UNREGISTER, null)
            return@withLock false
        }
        val retryLater = try {
            apiCall {
                pushApi.unregisterWithToken("Bearer ${pending.idToken}", MobilePushUnregisterRequest(pending.deviceId))
            }
            false
        } catch (error: WaiveraError) {
            error.isTransient
        }
        if (!retryLater) secureStore.put(SecureStore.PENDING_UNREGISTER, null)
        retryLater
    }

    override suspend fun onNewToken(token: String) {
        secureStore.put(SecureStore.FCM_TOKEN, token)
        if (session.state.value.currentUserId != null) register(token, settings.settings.first().notifications)
    }

    suspend fun storedToken(): String? = secureStore.get(SecureStore.FCM_TOKEN)

    /** The ID token is a bearer secret: it lives only in the encrypted store and never in logs. */
    @Serializable
    private data class PendingUnregister(
        val deviceId: String,
        val idToken: String,
        @Serializable(with = com.waivera.waivera.core.network.time.UtcInstantSerializer::class)
        val capturedAt: Instant,
    ) {
        override fun toString(): String = "PendingUnregister(deviceId=$deviceId)"
    }

    companion object {
        /** Firebase ID tokens last an hour; stop a little before so a retry never sends a dead one. */
        private val TOKEN_LIFETIME: Duration = Duration.ofMinutes(55)

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
