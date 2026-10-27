package com.waivera.waivera.core.data

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.SendFailure
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.data.outbox.OutboxItem
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.FixtureServer
import com.waivera.waivera.core.testing.Fixtures
import com.waivera.waivera.core.testing.eventually
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private const val MAX_ATTEMPTS = com.waivera.waivera.core.data.outbox.OutboxProcessor.MAX_AUTOMATIC_ATTEMPTS

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

    private fun hubAnswers(failure: WaiveraError?) {
        graph.realtime.sendResult = { conversationId, text, clientId, _ ->
            failure?.let { throw it }
            com.waivera.waivera.core.network.dto.MessageSendResponse(
                id = 9_600 + text.length.toLong(),
                conversationId = conversationId,
                clientMessageId = clientId,
                messageContent = text,
            )
        }
    }

    @Test
    fun messagesWrittenOfflineStayQueuedAndGoOutWhenTheNetworkReturns() = testScope.runTest {
        graph.connectivity.online.value = false
        hubAnswers(WaiveraError.Network())
        val ids = listOf("a", "bb", "ccc", "dddd", "eeeee", "ffffff").map { repo.send(7, it) }
        repeat(MAX_ATTEMPTS + 1) { graph.processor.process() }
        val queued = ids.mapNotNull { graph.outbox.get(it) }
        assertEquals(List(ids.size) { SendState.QUEUED }, queued.map { it.state }, "nothing fails while offline")
        assertEquals(List(ids.size) { 0 }, queued.map { it.attempts })

        graph.connectivity.online.value = true
        hubAnswers(null)
        assertFalse(graph.processor.process(), "nothing is left")
        assertTrue(ids.all { graph.outbox.get(it) == null })
    }

    @Test
    fun aSendThatFailedForAPassingReasonResumesButARejectedOneStaysFailed() = testScope.runTest {
        hubAnswers(WaiveraError.Server(503))
        val flaky = repo.send(7, "flaky")
        eventually { graph.outbox.get(flaky)?.attempts == 1 }
        repeat(MAX_ATTEMPTS) { graph.processor.process() }
        assertEquals(SendState.FAILED, graph.outbox.get(flaky)?.state, "the online retry budget is spent")
        hubAnswers(WaiveraError.Validation())
        val rejected = repo.send(8, "rejected")
        eventually { graph.outbox.get(rejected)?.state == SendState.FAILED }

        hubAnswers(null)
        graph.processor.process(resumeFailed = true)
        assertNull(graph.outbox.get(flaky), "the server error could pass, so it is sent again")
        assertEquals(SendState.FAILED, graph.outbox.get(rejected)?.state, "a rejected message waits for Retry")
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
    fun aRepeatedEchoWithAClientIdNeverRemovesAnotherQueuedMessageWithTheSameText() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 503)
        val first = repo.send(7, "ok")
        graph.clock.advanceMillis(5_000)
        val second = repo.send(7, "ok")
        eventually { graph.db.outbox.rows.value.size == 2 && graph.scheduler.scheduled > 0 }
        val echo = com.waivera.waivera.core.data.model.Message(
            id = 9700,
            conversationId = 7,
            clientMessageId = first,
            authorId = "usr_alex_rivera",
            text = "ok",
            createdAt = graph.clock.now,
        )
        // Send response and hub echo of the same message: the second ingest finds no row by id.
        graph.ingest.ingest(listOf(echo))
        graph.ingest.ingest(listOf(echo))
        assertNull(graph.outbox.get(first))
        assertEquals(second, graph.outbox.get(second)?.clientMessageId, "the second \"ok\" is still queued")
    }

    @Test
    fun theTextFallbackSkipsAMessageThatIsBeingSent() = testScope.runTest {
        val sending = OutboxItem(
            clientMessageId = "in-flight",
            conversationId = 7,
            authorId = "usr_alex_rivera",
            text = "on it",
            state = SendState.SENDING,
            createdAt = graph.clock.now,
        )
        graph.outbox.put(sending)
        graph.ingest.ingest(
            listOf(
                com.waivera.waivera.core.data.model.Message(
                    id = 9701,
                    conversationId = 7,
                    authorId = "usr_alex_rivera",
                    text = "on it",
                    createdAt = graph.clock.now.plusSeconds(1),
                ),
            ),
        )
        assertEquals(SendState.SENDING, graph.outbox.get("in-flight")?.state, "settled by its own response")
    }

    @Test
    fun aSendDuringAColdStartWaitsForTheSessionAndQueues() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 503)
        graph.session.mutableState.value = com.waivera.waivera.core.data.model.SessionState.Initializing
        val sent = async { repo.send(7, "reply from the shade") }
        runCurrent()
        assertFalse(sent.isCompleted, "waits for the bootstrap instead of failing NotSignedIn")
        graph.session.mutableState.value = com.waivera.waivera.core.data.model.SessionState.SignedIn(
            com.waivera.waivera.core.data.model.CurrentUser("usr_alex_rivera"),
        )
        val clientId = sent.await()
        assertEquals("usr_alex_rivera", graph.outbox.get(clientId)?.authorId)
    }

    @Test
    fun aSendWhileSignedOutStillFails() = testScope.runTest {
        graph.session.mutableState.value = com.waivera.waivera.core.data.model.SessionState.SignedOut()
        assertIs<WaiveraError.NotSignedIn>(runCatching { repo.send(7, "nope") }.exceptionOrNull())
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

    @Test
    fun anEditReplacesTheStalePlainTextSoThePreviewShowsTheNewWords() = testScope.runTest {
        graph.server.fixture("GET /api/chat/7/messages/pagination", "rest-chat-get-messages-pagination.json")
        repo.refreshLatest(7).getOrThrow()
        graph.cache.updateMessage(9312) { it.copy(plainText = "can you cover Friday?") }
        graph.server.json(
            "PUT /api/chat/message/9312",
            """{"data":{"id":9312,"conversationId":7,"messageContent":"can you cover Saturday?","isEdited":true},""" +
                """"statusCode":200,"success":true}""",
        )
        val edited = repo.edit(9312, "can you cover Saturday?").getOrThrow()
        assertNull(edited.plainText, "no stale rendering of the old text")
        assertEquals("can you cover Saturday?", edited.text)
        assertEquals(com.waivera.waivera.core.data.model.Message.CONTENT_FORMAT_PLAIN, edited.contentFormat)
    }

    @Test
    fun permanentFailureKeepsItsReasonUntilRetried() = testScope.runTest {
        graph.realtime.connected = false
        graph.server.status("POST /api/chat/send", 403)
        val clientId = repo.send(7, "nope")
        eventually { graph.outbox.get(clientId)?.state == SendState.FAILED }
        val failed = graph.outbox.get(clientId)
        assertEquals(SendFailure.FORBIDDEN, failed?.failure)
        assertIs<WaiveraError.Forbidden>(failed?.toMessage(null)?.sendError)

        graph.server.status("POST /api/chat/send", 503)
        repo.retry(clientId).getOrThrow()
        eventually { graph.outbox.get(clientId)?.state == SendState.QUEUED }
        assertNull(graph.outbox.get(clientId)?.failure, "a retry clears the old reason")
    }

    @Test
    fun uploadProgressShowsOnTheSendingMessageOnly() = testScope.runTest {
        val item = OutboxItem(
            clientMessageId = "c1",
            conversationId = 7,
            authorId = "usr_alex_rivera",
            text = "",
            state = SendState.SENDING,
            createdAt = Instant.parse("2026-10-20T13:00:00Z"),
        )
        graph.outbox.put(item)
        graph.outbox.setUploadProgress("c1", 0.4f)
        assertEquals(0.4f, graph.outbox.observeConversation(7).first().single().toMessage(null).uploadProgress)
        graph.outbox.put(item.copy(state = SendState.FAILED, failure = SendFailure.NETWORK))
        assertNull(graph.outbox.observeConversation(7).first().single().toMessage(null).uploadProgress)
        graph.outbox.clearUploadProgress("c1")
        graph.outbox.put(item)
        assertNull(graph.outbox.observeConversation(7).first().single().uploadProgress)
    }
}
