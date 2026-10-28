package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.Bookmark
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class BookmarkRulesTest {
    private fun message(
        id: Long,
        createdAt: String? = null,
        text: String? = "hello",
        parent: Long? = null,
        root: Long? = null,
        conversationId: Long = 7,
        attachment: Attachment? = null,
        replies: Int = 0,
    ) = Message(
        id = id,
        conversationId = conversationId,
        author = Person("u1", displayName = "Priya Raman"),
        text = text,
        createdAt = WaiveraTime.parseInstant(createdAt),
        parentMessageId = parent,
        threadRootMessageId = root,
        attachment = attachment,
        threadReplyCount = replies,
    )

    @Test
    fun aNestedReplyOpensTheThreadRootNotItsParent() {
        assertEquals(100L, BookmarkRules.threadRoot(message(id = 300, parent = 200, root = 100)))
    }

    @Test
    fun aReplyWithoutARootFallsBackToItsParent() {
        assertEquals(200L, BookmarkRules.threadRoot(message(id = 300, parent = 200)))
    }

    @Test
    fun aRowCarriesItsAuthorsIdForTheirPhoto() {
        assertEquals("u1", BookmarkRules.row(message(id = 100)).authorId)
        assertEquals("u9", BookmarkRules.row(message(id = 101).copy(authorId = "u9")).authorId)
    }

    @Test
    fun aRootMessageOpensTheConversation() {
        assertNull(BookmarkRules.threadRoot(message(id = 100)))
        assertNull(BookmarkRules.threadRoot(message(id = 100, root = 100)))
    }

    @Test
    fun zoneLessTimestampsOrderAsUtcAgainstZonedOnes() {
        // 14:30 UTC (zone-less) is later than 09:00-05:00 (14:00 UTC), though "09" < "14" as local times.
        val zoneLess = message(id = 1, createdAt = "2026-10-20T14:30:00")
        val zoned = message(id = 2, createdAt = "2026-10-20T09:00:00-05:00")
        val rows = BookmarkRules.rows(listOf(Bookmark(zoned), Bookmark(zoneLess)))
        assertEquals(listOf(1L, 2L), rows.map { it.messageId })
    }

    @Test
    fun rowsNeedAPositiveConversationId() {
        val rows = BookmarkRules.rows(listOf(Bookmark(message(1, conversationId = 0)), Bookmark(message(2))))
        assertEquals(listOf(2L), rows.map { it.messageId })
    }

    @Test
    fun imageOnlyBookmarksSayPhotoNotFile() {
        val photo = message(1, text = null, attachment = Attachment(url = "/a/1", contentType = "image/jpeg"))
        val pdf = message(
            id = 2,
            text = " ",
            attachment = Attachment(url = "/a/2", originalName = "roster.pdf", contentType = "application/pdf"),
        )
        assertEquals(BookmarkPreview.Photo, BookmarkRules.preview(photo))
        assertEquals(BookmarkPreview.File("roster.pdf"), BookmarkRules.preview(pdf))
        assertEquals(BookmarkPreview.Saved, BookmarkRules.preview(message(3, text = null)))
        assertEquals(BookmarkPreview.Text("two words"), BookmarkRules.preview(message(4, text = "  two \n words ")))
    }

    @Test
    fun searchingThreadSurfacesEveryThreadReply() {
        val words = BookmarkSearchWords()
        val reply = BookmarkRules.row(message(300, text = "see you there", root = 100))
        val root = BookmarkRules.row(message(100, text = "who is on call?"))
        assertTrue(BookmarkRules.matches(reply, "thread", words))
        assertFalse(BookmarkRules.matches(root, "thread", words))
        assertTrue(BookmarkRules.matches(root, "PRIYA", words))
        assertTrue(BookmarkRules.matches(root, "on call", words))
    }

    @Test
    fun aMessageWithRepliesIsAThreadRootAndAReplyNeverIs() {
        val root = BookmarkRules.row(message(100, replies = 2))
        val reply = BookmarkRules.row(message(300, root = 100, replies = 1))
        val plain = BookmarkRules.row(message(200))
        assertTrue(root.isThreadRoot)
        assertTrue(root.isInThread)
        assertNull(root.threadRootId, "a root still opens in the conversation")
        assertFalse(reply.isThreadRoot)
        assertTrue(reply.isInThread)
        assertFalse(plain.isInThread)
    }

    @Test
    fun filtersSplitMessagesFromThreads() {
        val root = BookmarkRules.row(message(100, replies = 2))
        val reply = BookmarkRules.row(message(300, root = 100))
        val plain = BookmarkRules.row(message(200))
        val rows = listOf(root, reply, plain)
        fun ids(filter: BookmarkFilter) = rows.filter { BookmarkRules.matches(it, filter) }.map { it.messageId }
        assertEquals(listOf(100L, 300L, 200L), ids(BookmarkFilter.All))
        assertEquals(listOf(200L), ids(BookmarkFilter.Messages))
        assertEquals(listOf(100L, 300L), ids(BookmarkFilter.Threads))
    }

    @Test
    fun searchingThreadAlsoFindsThreadRoots() {
        val words = BookmarkSearchWords()
        assertTrue(BookmarkRules.matches(BookmarkRules.row(message(100, replies = 2)), "thread", words))
    }

    @Test
    fun aSearchMissOutranksAnEmptyChip() {
        assertEquals(BookmarksEmptyReason.NoBookmarks, BookmarksUiState(filter = BookmarkFilter.Threads).emptyReason)
        val threads = BookmarksUiState(filter = BookmarkFilter.Threads, totalCount = 2)
        assertEquals(BookmarksEmptyReason.NoneInFilter, threads.emptyReason)
        assertEquals(BookmarksEmptyReason.NoMatch, threads.copy(query = "zz").emptyReason)
        assertFalse(BookmarksUiState(query = " ").isFiltered)
        assertTrue(threads.isFiltered)
    }
}

class BookmarksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val realtime = FakeRealtimeStatus()

    /** Counts refreshes and can fail toggles; reads and writes the demo backend otherwise. */
    private class CountingBookmarks(private val delegate: BookmarkRepository) : BookmarkRepository by delegate {
        var refreshes = 0
        var failToggles: WaiveraError? = null

        override suspend fun refresh(): Result<Unit> {
            refreshes++
            return delegate.refresh()
        }

        override suspend fun toggle(messageId: Long): Result<Boolean> =
            failToggles?.let { Result.failure(it) } ?: delegate.toggle(messageId)
    }

    private fun TestScope.viewModel(
        graph: DemoGraph,
        bookmarks: BookmarkRepository = graph.bookmarks,
    ): BookmarksViewModel {
        val vm = BookmarksViewModel(bookmarks, realtime, graph.calendar, TestClock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun CoroutineScope.nextEffect(vm: BookmarksViewModel) =
        async(start = CoroutineStart.UNDISPATCHED) { vm.effect.first() }

    @Test
    fun populatedNewestFirstWithOneRequest() = runTest {
        val graph = DemoGraph(backgroundScope)
        val counting = CountingBookmarks(graph.bookmarks)
        val vm = viewModel(graph, counting)

        val state = vm.uiState.value
        assertEquals(ListPhase.Content, state.phase)
        assertEquals(DemoData.bookmarkedIds(), state.rows.map { it.messageId }.toSet())
        assertEquals(state.rows.sortedByDescending { it.createdAt }, state.rows)
        assertEquals(1, counting.refreshes)
    }

    @Test
    fun aBookmarkedThreadReplyOpensItsRoot() = runTest {
        val graph = DemoGraph(backgroundScope)
        val reply = graph.backend.messages.value.values.flatten().first { it.isThreadReply }
        graph.backend.bookmarks.update { it + reply.id }

        val vm = viewModel(graph)

        val row = vm.uiState.value.rows.single { it.messageId == reply.id }
        assertEquals(reply.threadRootMessageId, row.threadRootId)
        assertTrue(row.isThreadReply)
        assertNull(vm.uiState.value.rows.single { it.messageId == DemoData.THREAD_ROOT }.threadRootId)
    }

    @Test
    fun loadingThenEmpty() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.bookmarks.value = emptySet()
        graph.backend.syncStatus.value = SyncStatus.Refreshing
        val vm = viewModel(graph)
        assertEquals(ListPhase.Loading, vm.uiState.value.phase)

        graph.backend.syncStatus.value = SyncStatus.Idle
        assertEquals(ListPhase.Empty, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.totalCount)
        assertEquals(BookmarksEmptyReason.NoBookmarks, vm.uiState.value.emptyReason)
    }

    @Test
    fun chipsSplitTheSavedMessagesAndKeepTheTotal() = runTest {
        val vm = viewModel(DemoGraph(backgroundScope))
        val total = DemoData.bookmarkedIds().size

        vm.onFilterChange(BookmarkFilter.Threads)
        assertEquals(listOf(DemoData.THREAD_ROOT), vm.uiState.value.rows.map { it.messageId })
        assertEquals(total, vm.uiState.value.totalCount)

        vm.onFilterChange(BookmarkFilter.Messages)
        assertEquals(listOf(9320L), vm.uiState.value.rows.map { it.messageId })

        vm.onFilterChange(BookmarkFilter.All)
        assertEquals(DemoData.bookmarkedIds(), vm.uiState.value.rows.map { it.messageId }.toSet())
        assertFalse(vm.uiState.value.isFiltered)
    }

    @Test
    fun anEmptyChipSaysSoAndAllBringsTheListBack() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.bookmarks.value = setOf(9320L)
        val vm = viewModel(graph)

        vm.onFilterChange(BookmarkFilter.Threads)

        assertEquals(ListPhase.Empty, vm.uiState.value.phase)
        assertEquals(BookmarksEmptyReason.NoneInFilter, vm.uiState.value.emptyReason)

        vm.onFilterChange(BookmarkFilter.All)

        assertEquals(ListPhase.Content, vm.uiState.value.phase)
    }

    @Test
    fun searchNarrowsInsideTheChosenChip() = runTest {
        val vm = viewModel(DemoGraph(backgroundScope))
        val root = vm.uiState.value.rows.single { it.messageId == DemoData.THREAD_ROOT }
        val author = root.author.substringBefore(' ')

        vm.onFilterChange(BookmarkFilter.Threads)
        vm.onQueryChange(author)
        assertEquals(listOf(DemoData.THREAD_ROOT), vm.uiState.value.rows.map { it.messageId })

        vm.onQueryChange("zzzz")
        assertEquals(ListPhase.Empty, vm.uiState.value.phase)
        assertEquals(BookmarksEmptyReason.NoMatch, vm.uiState.value.emptyReason)
    }

    @Test
    fun aThreadReplyBookmarkedElsewhereJoinsTheThreadsChip() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel(graph)
        vm.onFilterChange(BookmarkFilter.Threads)
        val reply = graph.backend.messages.value.values.flatten().first { it.isThreadReply }

        graph.bookmarks.toggle(reply.id)

        assertTrue(vm.uiState.value.rows.any { it.messageId == reply.id })
        assertTrue(vm.uiState.value.rows.all { it.isInThread })
    }

    @Test
    fun anErrorWithNothingCachedIgnoresTheChip() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.backend.bookmarks.value = emptySet()
        graph.backend.failRefreshes = WaiveraError.Server(500)
        val vm = viewModel(graph)

        vm.onFilterChange(BookmarkFilter.Threads)

        assertEquals(ListPhase.Error, vm.uiState.value.phase)
    }

    @Test
    fun searchWithNoMatchKeepsTheTotal() = runTest {
        val vm = viewModel(DemoGraph(backgroundScope))

        vm.onQueryChange("zzzz")

        assertEquals(ListPhase.Empty, vm.uiState.value.phase)
        assertEquals(DemoData.bookmarkedIds().size, vm.uiState.value.totalCount)
    }

    @Test
    fun errorWithNothingCachedShowsTheErrorAndRetryRecovers() = runTest {
        val graph = DemoGraph(backgroundScope)
        val saved = graph.backend.bookmarks.value
        graph.backend.bookmarks.value = emptySet()
        graph.backend.failRefreshes = WaiveraError.Server(500)
        val vm = viewModel(graph)
        assertEquals(ListPhase.Error, vm.uiState.value.phase)

        graph.backend.failRefreshes = null
        graph.backend.bookmarks.value = saved
        vm.refresh()

        assertEquals(ListPhase.Content, vm.uiState.value.phase)
    }

    @Test
    fun removalIsOptimisticWithUndo() = runTest {
        val graph = DemoGraph(backgroundScope)
        val vm = viewModel(graph)
        val effect = backgroundScope.nextEffect(vm)

        vm.remove(DemoData.THREAD_ROOT)

        assertFalse(vm.uiState.value.rows.any { it.messageId == DemoData.THREAD_ROOT })
        assertEquals(BookmarksEffect.Removed(DemoData.THREAD_ROOT), effect.await())
        assertFalse(DemoData.THREAD_ROOT in graph.backend.bookmarks.value)

        vm.undoRemove(DemoData.THREAD_ROOT)

        assertTrue(vm.uiState.value.rows.any { it.messageId == DemoData.THREAD_ROOT })
    }

    @Test
    fun aFailedRemovalPutsTheRowBack() = runTest {
        val graph = DemoGraph(backgroundScope)
        val failing = CountingBookmarks(graph.bookmarks).apply { failToggles = WaiveraError.Network() }
        val vm = viewModel(graph, failing)
        val effect = backgroundScope.nextEffect(vm)

        vm.remove(DemoData.THREAD_ROOT)

        assertIs<BookmarksEffect.RemoveFailed>(effect.await())
        assertTrue(vm.uiState.value.rows.any { it.messageId == DemoData.THREAD_ROOT })
    }

    @Test
    fun removalsElsewhereShowWithoutARefresh() = runTest {
        val graph = DemoGraph(backgroundScope)
        val counting = CountingBookmarks(graph.bookmarks)
        val vm = viewModel(graph, counting)

        // The conversation screen (or ReceiveBookmarkUpdate) toggles through the same repository.
        graph.bookmarks.toggle(9320L)

        assertFalse(vm.uiState.value.rows.any { it.messageId == 9320L })
        assertEquals(1, counting.refreshes)
    }

    @Test
    fun reconnectRefetches() = runTest {
        val graph = DemoGraph(backgroundScope)
        val counting = CountingBookmarks(graph.bookmarks)
        viewModel(graph, counting)

        realtime.set(ConnectionState.Reconnecting(1, 1_000))
        realtime.set(ConnectionState.Connected)

        assertEquals(2, counting.refreshes)
    }

    @Test
    fun aReconnectKeepsTheChipAndTheSearch() = runTest {
        val graph = DemoGraph(backgroundScope)
        val counting = CountingBookmarks(graph.bookmarks)
        val vm = viewModel(graph, counting)
        vm.onFilterChange(BookmarkFilter.Threads)
        vm.onQueryChange("on call")

        realtime.set(ConnectionState.Reconnecting(1, 1_000))
        realtime.set(ConnectionState.Connected)

        assertEquals(2, counting.refreshes)
        assertEquals(BookmarkFilter.Threads, vm.uiState.value.filter)
        assertEquals("on call", vm.uiState.value.query)
        assertTrue(vm.uiState.value.rows.all { it.isInThread })
    }

    @Test
    fun datesReadLikeTheInboxInTheSavedZoneAcrossTheFallBack() {
        val chicago = ZoneId.of("America/Chicago")
        // Sunday 1 Nov 2026 10:30 CST, the day the clocks fell back in Chicago.
        val now = Instant.parse("2026-11-01T16:30:00Z")
        val earlyToday = Instant.parse("2026-11-01T05:30:00Z") // 00:30 CDT, still today
        assertEquals(BookmarkDate.Today(earlyToday.atZone(chicago)), BookmarkDate.of(earlyToday, now, chicago))
        assertEquals(BookmarkDate.Yesterday, BookmarkDate.of(Instant.parse("2026-11-01T04:30:00Z"), now, chicago))
        assertEquals(
            BookmarkDate.OnDate(LocalDate.of(2026, 10, 20), showYear = false),
            BookmarkDate.of(Instant.parse("2026-10-20T14:30:00Z"), now, chicago),
        )
        assertEquals(
            BookmarkDate.OnDate(LocalDate.of(2025, 12, 31), showYear = true),
            BookmarkDate.of(Instant.parse("2026-01-01T03:00:00Z"), now, chicago),
        )
    }
}
