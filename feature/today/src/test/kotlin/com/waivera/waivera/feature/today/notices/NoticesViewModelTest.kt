package com.waivera.waivera.feature.today.notices

import com.waivera.waivera.core.data.model.NoticeFeed
import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.today.FakeCalendarRepository
import com.waivera.waivera.feature.today.FakeNoticeRepository
import com.waivera.waivera.feature.today.FakeRealtimeStatusRepository
import com.waivera.waivera.feature.today.TEST_LINKS
import com.waivera.waivera.feature.today.TEST_NOW
import com.waivera.waivera.feature.today.TestNowSource
import com.waivera.waivera.feature.today.notice
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class NoticesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val notices = FakeNoticeRepository(Synced(NoticeFeed()))
    private val realtime = FakeRealtimeStatusRepository()

    private fun TestScope.start(): Pair<NoticesViewModel, MutableList<NoticesEffect>> {
        val viewModel = NoticesViewModel(
            notices,
            FakeCalendarRepository(),
            realtime,
            TEST_LINKS,
            NoticeOpener(notices, TEST_LINKS),
            TestNowSource(),
        )
        val effects = mutableListOf<NoticesEffect>()
        backgroundScope.launch { viewModel.uiState.collect {} }
        backgroundScope.launch { viewModel.effects.toList(effects) }
        runCurrent()
        return viewModel to effects
    }

    @Test
    fun loading_untilTheFirstListArrives() = runTest(main.testDispatcher) {
        val (viewModel, _) = start()
        assertEquals(NoticesPhase.Loading, viewModel.uiState.value.phase)
        assertEquals(1, notices.refreshes)
    }

    @Test
    fun empty_afterASyncWithNothing() = runTest(main.testDispatcher) {
        val (viewModel, _) = start()
        notices.state.value = Synced(NoticeFeed(), lastSyncedAt = TEST_NOW)
        runCurrent()
        assertEquals(NoticesPhase.Empty, viewModel.uiState.value.phase)
        assertEquals(false, viewModel.uiState.value.isFilteredEmpty)
    }

    @Test
    fun error_whenTheFirstLoadFails_andTheBannerStandsDown() = runTest(main.testDispatcher) {
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connected, isOnline = false)
        val (viewModel, _) = start()
        notices.state.value = Synced(NoticeFeed(), SyncStatus.Failed(WaiveraError.Network()))
        runCurrent()
        assertEquals(NoticesPhase.Error, viewModel.uiState.value.phase)
        assertEquals(ConnectionBannerState.Connected, viewModel.uiState.value.connection)
    }

    @Test
    fun reconnecting_showsTheBannerOverTheSavedList() = runTest(main.testDispatcher) {
        notices.state.value = Synced(NoticeFeed(listOf(notice("a")), 1), lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Reconnecting(1, 2_000), isOnline = true)
        runCurrent()
        assertEquals(ConnectionBannerState.Reconnecting, viewModel.uiState.value.connection)
        assertEquals(NoticesPhase.Content, viewModel.uiState.value.phase)
        viewModel.retryConnection()
        assertEquals(1, realtime.retries)
    }

    @Test
    fun refreshFailure_keepsTheListAndSaysSo() = runTest(main.testDispatcher) {
        notices.state.value = Synced(
            NoticeFeed(listOf(notice("a")), 1),
            SyncStatus.Failed(WaiveraError.Network()),
            lastSyncedAt = TEST_NOW,
        )
        val (viewModel, _) = start()
        assertEquals(NoticesPhase.Content, viewModel.uiState.value.phase)
        assertTrue(viewModel.uiState.value.refreshFailed)
    }

    @Test
    fun filter_narrowsAndExplainsAnEmptyChip() = runTest(main.testDispatcher) {
        notices.state.value = Synced(
            NoticeFeed(listOf(notice("t", NoticeKind.TaskAssigned), notice("s", NoticeKind.Signature)), 2),
            lastSyncedAt = TEST_NOW,
        )
        val (viewModel, _) = start()
        viewModel.setFilter(NoticeFilter.Approvals)
        runCurrent()
        assertEquals(listOf("s"), viewModel.uiState.value.groups.flatMap { it.rows }.map { it.id })
        viewModel.setFilter(NoticeFilter.Meetings)
        runCurrent()
        assertTrue(viewModel.uiState.value.isFilteredEmpty)
    }

    @Test
    fun open_marksReadAndOpensTheWebPage() = runTest(main.testDispatcher) {
        notices.state.value = Synced(NoticeFeed(listOf(notice("t")), 1), lastSyncedAt = TEST_NOW)
        val (viewModel, effects) = start()
        viewModel.open("t")
        runCurrent()
        assertEquals(NoticesEffect.Open(NoticeDestination.Task(42)), effects.single())
        assertEquals(listOf("t"), notices.markedRead)
        assertEquals(0, viewModel.uiState.value.unreadCount)
    }

    @Test
    fun open_withNowhereToGoSaysSo() = runTest(main.testDispatcher) {
        notices.state.value =
            Synced(NoticeFeed(listOf(notice("x", NoticeKind.Other, taskId = null)), 1), lastSyncedAt = TEST_NOW)
        val (viewModel, effects) = start()
        viewModel.open("x")
        runCurrent()
        assertEquals(NoticesEffect.NothingToOpen, effects.single())
    }

    @Test
    fun markAllRead_failureOffersRetry() = runTest(main.testDispatcher) {
        notices.state.value = Synced(NoticeFeed(listOf(notice("a")), 1), lastSyncedAt = TEST_NOW)
        notices.markAllResult = Result.failure(WaiveraError.Network())
        val (viewModel, effects) = start()
        viewModel.markAllRead()
        runCurrent()
        assertEquals(NoticesEffect.MarkAllReadFailed, effects.single())
        assertEquals(false, viewModel.uiState.value.isMarkingAllRead)
    }
}
