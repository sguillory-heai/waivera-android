package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.log.WaiveraLog
import com.waivera.waivera.core.realtime.RealtimeClient
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Invalidates this device's FCM token, so a server registration that outlived the session points
 * at a dead token (AS-02). Implemented in `:app` over Firebase Messaging; a no-op in demo mode.
 */
fun interface PushTokenRevoker {
    suspend fun revoke()
}

/**
 * The one routine for every end of a session, whatever caused it: the person signed out, the
 * backend rejected the bootstrap, Firebase revoked the user, or another account took over the
 * device (AF-04, AS-01). It stops realtime, unregisters push (queued for retry on failure), runs
 * every [SignOutCleaner] (Room, outbox, attachment and image caches, notifications, the shared
 * attachment folder), clears the secure store and revokes the FCM token. Each step is
 * independent: one failing never stops the others.
 */
@Singleton
class SessionEnder @Inject constructor(
    private val realtime: RealtimeClient,
    private val push: Provider<PushRegistrationRepository>,
    private val cleaners: Set<@JvmSuppressWildcards SignOutCleaner>,
    private val secureStore: SecureStore,
    private val pushTokens: PushTokenRevoker,
) {
    suspend fun end() {
        step("realtime") { realtime.stop() }
        // Unregister while a token may still be valid; a failure is queued by the repository.
        step("unregister") { push.get().unregister().getOrThrow() }
        cleaners.forEach { cleaner -> step("cleaner") { cleaner.clear() } }
        step("store") { secureStore.clear(keep = setOf(SecureStore.DEVICE_ID, SecureStore.PENDING_UNREGISTER)) }
        step("token") { pushTokens.revoke() }
    }

    // Cleanup must reach every step; only cancellation stops it.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            WaiveraLog.w(TAG, "Session end step failed: $name (${failure.javaClass.simpleName})")
        }
    }

    private companion object {
        const val TAG = "SessionEnd"
    }
}
