package com.waivera.waivera.core.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.waivera.waivera.core.notifications.di.NotificationsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * FCM entry point. Android chat pushes are data-only and high priority (ADR 0003), so this
 * builds every notification itself, in every process state. A high-priority message must
 * always end in a visible notification unless the policy suppresses it for an on-screen chat.
 */
class WaiveraMessagingService : FirebaseMessagingService() {
    private val entryPoint: NotificationsEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, NotificationsEntryPoint::class.java)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title
        val body = message.notification?.body
        entryPoint.scope().launch { entryPoint.pushHandler().handle(data, title, body) }
    }

    override fun onNewToken(token: String) {
        // Rotation re-registers only; it never restarts the session or the hub (pitfall 9).
        entryPoint.scope().launch { entryPoint.tokenSink().onNewToken(token) }
    }
}
