package com.waivera.waivera.core.notifications.token

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

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
}

@Singleton
class FirebasePushTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : PushTokenProvider {

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
}

/**
 * Registers the current token after sign-in and on every cold start while signed in (push.md
 * §1), and when the permission is granted. Token rotation reaches the sink directly from
 * `WaiveraMessagingService.onNewToken` without touching the session or the hub.
 */
@Singleton
class PushRegistrar @Inject constructor(private val tokens: PushTokenProvider, private val sink: PushTokenSink) {
    suspend fun registerCurrentToken() {
        val token = tokens.currentToken() ?: return
        sink.onNewToken(token)
    }
}
