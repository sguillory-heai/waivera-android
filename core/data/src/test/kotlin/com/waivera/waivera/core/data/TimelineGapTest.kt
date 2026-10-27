package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.Mappers
import com.waivera.waivera.core.data.live.TimelineGap
import com.waivera.waivera.core.data.live.TimelineGaps
import com.waivera.waivera.core.data.live.TimelineMeta
import com.waivera.waivera.core.testing.FixtureServer
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.RecordedRequest

/** AF-03: a jump to an old message must not leave a hole between it and the cached tail. */
class TimelineGapTest {
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)
    private val repo = graph.messages

    @AfterTest
    fun tearDown() = graph.close()

    /** A conversation with messages 1..[newest], served like sprout_api (oldest first). */
    private fun serve(newest: Long) {
        graph.server.route("GET /api/chat/7/messages/pagination") { request ->
            val size = request.query("pageSize")?.toInt() ?: 50
            val before = request.query("beforeMessageId")?.toLong() ?: (newest + 1)
            val ids = (maxOf(1, before - size)..<before).toList()
            FixtureServer.json(page(ids))
        }
        graph.server.route("GET /api/chat/7/messages/context/*") { request ->
            val target = request.url.encodedPath.substringAfterLast('/').toLong()
            val before = request.query("before")?.toLong() ?: 30
            val after = request.query("after")?.toLong() ?: 30
            FixtureServer.json(page((maxOf(1, target - before)..minOf(newest, target + after)).toList()))
        }
    }

    private fun RecordedRequest.query(name: String): String? = url.queryParameter(name)

    private fun page(ids: List<Long>): String = ids.joinToString(",", "[", "]") { id ->
        val at = Instant.parse("2026-10-01T00:00:00Z").plusSeconds(id * 60)
        """{"id":$id,"conversationId":7,"userId":"usr_priya_raman","messageContent":"m$id","createDate":"$at"}"""
    }

    private suspend fun shown(): List<Long> = repo.observeTimeline(7).first().data.messages.map { it.id }

    private fun assertContiguous(ids: List<Long>, from: Long, to: Long) {
        assertEquals((from..to).toList(), ids, "every message between $from and $to is present, in order")
    }

    @Test
    fun aJumpToAnOldMessageJoinsTheTailSoScrollingDownReachesTheLatest() = testScope.runTest {
        serve(newest = 400)
        repo.refreshLatest(7).getOrThrow()
        repo.loadAround(7, 100).getOrThrow()
        val ids = shown()
        assertTrue(100L in ids, "the highlighted message is shown")
        assertContiguous(ids, 70, 400)
        assertTrue(graph.cache.document("timeline:7", TimelineMeta.serializer())?.gaps.orEmpty().isEmpty())
    }

    @Test
    fun aHoleTooWideToJoinAtOnceIsHiddenAndFilledByPagingOlder() = testScope.runTest {
        serve(newest = 3_000)
        repo.refreshLatest(7).getOrThrow()
        repo.loadAround(7, 500).getOrThrow()
        // 8 forward pages of 75 reach 1130; the tail starts at 2951.
        assertEquals(
            listOf(TimelineGap(1_130, 2_951)),
            graph.cache.document("timeline:7", TimelineMeta.serializer())?.gaps,
        )
        assertContiguous(shown(), 2_951, 3_000)
        assertTrue(repo.loadOlder(7).getOrThrow(), "the hole below the shown messages counts as older")
        val cursor = graph.server.requests.mapNotNull { it.url.queryParameter("beforeMessageId") }.last()
        assertEquals("2951", cursor, "paging starts at the hole, not below the jump")
        assertContiguous(shown(), 2_901, 3_000)
    }

    @Test
    fun aJumpOnAnEmptyCacheThenTheTailPageKeepsTheJumpAndTracksTheHole() = testScope.runTest {
        serve(newest = 3_000)
        repo.loadAround(7, 500).getOrThrow()
        assertContiguous(shown(), 470, 1_130)
        repo.refreshLatest(7).getOrThrow()
        assertTrue(graph.cache.message(500) != null, "the jump target is not dropped as a stale cache")
        assertEquals(
            listOf(TimelineGap(1_130, 2_951)),
            graph.cache.document("timeline:7", TimelineMeta.serializer())?.gaps,
        )
    }

    @Test
    fun aJumpNearTheLatestNeedsNoGap() = testScope.runTest {
        serve(newest = 120)
        repo.loadAround(7, 110).getOrThrow()
        assertTrue(graph.cache.document("timeline:7", TimelineMeta.serializer())?.gaps.orEmpty().isEmpty())
        assertContiguous(shown(), 80, 120)
    }

    /** What the hub does with a live message: it lands in the cache without any REST page. */
    private suspend fun hubDelivers(id: Long) {
        val live = graph.chatApi.getMessageContext(7, id, 0, 0).body().orEmpty().map(Mappers::message)
        graph.ingest.ingest(live)
    }

    @Test
    fun aLiveMessageAfterALongGapDoesNotHideTheMissedMessages() = testScope.runTest {
        serve(newest = 50)
        repo.refreshLatest(7).getOrThrow()
        // 121 messages arrive while the app is away; the hub delivers the newest one first.
        serve(newest = 171)
        hubDelivers(171)
        repo.refreshLatest(7).getOrThrow()
        assertContiguous(shown(), 122, 171)
        assertTrue(repo.loadOlder(7).getOrThrow())
        assertContiguous(shown(), 72, 171)
        assertTrue(repo.loadOlder(7).getOrThrow())
        assertContiguous(shown(), 22, 171)
    }

    @Test
    fun aLiveMessageInAClosedConversationDoesNotHideTheHoleBelowTheNewTail() = testScope.runTest {
        serve(newest = 3_000)
        repo.loadAround(7, 500).getOrThrow()
        repo.refreshLatest(7).getOrThrow()
        // While the conversation is closed 200 messages arrive; only the newest reaches the cache live.
        serve(newest = 3_200)
        hubDelivers(3_200)
        repo.refreshLatest(7).getOrThrow()
        assertEquals(
            listOf(TimelineGap(1_130, 2_951), TimelineGap(3_000, 3_151)),
            graph.cache.document("timeline:7", TimelineMeta.serializer())?.gaps,
            "the new hole runs from the last confirmed message to the new tail",
        )
        assertContiguous(shown(), 3_151, 3_200)
    }

    @Test
    fun coverSplitsShrinksAndRemovesGaps() {
        val gap = TimelineGap(10, 100)
        assertEquals(listOf(gap), TimelineGaps.cover(listOf(gap), 100, 200), "touching the edge covers nothing")
        assertEquals(listOf(TimelineGap(10, 40), TimelineGap(60, 100)), TimelineGaps.cover(listOf(gap), 40, 60))
        assertEquals(listOf(TimelineGap(60, 100)), TimelineGaps.cover(listOf(gap), 5, 60))
        assertEquals(emptyList(), TimelineGaps.cover(listOf(gap), Long.MIN_VALUE, Long.MAX_VALUE))
        assertEquals(listOf(TimelineGap(10, 120)), TimelineGaps.add(listOf(gap), TimelineGap(50, 120)))
        assertNull(TimelineGaps.windowFloor(listOf(TimelineGap(300, Long.MAX_VALUE)), listOf(1L, 300L)))
        assertEquals(100, TimelineGaps.windowFloor(listOf(gap), listOf(5L, 10L, 100L, 150L)))
    }
}
