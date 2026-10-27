package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.push.MobilePushRegistrationRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MobilePushContractTest {
    @Test
    fun registrationUsesSproutApiFieldNamesAndRegistersAnFcmToken() {
        val request = MobilePushRegistrationRequest(
            deviceId = "device-1",
            pushToken = "fcm-token",
            pushEnabled = true,
            chatMessagesEnabled = true,
            mentionsEnabled = true,
            reactionsEnabled = false,
            activeCallsEnabled = true,
            soundEnabled = false,
            vibrationEnabled = true,
            previewContentEnabled = false,
        )

        val json: JsonObject = WaiveraJson.parseToJsonElement(WaiveraJson.encodeToString(request)).jsonObject

        assertEquals(
            setOf(
                "deviceId", "pushToken", "platform", "provider", "pushEnabled", "chatMessagesEnabled",
                "mentionsEnabled", "reactionsEnabled", "activeCallsEnabled", "soundEnabled",
                "vibrationEnabled", "previewContentEnabled",
            ),
            json.keys,
        )
        assertEquals("fcm", json.getValue("provider").jsonPrimitive.content)
        assertEquals("android", json.getValue("platform").jsonPrimitive.content)
        assertEquals(false, json.getValue("previewContentEnabled").jsonPrimitive.boolean)
    }
}
