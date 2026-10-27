package com.waivera.waivera.core.notifications.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.waivera.waivera.core.notifications.di.NotificationsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Simulates an FCM push in debug builds:
 *
 * ```
 * adb shell am broadcast -a com.waivera.waivera.DEBUG_PUSH -p com.waivera.waivera \
 *   --es payload "$(cat docs/push-test-payloads/chat-message.json)"
 * ```
 *
 * `payload` is either the FCM data map or a whole FCM request body (`message.data` and
 * `message.notification` are read from it). It goes through the same handler as real pushes.
 */
class DebugPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: return
        val parsed = DebugPushPayload.parse(payload) ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationsEntryPoint::class.java,
        )
        val pending = goAsync()
        entryPoint.scope().launch {
            try {
                entryPoint.pushHandler().handle(parsed.data, parsed.title, parsed.body)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_PAYLOAD = "payload"
    }
}

internal data class DebugPushPayload(val data: Map<String, String>, val title: String?, val body: String?) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): DebugPushPayload? {
            val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return null
            val message = root["message"] as? JsonObject
            val dataObject = (message?.get("data") ?: root["data"] ?: root) as? JsonObject ?: return null
            val notification = (message?.get("notification") ?: root["notification"]) as? JsonObject
            return DebugPushPayload(
                data = dataObject.mapNotNull { (key, value) -> value.asString()?.let { key to it } }.toMap(),
                title = notification?.get("title")?.asString(),
                body = notification?.get("body")?.asString(),
            )
        }

        private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
    }
}
