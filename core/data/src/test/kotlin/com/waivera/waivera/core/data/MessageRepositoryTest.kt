package com.waivera.waivera.core.data

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.FixtureServer
import com.waivera.waivera.core.testing.Fixtures
import com.waivera.waivera.core.testing.eventually
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class MessageRepositoryTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val repo = graph.messages

    @AfterTest
    fun tearDown() = graph.close()

    private fun page(ids: IntRange, conversationId: Long = 7): String = ids.joinToString(",", "[", "]") { id ->
        """{"id":$id,"conversationId":$conversationId,"userId":"usr_priya_raman",""" +
            """"messageContent":"m$id","createDate":"2026-10-20T13:00:00"}"""
    }

    private suspend fun timeline() = graph.cache.observeMessages(7).first()

    @Test
    fun refreshLatestLoadsAPageAndKnowsWhenNothingIsOlder() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        repo.refreshLatest(7).getOrThrow()
        assertEquals(listOf(9310L, 9311L, 9312L), timeline().map { it.id })
        val meta = graph.cache.document("timeline:7", com.waivera.waivera.core.data.live.TimelineMeta.serializer())
        assertFalse(meta?.hasOlder ?: true, "a short page means no older messages (G-06)")
        assertEquals("50", graph.server.requests.single().url.queryParameter("pageSize"))
    }

    @Test
    fun emptyConversationIsEmpty() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination-empty.json")
        repo.refreshLatest(7).getOrThrow()
        assertTrue(timeline().isEmpty())
    }

    @Test
    fun aFullPageNewerThanTheCacheDropsTheStaleCache() = testScope.runTest {
        graph.server.json("GET /api/chat/7/messages/pagination", page(1..3))
        repo.refreshLatest(7).getOrThrow()
        graph.server.json("GET /api/chat/7/messages/pagination", page(101..150))
        repo.refreshLatest(7).getOrThrow()
        val ids = timeline().map { it.id }
        assertEquals(50, ids.size)
        assertTrue(ids.none { it < 101 }, "no hole between old cache and new page")
    }

    @Test
    fun loadOlderStopsOnAShortPage() = testScope.runTest {
        graph.server.json("GET /api/chat/7/messages/pagination", page(101..150))
        repo.refreshLatest(7).getOrThrow()
        graph.server.json("GET /api/chat/7/messages/pagination", page(81..100))
        assertFalse(repo.loadOlder(7).getOrThrow())
        assertEquals("101", graph.server.requests.last().url.queryParameter("beforeMessageId"))
        assertFalse(repo.loadOlder(7).getOrThrow(), "no request once has-more is false")
        assertEquals(2, graph.server.requests.size)
    }

    @Test
    fun loadOlderStopsWhenTheOldestIdDoesNotMoveBack() = testScope.runTest {
        graph.server.json("GET /api/chat/7/messages/pagination", page(101..150))
        repo.refreshLatest(7).getOrThrow()
        assertFalse(repo.loadOlder(7).getOrThrow(), "a full page that repeats ids must stop paging")
    }

    @Test
    fun sendOverTheHubReconcilesByClientId() = testScope.runTest {
        val clientId = repo.send(7, "on my way")
        runCurrent()
        assertTrue(graph.db.outbox.rows.value.isEmpty(), "optimistic row replaced by the server message")
        val sent = timeline().single()
        assertEquals(clientId, sent.clientMessageId)
        assertEquals(SendState.SENT, sent.sendState)
        assertEquals(clientId, graph.realtime.calls.single().second[4])
    }

    @Test
    fun sendFallsBackToRestWithTheSameClientIdWhenTheHubIsDown() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.route("POST /api/chat/send") { request ->
            val clientId = Regex(
                "\"clientMessageId\":\"([^\"]+)\"",
            ).find(request.body?.utf8().orEmpty())?.groupValues?.get(1)
            FixtureServer.json(
                """{"id":9400,"conversationId":7,"clientMessageId":"$clientId",""" +
                    """"messageContent":"hi","userId":"usr_alex_rivera"}""",
            )
        }
        val clientId = repo.send(7, "hi")
        eventually { graph.db.outbox.rows.value.isEmpty() }
        assertEquals(clientId, timeline().single().clientMessageId)
    }

    @Test
    fun timelineShowsTheOptimisticMessageWhileQueued() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 503)
        repo.observeTimeline(7).test {
            repo.send(7, "pending")
            runCurrent()
            val latest = expectMostRecentItem().data.messages.last()
            assertTrue(latest.isPending)
            assertTrue(latest.id < 0)
            assertEquals(1, graph.realtime.attended[7], "collecting joins the hub group")
            cancelAndIgnoreRemainingEvents()
        }
        runCurrent()
        assertTrue(graph.realtime.attended.isEmpty(), "leaving the screen releases the group")
    }

    @Test
    fun transientFailureStaysQueuedAndSchedulesARetry() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 503)
        val clientId = repo.send(7, "later")
        eventually { graph.scheduler.scheduled > 0 }
        val item = graph.outbox.get(clientId)
        assertEquals(SendState.QUEUED, item?.state)
        assertEquals(1, item?.attempts)
        assertEquals(1, graph.scheduler.scheduled)
    }

    @Test
    fun permanentFailureShowsRetryAndRetrySucceedsWithoutDuplicates() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 400, "\"Bad request\"")
        val clientId = repo.send(7, "oops")
        eventually { graph.outbox.get(clientId)?.state == SendState.FAILED }
        assertEquals(SendState.FAILED, graph.outbox.get(clientId)?.state)

        graph.server.json(
            "POST /api/chat/send",
            """{"id":9401,"conversationId":7,"clientMessageId":"$clientId","messageContent":"oops"}""",
        )
        repo.retry(clientId).getOrThrow()
        eventually { graph.outbox.get(clientId) == null }
        assertNull(graph.outbox.get(clientId))
        assertEquals(1, timeline().size)
    }

    @Test
    fun hubEchoBeforeTheResponseDoesNotDuplicate() = testScope.runTest {
        graph.realtime.sendResult = { conversationId, text, clientId, _ ->
            com.waivera.waivera.core.network.dto.MessageSendResponse(
                id = 9500,
                conversationId = conversationId,
                clientMessageId = clientId,
                messageContent = text,
            )
        }
        val clientId = repo.send(7, "twice?")
        // The echo arrives with the same id, as ReceiveMessage would deliver it.
        graph.ingest.ingest(
            listOf(
                com.waivera.waivera.core.data.model.Message(
                    id = 9500,
                    conversationId = 7,
                    clientMessageId = clientId,
                    text = "twice?",
                ),
            ),
        )
        runCurrent()
        assertEquals(listOf(9500L), timeline().map { it.id })
        assertTrue(graph.db.outbox.rows.value.isEmpty())
    }

    @Test
    fun echoWithoutClientIdReconcilesByAuthorTextAndTime() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 503)
        repo.send(7, "  see you soon ")
        eventually { graph.scheduler.scheduled > 0 }
        assertEquals(1, graph.db.outbox.rows.value.size)
        graph.ingest.ingest(
            listOf(
                com.waivera.waivera.core.data.model.Message(
                    id = 9600,
                    conversationId = 7,
                    authorId = "usr_alex_rivera",
                    text = "see you soon",
                    createdAt = graph.clock.now.plusSeconds(20),
                ),
            ),
        )
        assertTrue(graph.db.outbox.rows.value.isEmpty())
    }

    @Test
    fun reactionsAreOptimisticAndRollBackOnFailure() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        repo.refreshLatest(7).getOrThrow()
        repo.react(9310, "👍").getOrThrow()
        assertEquals(listOf("👍"), graph.cache.message(9310)?.reactions?.map { it.emoji })

        graph.realtime.failNext = WaiveraError.Validation()
        assertIs<WaiveraError.Validation>(repo.react(9310, "🎉").exceptionOrNull())
        assertEquals(listOf("👍"), graph.cache.message(9310)?.reactions?.map { it.emoji }, "rolled back")
    }

    @Test
    fun reactionsNeedTheHub() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        repo.refreshLatest(7).getOrThrow()
        graph.realtime.connected = false
        assertIs<WaiveraError.RealtimeUnavailable>(repo.react(9310, "👍").exceptionOrNull())
    }

    @Test
    fun reactionToggleRules() {
        val me = "usr_alex_rivera"
        val base = listOf(com.waivera.waivera.core.data.model.Reaction("other", "👍"))
        val added = repo.toggle(base, me, "👍")
        assertEquals(2, added.size)
        assertEquals(base, repo.toggle(added, me, "👍"), "same emoji removes")
        assertEquals("🎉", repo.toggle(added, me, "🎉").single { it.userId == me }.emoji, "different emoji replaces")
    }

    @Test
    fun deleteBlanksTheMessage() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        repo.refreshLatest(7).getOrThrow()
        graph.server.fixture("DELETE /api/chat/message/9311", "rest-chat-delete-message.json")
        repo.delete(9311).getOrThrow()
        val deleted = graph.cache.message(9311)
        assertTrue(deleted?.isDeleted == true)
        assertNull(deleted?.text)
    }

    @Test
    fun editUpdatesText() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        repo.refreshLatest(7).getOrThrow()
        val editFixture = Fixtures.text("rest-chat-edit-message.json")
        val editedId = Regex("\"id\":\\s*(\\d+)").find(editFixture)?.groupValues?.get(1)?.toLong() ?: 9311
        graph.server.json("PUT /api/chat/message/$editedId", editFixture)
        val edited = repo.edit(editedId, "fixed").getOrThrow()
        assertTrue(edited.isEdited)
    }
}
