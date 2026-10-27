package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.dto.MobilePushRegistrationRequest
import com.waivera.waivera.core.network.dto.MobilePushResultDto
import com.waivera.waivera.core.testing.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class MobilePushContractTest {
    @Test
    fun registrationMatchesTheAndroidRequestFixture() {
        val request = MobilePushRegistrationRequest(
            deviceId = "dev_a1b2c3d4e5f60718",
            pushToken = "fcm-example-registration-token-android-0001",
            pushEnabled = true,
            chatMessagesEnabled = true,
            mentionsEnabled = true,
            reactionsEnabled = true,
            activeCallsEnabled = true,
            soundEnabled = true,
            vibrationEnabled = true,
            previewContentEnabled = true,
        )

        val encoded: JsonObject = WaiveraJson.parseToJsonElement(
            WaiveraJson.encodeToString(MobilePushRegistrationRequest.serializer(), request),
        ).jsonObject
        val fixture = WaiveraJson.parseToJsonElement(Fixtures.text("rest-mobile-push-register-request-android.json"))

        assertEquals(fixture, encoded)
    }

    @Test
    fun registrationErrorBodyDecodes() {
        val result = WaiveraJson.decodeNormalized(
            MobilePushResultDto.serializer(),
            Fixtures.text("rest-mobile-push-register-error.json"),
        )

        assertFalse(result.success)
    }
}
