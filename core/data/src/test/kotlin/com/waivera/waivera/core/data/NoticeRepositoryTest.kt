package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.NoticeMapping
import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.dto.EventNotificationResponse
import com.waivera.waivera.core.realtime.HubEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The web bell's notifications on the phone: load, empty, error, live arrival and read state. */
class NoticeRepositoryTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)

    @AfterTest
    fun tearDown() = graph.close()

    private val page = """
        [
          {"id":"0c1d-guid","type":"task.approval_requested","status":"new",
           "title":"Task {task} is ready for your approval","message":"{author} submitted task {task} {unknown}",
           "actionUrl":"/task/42","createdAt":"2026-11-01T15:00:00","read":false,
           "notificationResources":[
             {"resourceId":"/task/42","resourceType":"task","resourceName":"42"},
             {"resourceId":"/user/u9","resourceType":"user","resourceName":"Priya Raman"}]},
          {"id":"esign-request-77","type":"Signature","title":"Signature requested","message":"Sign the agreement",
           "actionUrl":"https://elsewhere.example/x","createdAt":"2026-10-31T15:00:00","read":true,
           "notificationResources":[]},
          {"id":"m-1","type":"meeting","title":"Team huddle","message":"Invited",
           "actionUrl":"/calendar?eventId=evt_8f2b19c4","createdAt":"2026-10-30T15:00:00","read":false,
           "notificationResources":[]}
        ]
    """.trimIndent()

    @Test
    fun loadsRendersPlaceholdersAndKeepsTheServerCount() = testScope.runTest {
        graph.server.json("GET /api/notifications", page)
        graph.server.json("GET /api/notifications/unread-count", """{"data":12,"success":true}""")
        graph.notices.refresh().getOrThrow()
        val synced = graph.notices.observeNotices().first()
        val feed = synced.data
        assertEquals(12, feed.unreadCount, "older unread rows count too")
        val approval = feed.items[0]
        assertEquals(NoticeKind.Approval, approval.kind)
        assertEquals("Task 42 is ready for your approval", approval.title)
        assertEquals("Priya Raman submitted task 42", approval.message)
        assertEquals("42", approval.taskId)
        val signature = feed.items[1]
        assertEquals(NoticeKind.Signature, signature.kind)
        assertNull(signature.webPath, "only same-site paths open")
        assertEquals("evt_8f2b19c4", feed.items[2].eventId)
        assertEquals(NoticeKind.Meeting, feed.items[2].kind)
        assertFalse(synced.isInitialLoad)
    }

    @Test
    fun anEmptyListIsEmptyAndAFailureKeepsTheLastCopy() = testScope.runTest {
        graph.server.json("GET /api/notifications", "[]")
        graph.server.json("GET /api/notifications/unread-count", """{"data":0,"success":true}""")
        graph.notices.refresh().getOrThrow()
        assertTrue(graph.notices.observeNotices().first().data.items.isEmpty())

        graph.server.json("GET /api/notifications", page)
        graph.notices.refresh().getOrThrow()
        graph.server.status("GET /api/notifications", 500)
        assertTrue(graph.notices.refresh().isFailure)
        val synced = graph.notices.observeNotices().first()
        assertIs<SyncStatus.Failed>(synced.status)
        assertEquals(3, synced.data.items.size)
    }

    @Test
    fun theCountFallsBackToThePageWhenItsCallFails() = testScope.runTest {
        graph.server.json("GET /api/notifications", page)
        graph.server.status("GET /api/notifications/unread-count", 500)
        graph.notices.refresh().getOrThrow()
        assertEquals(2, graph.notices.observeNotices().first().data.unreadCount)
    }

    @Test
    fun markReadIsOptimisticAndRollsBackWhenRefused() = testScope.runTest {
        graph.server.json("GET /api/notifications", page)
        graph.server.json("GET /api/notifications/unread-count", """{"data":2,"success":true}""")
        graph.notices.refresh().getOrThrow()

        graph.server.json("PUT /api/notifications/0c1d-guid", """{"data":true,"success":true}""")
        graph.notices.markRead("0c1d-guid").getOrThrow()
        var feed = graph.notices.observeNotices().first().data
        assertTrue(feed.items.first { it.id == "0c1d-guid" }.read)
        assertEquals(1, feed.unreadCount)

        graph.server.status("PUT /api/notifications/m-1", 500)
        assertTrue(graph.notices.markRead("m-1").isFailure)
        feed = graph.notices.observeNotices().first().data
        assertFalse(feed.items.first { it.id == "m-1" }.read)
        assertEquals(1, feed.unreadCount)

        graph.server.json("PUT /api/notifications/mark-all-read", """{"data":true,"success":true}""")
        graph.notices.markAllRead().getOrThrow()
        feed = graph.notices.observeNotices().first().data
        assertEquals(0, feed.unreadCount)
        assertTrue(feed.items.all { it.read })
    }

    @Test
    fun aWebFeedHubEventArrivesOnceAndParticipantFeedsAreIgnored() = testScope.runTest {
        val dto = WaiveraJson.decodeFromString(
            EventNotificationResponse.serializer(),
            """{"id":"live-1","type":"task.assigned","title":"A new task has been assigned to you",
               "message":"{author} assigned task {task} to you","createdAt":"2026-11-01T16:00:00","read":false,
               "notificationResources":[{"resourceId":"/task/7","resourceType":"task","resourceName":"7"}]}""",
        )
        graph.realtimeSync.apply(HubEvent.Notification(dto, isWebFeed = false))
        assertTrue(graph.notices.observeNotices().first().data.items.isEmpty())

        graph.realtimeSync.apply(HubEvent.Notification(dto, isWebFeed = true))
        graph.realtimeSync.apply(HubEvent.Notification(dto, isWebFeed = true))
        val feed = graph.notices.observeNotices().first().data
        assertEquals(listOf("live-1"), feed.items.map { it.id })
        assertEquals(1, feed.unreadCount)
        assertEquals("assigned task 7 to you", feed.items.single().message, "an unknown author is dropped")
    }

    @Test
    fun aNumericIdFromAnOlderPayloadStillDecodes() {
        val dto = WaiveraJson.decodeFromString(EventNotificationResponse.serializer(), """{"id":55120,"title":"x"}""")
        assertEquals("55120", NoticeMapping.notice(dto)?.id)
    }

    @Test
    fun aRowReadOnThePhoneStaysReadWhenALaggingPageStillSaysUnread() = testScope.runTest {
        graph.server.json("GET /api/notifications", page)
        graph.server.json("GET /api/notifications/unread-count", """{"data":2,"success":true}""")
        graph.notices.refresh().getOrThrow()
        graph.server.json("PUT /api/notifications/0c1d-guid", """{"data":true,"success":true}""")
        graph.notices.markRead("0c1d-guid").getOrThrow()
        // The server's page was built before the mark-read landed.
        graph.notices.refresh().getOrThrow()
        val feed = graph.notices.observeNotices().first().data
        assertTrue(feed.items.first { it.id == "0c1d-guid" }.read)
        assertEquals(1, feed.unreadCount)
    }

    @Test
    fun aRowThatArrivedLiveAndIsNewerThanThePageSurvivesARefresh() = testScope.runTest {
        graph.server.json("GET /api/notifications", page)
        graph.server.json("GET /api/notifications/unread-count", """{"data":2,"success":true}""")
        graph.notices.refresh().getOrThrow()
        val live = graph.notices.observeNotices().first().data.items[0]
            .copy(id = "live-1", createdAt = java.time.Instant.parse("2026-11-02T15:00:00Z"))
        graph.notices.onArrived(live)
        graph.notices.refresh().getOrThrow()
        val feed = graph.notices.observeNotices().first().data
        assertEquals("live-1", feed.items.first().id)
        assertEquals(3, feed.unreadCount)
    }
}
