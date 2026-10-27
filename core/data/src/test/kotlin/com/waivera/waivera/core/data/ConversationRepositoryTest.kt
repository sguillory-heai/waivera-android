package com.waivera.waivera.core.data

import app.cash.turbine.test
import com.waivera.waivera.core.data.live.LiveConversationRepository
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.isEmpty
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.Fixtures
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class ConversationRepositoryTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val repo = graph.conversations

    @AfterTest
    fun tearDown() = graph.close()

    private fun serveInbox() {
        graph.server.fixture("GET /api/chat/my-conversations", "rest-chat-get-my-conversations.json")
        graph.server.fixture("GET /api/chat/all", "rest-chat-get-all.json")
    }

    @Test
    fun coldCacheIsLoadingThenContent() = testScope.runTest {
        serveInbox()
        repo.observeConversations().test {
            val first = awaitItem()
            assertTrue(first.isInitialLoad)
            repo.refresh().getOrThrow()
            val loaded = expectMostRecentItem()
            assertNotNull(loaded.lastSyncedAt)
            assertEquals(setOf(7L, 11L, 14L, 41L, 58L), loaded.data.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
        val query = graph.server.requestsTo("/api/chat/all").single().url
        assertEquals("true", query.queryParameter("includeMeetings"))
        assertEquals("false", query.queryParameter("includeArchived"), "G-13: always sent explicitly")
    }

    @Test
    fun emptyInboxIsEmptyAfterSync() = testScope.runTest {
        graph.server.json("GET /api/chat/my-conversations", "[]")
        graph.server.json("GET /api/chat/all", """{"data":[],"success":true}""")
        repo.refresh().getOrThrow()
        assertTrue(repo.observeConversations().first().isEmpty)
    }

    @Test
    fun errorKeepsCachedDataAndReportsTypedError() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        graph.server.json(
            "GET /api/chat/my-conversations",
            Fixtures.text("rest-error-exception-middleware-500.json"),
            code = 500,
        )
        val failure = repo.refresh()
        assertIs<WaiveraError.Server>(failure.exceptionOrNull())
        val state = repo.observeConversations().first()
        assertIs<SyncStatus.Failed>(state.status)
        assertEquals(5, state.data.size, "last good copy stays visible")
    }

    @Test
    fun allFailingStillBuildsTheInboxFromMyConversations() = testScope.runTest {
        graph.server.fixture("GET /api/chat/my-conversations", "rest-chat-get-my-conversations.json")
        graph.server.status("GET /api/chat/all", 500)
        repo.refresh().getOrThrow()
        assertEquals(setOf(7L, 11L, 14L), repo.observeConversations().first().data.map { it.id }.toSet())
    }

    @Test
    fun removedConversationsDisappearOnRefresh() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        graph.server.json("GET /api/chat/my-conversations", "[]")
        graph.server.json("GET /api/chat/all", """{"data":[],"success":true}""")
        repo.refresh().getOrThrow()
        assertTrue(repo.observeConversations().first().data.isEmpty())
    }

    @Test
    fun openConversationReusesTheExactParticipantSet() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        assertEquals(7L, repo.openConversation(listOf("usr_priya_raman")).getOrThrow())
        assertTrue(graph.server.requestsTo("/api/chat/create-conversation").isEmpty())
    }

    @Test
    fun openConversationCreatesWhenNoneMatches() = testScope.runTest {
        graph.server.fixture("POST /api/chat/create-conversation", "rest-chat-create-conversation.json")
        assertEquals(97L, repo.openConversation(listOf("usr_jordan_lee")).getOrThrow())
        val body = graph.server.requestsTo("/api/chat/create-conversation").single().body?.utf8().orEmpty()
        assertTrue("usr_jordan_lee" in body && "usr_alex_rivera" !in body, "the server adds the caller")
    }

    @Test
    fun openConversationRefetchesWhenCreateFails() = testScope.runTest {
        graph.server.status("POST /api/chat/create-conversation", 500)
        serveInbox()
        assertEquals(7L, repo.openConversation(listOf("usr_priya_raman")).getOrThrow())
    }

    @Test
    fun singleConversationFetchKeepsListBadge() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        graph.server.fixture("GET /api/chat/conversation/7", "rest-chat-get-conversation.json")
        repo.refreshConversation(7).getOrThrow()
        assertEquals(1, repo.observeConversation(7).first()?.unreadCount)
    }

    @Test
    fun markReadClearsTheBadgeOptimistically() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        graph.server.fixture("PUT /api/chat/7/read-through", "rest-chat-read-through.json")
        repo.markRead(7, 9312).getOrThrow()
        assertEquals(0, repo.observeConversation(7).first()?.unreadCount)
        assertEquals(3, repo.observeUnreadTotal().first(), "only the channel badge remains")
        assertEquals(listOf(7L), graph.readsCleared, "reading in the app clears the shade too (IF-07)")
    }

    @Test
    fun aFailedMarkReadRestoresTheBadgeSoRetryCanSendItAgain() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        val before = assertNotNull(repo.observeConversation(7).first())
        assertTrue(before.unreadCount > 0)
        graph.server.status("PUT /api/chat/7/read-through", 503)
        assertIs<WaiveraError.Server>(repo.markRead(7, 9312).exceptionOrNull())
        val rolledBack = repo.observeConversation(7).first()
        assertEquals(before.unreadCount, rolledBack?.unreadCount, "the server is still unread, so is the badge")
        assertEquals(before.lastReadAt, rolledBack?.lastReadAt)
        assertEquals(emptyList(), graph.readsCleared, "still unread, so the notification stays")

        graph.server.fixture("PUT /api/chat/7/read-through", "rest-chat-read-through.json")
        repo.markRead(7, 9312).getOrThrow()
        assertEquals(0, repo.observeConversation(7).first()?.unreadCount)
    }

    private fun lobbyState(url: String): String {
        val state = url.substringAfter("?state=")
        return java.net.URLDecoder.decode(
            String(java.util.Base64.getDecoder().decode(java.net.URLDecoder.decode(state, "UTF-8"))),
            "UTF-8",
        )
    }

    @Test
    fun callLobbyJoinsTheOngoingCallWithoutCreatingARoom() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        val url = repo.callLobbyUrl(11).getOrThrow()
        assertTrue(url.startsWith("https://app.example.test/lobby/room_7f3c91?state="))
        val state = lobbyState(url)
        assertTrue("\"relatedChatConversationId\":\"11\"" in state, "a string, as the web client sends it")
        assertTrue("\"isPublisher\":false" in state)
        assertTrue("\"isOneToOneCall\":false" in state, "three participants")
        assertTrue("\"calenderEventId\":null" in state)
        assertTrue(graph.server.requestsTo("/api/video/createmeetingroom").isEmpty())
    }

    @Test
    fun callLobbyCreatesARoomWhenNoneIsActive() = testScope.runTest {
        serveInbox()
        repo.refresh().getOrThrow()
        graph.server.fixture("POST /api/video/createmeetingroom", "rest-video-create-meeting-room.json")
        val url = repo.callLobbyUrl(7).getOrThrow()
        assertTrue(url.startsWith("https://app.example.test/lobby/room_7f3c91?state="))
        val body = graph.server.requestsTo("/api/video/createmeetingroom").single().body?.utf8().orEmpty()
        assertTrue("\"conversationId\":7" in body)
        assertFalse("\"calendarEventId\":\"" in body, "no event for an ad-hoc call")
        val state = lobbyState(url)
        assertTrue("\"isPublisher\":true" in state, "the creator publishes")
        assertTrue("\"isOneToOneCall\":true" in state)
        assertTrue("\"relatedChatConversationId\":\"7\"" in state)
    }

    @Test
    fun callLobbyFetchesAnUncachedConversationAndNeedsTheWebBase() = testScope.runTest {
        graph.server.fixture("GET /api/chat/conversation/7", "rest-chat-get-conversation.json")
        graph.server.fixture("POST /api/video/createmeetingroom", "rest-video-create-meeting-room.json")
        assertTrue(repo.callLobbyUrl(7).getOrThrow().contains("/lobby/"))
        assertEquals(1, graph.server.requestsTo("/api/chat/conversation/7").size)

        val noWebBase = NetworkConfig(graph.server.baseUrl, "", allowLocalCleartext = true)
        val unconfigured = LiveConversationRepository(
            graph.chatApi, graph.videoApi, noWebBase, graph.cache, graph.sync,
            graph.session, graph.typing, graph.visible, graph.clock, {},
        )
        val error = assertIs<WaiveraError.Configuration>(unconfigured.callLobbyUrl(7).exceptionOrNull())
        assertEquals(listOf("waivera.webBaseUrl"), error.missingKeys)
    }
}
