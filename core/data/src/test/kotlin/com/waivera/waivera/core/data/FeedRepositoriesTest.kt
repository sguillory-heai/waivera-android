package com.waivera.waivera.core.data

import app.cash.turbine.test
import com.waivera.waivera.core.data.live.LivePeopleRepository
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.isEmpty
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.Fixtures
import com.waivera.waivera.core.testing.eventually
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Bookmarks, activity, threads, people and profile: loading, empty, error and event paths. */
class FeedRepositoriesTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)

    @AfterTest
    fun tearDown() = graph.close()

    @Test
    fun bookmarksLoadAndNavigateToTheThreadRoot() = testScope.runTest {
        graph.server.fixture("GET /api/chat/bookmarks", "rest-chat-get-bookmarks.json")
        graph.bookmarks.refresh().getOrThrow()
        val bookmark = graph.bookmarks.observeBookmarks().first().data.single()
        assertEquals(9330L, bookmark.messageId)
        assertNull(bookmark.threadRootMessageId, "a root opens itself")
        assertEquals(setOf(9330L), graph.bookmarks.observeBookmarkedIds().first())
    }

    @Test
    fun bookmarks500FallsBackToPerConversationSearch() = testScope.runTest {
        graph.server.fixture("GET /api/chat/my-conversations", "rest-chat-get-my-conversations.json")
        graph.server.fixture("GET /api/chat/all", "rest-chat-get-all.json")
        graph.conversations.refresh().getOrThrow()
        graph.server.json("GET /api/chat/bookmarks", Fixtures.text("rest-error-controller-500.json"), code = 500)
        graph.server.json("GET /api/chat/search/conversation/*", """{"data":[],"success":true}""")
        graph.server.fixture("GET /api/chat/search/conversation/7", "rest-chat-search-conversation.json")
        graph.bookmarks.refresh().getOrThrow()
        assertEquals(listOf(9320L), graph.bookmarks.observeBookmarks().first().data.map { it.messageId })
        val searches = graph.server.requests.filter { it.url.encodedPath.startsWith("/api/chat/search/conversation/") }
        assertEquals(5, searches.size, "one search per cached conversation")
        assertTrue(searches.all { it.url.queryParameter("bookmarkedOnly") == "true" })
    }

    @Test
    fun bookmarkToggleUpdatesTableAndMessage() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        graph.messages.refreshLatest(7).getOrThrow()
        graph.server.fixture("POST /api/chat/message/9310/bookmark", "rest-chat-toggle-bookmark.json")
        assertTrue(graph.bookmarks.toggle(9310).getOrThrow())
        assertTrue(graph.cache.message(9310)?.isBookmarked == true)
        assertEquals(setOf(9310L), graph.bookmarks.observeBookmarkedIds().first())
        graph.bookmarks.apply(9310, false)
        assertTrue(graph.bookmarks.observeBookmarkedIds().first().isEmpty())
    }

    @Test
    fun activityPagesMarksReadAndReportsErrors() = testScope.runTest {
        graph.server.fixture("GET /api/chat/recent-activity", "rest-chat-recent-activity.json")
        graph.activity.observeActivity().test {
            assertTrue(awaitItem().isInitialLoad)
            graph.activity.refresh().getOrThrow()
            val feed = expectMostRecentItem().data
            assertEquals(3, feed.items.size)
            assertFalse(feed.hasMore, "fewer than 30 rows")
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("30", graph.server.requests.single().url.queryParameter("pageSize"))

        graph.server.status("PUT /api/chat/recent-activity/4411/read", 200)
        graph.activity.markRead(4411).getOrThrow()
        assertTrue(graph.cache.allActivity().single { it.id == 4411L }.read)
        graph.server.status("PUT /api/chat/recent-activity/read-all", 200)
        graph.activity.markAllRead().getOrThrow()
        assertEquals(0, graph.activity.observeActivity().first().data.unreadCount)
        assertFalse(graph.activity.loadMore().getOrThrow())

        graph.server.status("GET /api/chat/recent-activity", 500)
        assertIs<WaiveraError.Server>(graph.activity.refresh().exceptionOrNull())
        val failed = graph.activity.observeActivity().first()
        assertIs<SyncStatus.Failed>(failed.status)
        assertEquals(3, failed.data.items.size)
    }

    @Test
    fun emptyActivityIsEmpty() = testScope.runTest {
        graph.server.json("GET /api/chat/recent-activity", "[]")
        graph.activity.refresh().getOrThrow()
        val synced = graph.activity.observeActivity().first()
        assertTrue(synced.lastSyncedAt != null && synced.data.items.isEmpty())
    }

    @Test
    fun threadLoadsRootAndRepliesAndJoinsTheParentGroup() = testScope.runTest {
        graph.server.fixture("GET /api/chat/thread/9330", "rest-chat-get-thread.json")
        graph.threads.observeThread(7, 9330).test {
            runCurrent()
            assertEquals(1, graph.realtime.attended[7])
            eventually { graph.cache.message(9332) != null }
            runCurrent()
            val loaded = expectMostRecentItem().data
            assertEquals(9330L, loaded.root?.id)
            assertEquals(listOf(9331L, 9332L), loaded.replies.map { it.id })
            assertEquals(setOf(9330L), graph.threads.activeRoots)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun threadRepliesSendWithTheParentAndShowOptimistically() = testScope.runTest {
        graph.server.fixture("GET /api/chat/thread/9330", "rest-chat-get-thread.json")
        graph.threads.refresh(9330).getOrThrow()
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 503)
        graph.threads.observeThread(7, 9330).test {
            graph.messages.send(7, "I can take Saturday", parentMessageId = 9330)
            runCurrent()
            val reply = expectMostRecentItem().data.replies.last()
            assertTrue(reply.isPending)
            assertEquals(9330L, reply.parentMessageId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun threadErrorIsTyped() = testScope.runTest {
        graph.server.status("GET /api/chat/thread/1", 404)
        assertIs<WaiveraError.NotFound>(graph.threads.refresh(1).exceptionOrNull())
    }

    @Test
    fun directoryLoadsAndAvatarsDecodeWithANegativeCache() = testScope.runTest {
        graph.server.json("GET /api/chat/initiation-directory", """[{"id":"usr_jordan_lee","displayName":"Jordan Lee"}]""")
        graph.people.refresh().getOrThrow()
        assertEquals(listOf("usr_jordan_lee"), graph.people.observeDirectory().first().data.map { it.id })

        graph.server.fixture("GET /api/file/avatar/usr_jordan_lee", "rest-file-avatar-base64.json")
        val bytes = assertNotNull(graph.people.avatar("usr_jordan_lee"))
        assertContentEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), bytes.copyOf(4))
        graph.people.avatar("usr_jordan_lee")
        assertEquals(1, graph.server.requestsTo("/api/file/avatar/usr_jordan_lee").size, "cached")

        graph.server.status("GET /api/file/avatar/usr_none", 404)
        assertNull(graph.people.avatar("usr_none"))
        assertNull(graph.people.avatar("usr_none"))
        assertEquals(1, graph.server.requestsTo("/api/file/avatar/usr_none").size, "negative cache")
        assertNull(LivePeopleRepository.decodeDataUri("https://example.test/a.png"))
    }

    @Test
    fun emptyDirectoryIsEmpty() = testScope.runTest {
        graph.server.json("GET /api/chat/initiation-directory", "[]")
        graph.people.refresh().getOrThrow()
        assertTrue(graph.people.observeDirectory().first().isEmpty)
    }

    @Test
    fun profileLoadsWithDateOnlyFields() = testScope.runTest {
        graph.server.fixture("GET /api/employee/getProfile/usr_alex_rivera", "rest-employee-get-profile.json")
        graph.profile.refresh().getOrThrow()
        val profile = assertNotNull(graph.profile.observeMyProfile().first().data)
        assertNotNull(profile.employeeId)
        graph.server.fixture("GET /api/TimeOff/get-timeOffs-for-user-profile/usr_alex_rivera", "rest-timeoff-get-for-user-profile.json")
        assertTrue(graph.profile.timeOff("usr_alex_rivera").getOrThrow().requests.isNotEmpty())
        graph.server.fixture("GET /api/employee/get-employee-files", "rest-employee-get-employee-files.json")
        assertTrue(graph.profile.files("usr_alex_rivera", 1).getOrThrow().items.isNotEmpty())
    }

    @Test
    fun profileErrorWhenSignedOut() = testScope.runTest {
        graph.session.mutableState.value = com.waivera.waivera.core.data.model.SessionState.SignedOut()
        assertIs<WaiveraError.NotSignedIn>(graph.profile.refresh().exceptionOrNull())
    }

    @Test
    fun attachmentUrlsResolveAgainstTheApiHost() {
        val attachments = com.waivera.waivera.core.data.live.LiveAttachmentRepository(
            graph.chatApi, graph.server.config, java.nio.file.Files.createTempDirectory("att").toFile(), kotlinx.coroutines.Dispatchers.IO,
        )
        assertEquals(graph.server.baseUrl + "api/chat/message/9301/attachment", attachments.resolve("/api/chat/message/9301/attachment"))
    }

    @Test
    fun rejectedAttachmentDownloadIsTyped() = testScope.runTest {
        val dir = java.nio.file.Files.createTempDirectory("att").toFile()
        val attachments = com.waivera.waivera.core.data.live.LiveAttachmentRepository(graph.chatApi, graph.server.config, dir, kotlinx.coroutines.Dispatchers.IO)
        graph.server.status("GET /api/chat/message/9301/attachment", 423)
        assertIs<WaiveraError.AttachmentRejected>(attachments.download(9301, "plan.pdf").exceptionOrNull())
        graph.server.route("GET /api/chat/message/9302/attachment") {
            mockwebserver3.MockResponse.Builder().code(200).body("PDFDATA").build()
        }
        val file = attachments.download(9302, "../../etc/passwd").getOrThrow()
        assertEquals(dir, file.parentFile, "file names cannot escape the cache directory")
        assertEquals("PDFDATA", file.readText())
        dir.deleteRecursively()
    }
}
