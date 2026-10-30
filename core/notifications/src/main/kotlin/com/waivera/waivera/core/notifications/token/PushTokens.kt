package com.waivera.waivera.core.notifications.token

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.waivera.waivera.core.notifications.PushStep
import com.waivera.waivera.core.notifications.PushTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Receives FCM tokens. Implemented in `:app`: when signed in it registers the token with
 * `POST /api/mobile-push/register` (with the current preferences); when signed out it does
 * nothing and the token is registered at the next sign-in.
 */
interface PushTokenSink {
    suspend fun onNewToken(token: String)
}

/** Reads the current FCM token. Null when Firebase is not configured (demo mode) or unavailable. */
interface PushTokenProvider {
    suspend fun currentToken(): String?

    /**
     * Invalidates the current token on every session end (AS-02), so a server registration
     * that outlived the session points at a dead token. Best effort; a new token follows later.
     */
    suspend fun deleteToken()
}

@Singleton
class FirebasePushTokenProvider @Inject constructor(@ApplicationContext private val context: Context) :
    PushTokenProvider {

    // Emulators without Play services and builds without Firebase config have no token; that is
    // not an error the person can act on, so it is not surfaced.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun currentToken(): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return try {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.token.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ignored: Exception) {
            null
        }
    }

    // Offline or without Play services the token cannot be deleted now; the unregister call and
    // the server's own retention cover that case, and nothing here is actionable for the person.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun deleteToken() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ignored: Exception) {
            Unit
        }
    }
}

/**
 * Registers the current token after sign-in and on every cold start while signed in (push.md
 * §1), and when the permission is granted. Token rotation reaches the sink directly from
 * `WaiveraMessagingService.onNewToken` without touching the session or the hub.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val tokens: PushTokenProvider,
    private val sink: PushTokenSink,
    private val trace: PushTrace,
) {
    suspend fun registerCurrentToken() {
        val token = tokens.currentToken()
        if (token == null) {
            trace.note(PushStep.NoToken, null)
            return
        }
        sink.onNewToken(token)
    }
}
