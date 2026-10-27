package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.LivePushRegistrationRepository
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.Fixtures
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PushRegistrationTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val push = graph.push

    @AfterTest
    fun tearDown() = graph.close()

    @Test
    fun registersAnFcmTokenWithTheFullPreferenceSet() = testScope.runTest {
        graph.server.fixture("POST /api/mobile-push/register", "rest-mobile-push-register.json")
        push.register("fcm-token-1", NotificationPreferences()).getOrThrow()
        val body = Json.parseToJsonElement(graph.server.requests.single().body?.utf8().orEmpty()).jsonObject
        assertEquals("fcm", body.getValue("provider").jsonPrimitive.content)
        assertEquals("android", body.getValue("platform").jsonPrimitive.content)
        assertEquals("fcm-token-1", body.getValue("pushToken").jsonPrimitive.content)
        val expectedKeys = Json.parseToJsonElement(
            Fixtures.text("rest-mobile-push-register-request-android.json"),
        ).jsonObject.keys
        assertEquals(expectedKeys, body.keys, "same shape as the contract fixture")
    }

    @Test
    fun identicalRegistrationsAreSkippedAndChangesAreSent() = testScope.runTest {
        graph.server.fixture("POST /api/mobile-push/register", "rest-mobile-push-register.json")
        push.register("t", NotificationPreferences()).getOrThrow()
        push.register("t", NotificationPreferences()).getOrThrow()
        assertEquals(1, graph.server.requests.size, "G-08: re-registering resets lastRegisteredAt")
        push.register("t", NotificationPreferences(sound = false)).getOrThrow()
        assertEquals(2, graph.server.requests.size)
    }

    @Test
    fun categoriesMapOntoServerFlags() {
        val prefs = NotificationPreferences(
            categories = NotificationCategory.entries.associateWith {
                it !=
                    NotificationCategory.GROUP_ACTIVITY
            },
        )
        val request = LivePushRegistrationRepository.request("d", "t", prefs)
        assertTrue(request.chatMessagesEnabled && request.mentionsEnabled)
        assertFalse(request.reactionsEnabled)
        assertFalse(
            LivePushRegistrationRepository.request(
                "d",
                "t",
                NotificationPreferences(enabled = false),
            ).chatMessagesEnabled,
        )
    }

    @Test
    fun failureIsTypedAndNotRemembered() = testScope.runTest {
        graph.server.json(
            "POST /api/mobile-push/register",
            Fixtures.text("rest-mobile-push-register-error.json"),
            code = 400,
        )
        assertIs<WaiveraError>(push.register("t", NotificationPreferences()).exceptionOrNull())
        graph.server.fixture("POST /api/mobile-push/register", "rest-mobile-push-register.json")
        push.register("t", NotificationPreferences()).getOrThrow()
        assertEquals(2, graph.server.requests.size)
    }

    @Test
    fun unregisterSendsTheDeviceIdAndForgetsTheFingerprint() = testScope.runTest {
        graph.server.fixture("POST /api/mobile-push/register", "rest-mobile-push-register.json")
        graph.server.fixture("POST /api/mobile-push/unregister", "rest-mobile-push-unregister.json")
        push.register("t", NotificationPreferences()).getOrThrow()
        val deviceId = graph.secureStore.values.getValue(SecureStore.DEVICE_ID)
        push.unregister().getOrThrow()
        assertTrue(deviceId in graph.server.requests.last().body?.utf8().orEmpty())
        push.register("t", NotificationPreferences()).getOrThrow()
        assertEquals(3, graph.server.requests.size, "registers again after unregister")
    }

    @Test
    fun newTokenIsStoredAndRegisteredOnlyWhenSignedIn() = testScope.runTest {
        graph.server.fixture("POST /api/mobile-push/register", "rest-mobile-push-register.json")
        graph.session.mutableState.value = SessionState.SignedOut()
        push.onNewToken("t1")
        assertEquals("t1", graph.secureStore.values[SecureStore.FCM_TOKEN])
        assertTrue(graph.server.requests.isEmpty())
        graph.session.mutableState.value =
            SessionState.SignedIn(com.waivera.waivera.core.data.model.CurrentUser("usr_alex_rivera"))
        push.onNewToken("t2")
        assertEquals(1, graph.server.requests.size)
    }
}
