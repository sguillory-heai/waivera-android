package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.MeetingHandoff
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.store.SecureStore
import com.waivera.waivera.core.data.sync.LiveDataLayerStarter
import com.waivera.waivera.core.testing.FakeConnectivityMonitor
import com.waivera.waivera.core.testing.FakeForegroundMonitor
import com.waivera.waivera.core.testing.eventually
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class LifecycleAndTypingTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val foreground = FakeForegroundMonitor(foreground = false)
    private val connectivity = FakeConnectivityMonitor(online = true)

    @AfterTest
    fun tearDown() = graph.close()

    private fun starter() = LiveDataLayerStarter(
        graph.realtime, graph.realtimeSync, graph.session, graph.settings, graph.push, foreground, connectivity,
        graph.processor, graph.scheduler, graph.typing, graph.sync, testScope.backgroundScope,
    )

    @Test
    fun outgoingTypingIsThrottledToOneEventPerThreeSeconds() = testScope.runTest {
        fun elapse(millis: Long) {
            graph.clock.advanceMillis(millis)
            advanceTimeBy(millis)
            runCurrent()
        }
        repeat(10) {
            graph.typing.onComposerChanged(7, isEmpty = false)
            elapse(250)
        }
        assertEquals(1, graph.realtime.calls.count { it.first == "SendTypingStatus" && it.second[1] == true })
        elapse(1_000)
        graph.typing.onComposerChanged(7, isEmpty = false)
        runCurrent()
        assertEquals(2, graph.realtime.calls.count { it.first == "SendTypingStatus" && it.second[1] == true })
        elapse(1_900)
        assertEquals(false, graph.realtime.calls.last().second[1], "idle ends typing")
    }

    @Test
    fun clearingTheComposerEndsTypingAtOnce() = testScope.runTest {
        graph.typing.onComposerChanged(7, isEmpty = false)
        runCurrent()
        graph.typing.onComposerChanged(7, isEmpty = true)
        runCurrent()
        assertEquals(listOf(true, false), graph.realtime.calls.map { it.second[1] })
    }

    @Test
    fun typingIsNotSentWhileDisconnected() = testScope.runTest {
        graph.realtime.connected = false
        graph.typing.onComposerChanged(7, isEmpty = false)
        runCurrent()
        assertTrue(graph.realtime.calls.isEmpty())
    }

    @Test
    fun foregroundDrivesTheHubAndReconnectsResync() = testScope.runTest {
        graph.server.json("GET /api/chat/my-conversations", "[]")
        graph.server.json("GET /api/chat/all", """{"data":[],"success":true}""")
        graph.server.json("GET /api/chat/recent-activity", "[]")
        starter().start()
        runCurrent()
        assertEquals(false, graph.realtime.foreground)
        foreground.foreground.value = true
        runCurrent()
        assertEquals(true, graph.realtime.foreground)

        graph.realtime.reconnect()
        runCurrent()
        eventually { graph.server.requestsTo("/api/chat/my-conversations").isNotEmpty() }
    }

    @Test
    fun connectivityComingBackRetriesTheHubAndRefreshesAnOfflineSession() = testScope.runTest {
        graph.session.mutableState.value = SessionState.SignedIn(CurrentUser("usr_alex_rivera"), isOffline = true)
        starter().start()
        runCurrent()
        connectivity.online.value = false
        runCurrent()
        connectivity.online.value = true
        runCurrent()
        eventually { graph.session.refreshes == 1 }
        assertEquals(1, graph.realtime.retries)
    }

    @Test
    fun notificationSettingChangesReRegisterPushDebounced() = testScope.runTest {
        graph.server.fixture("POST /api/mobile-push/register", "rest-mobile-push-register.json")
        graph.secureStore.values[SecureStore.FCM_TOKEN] = "token-1"
        starter().start()
        advanceTimeBy(600)
        runCurrent()
        eventually { graph.server.requests.size == 1 }
        graph.settings.setSound(false)
        graph.settings.setVibration(false)
        advanceTimeBy(600)
        runCurrent()
        eventually { graph.server.requests.size == 2 }
        assertTrue(
            "\"vibrationEnabled\":false" in graph.server.requests.last().body?.utf8().orEmpty(),
            "one call with both changes",
        )
    }

    @Test
    fun hubEventsFlowIntoTheCache() = testScope.runTest {
        starter().start()
        runCurrent()
        graph.realtime.emit(com.waivera.waivera.core.realtime.HubEvent.ChatActivityRemoved(1))
        graph.realtime.emit(
            com.waivera.waivera.core.realtime.HubEvent.ChatActivity(
                com.waivera.waivera.core.network.dto.ChatActivityResponse(id = 5, conversationId = 7, messageId = 9),
            ),
        )
        runCurrent()
        assertEquals(listOf(5L), graph.cache.allActivity().map { it.id })
    }

    @Test
    fun settingsPersistThemeHapticsAndCategories() = testScope.runTest {
        graph.settings.setTheme(ThemePreference.DARK)
        graph.settings.setHapticsEnabled(false)
        graph.settings.setCategoryEnabled(NotificationCategory.DIGEST, false)
        val settings = graph.settings.settings.first()
        assertEquals(ThemePreference.DARK, settings.theme)
        assertFalse(settings.hapticsEnabled)
        assertFalse(settings.notifications.isEnabled(NotificationCategory.DIGEST))
        assertTrue(settings.notifications.isEnabled(NotificationCategory.MESSAGES))
    }

    @Test
    fun encodeUriComponentMatchesJavaScript() {
        assertEquals("a%20b%2Bc%2F%3D!'()*~-_.", MeetingHandoff.encodeUriComponent("a b+c/=!'()*~-_."))
        assertEquals("%7B%22k%22%3A%22%C3%A9%22%7D", MeetingHandoff.encodeUriComponent("{\"k\":\"é\"}"))
    }
}
