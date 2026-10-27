package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.EnglishConversationTitles
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestSessionRepository
import com.waivera.waivera.feature.today.model.ActivityFilter
import com.waivera.waivera.feature.today.model.TodayUiState
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TodayViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val session = TestSessionRepository(
        SessionState.SignedIn(CurrentUser(userId = ME, displayName = "Alex Rivera", timeZoneId = "America/Chicago")),
    )
    private val conversations = FakeConversationRepository()
    private val activity = FakeActivityRepository()
    private val calendar = FakeCalendarRepository()
    private val realtime = FakeRealtimeStatusRepository()
    private val now = TestNowSource()

    private fun TestScope.start(): Pair<TodayViewModel, MutableList<TodayEffect>> {
        val viewModel =
            TodayViewModel(session, conversations, activity, calendar, realtime, now, EnglishConversationTitles)
        val effects = mutableListOf<TodayEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.toList(effects) }
        return viewModel to effects
    }

    private val TodayViewModel.ui: TodayUiState get() = state.value

    @Test
    fun loading_nothingCachedShowsTheInitialLoad() = runTest(main.testDispatcher) {
        conversations.state.value = Synced(emptyList(), SyncStatus.Refreshing, null)
        activity.state.value = Synced(ActivityFeed(), SyncStatus.Refreshing, null)
        calendar.scheduleSyncedAt = null
        val (viewModel, _) = start()
        assertTrue(viewModel.ui.isInitialLoad)
        assertFalse(viewModel.ui.isAllCaughtUp)
        assertEquals("Alex Rivera", viewModel.ui.accountName)
    }

    @Test
    fun empty_everythingSyncedAndNothingPendingIsAllCaughtUp() = runTest(main.testDispatcher) {
        val (viewModel, _) = start()
        assertFalse(viewModel.ui.isInitialLoad)
        assertTrue(viewModel.ui.isAllCaughtUp)
        assertTrue(viewModel.ui.nextUp.isReady)
        assertTrue(viewModel.ui.nextUp.items.isEmpty())
        assertTrue(viewModel.ui.activity.isEmpty)
    }

    @Test
    fun error_nothingCachedAndEverySourceFailedShowsTheErrorState() = runTest(main.testDispatcher) {
        val error = WaiveraError.Network()
        conversations.state.value = Synced(emptyList(), SyncStatus.Failed(error), null)
        activity.state.value = Synced(ActivityFeed(), SyncStatus.Failed(error), null)
        calendar.scheduleStatus = SyncStatus.Failed(error)
        calendar.scheduleSyncedAt = null
        val (viewModel, _) = start()
        assertEquals(error, viewModel.ui.loadError)
    }

    @Test
    fun error_aFailedRefreshKeepsCachedContentAndSaysSo() = runTest(main.testDispatcher) {
        conversations.state.value = Synced(listOf(conversation(7, unread = 2)), lastSyncedAt = TEST_NOW)
        conversations.refreshResult = Result.failure(WaiveraError.Network())
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.Refresh)
        assertFalse(viewModel.ui.isRefreshing)
        assertEquals(1, viewModel.ui.needsYou.items.size)
        assertIs<TodayEffect.RefreshFailed>(effects.single())
    }

    @Test
    fun refresh_refreshesEverySourceIncludingTheScheduleRangeInTheSavedZone() = runTest(main.testDispatcher) {
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.Refresh)
        assertEquals(1, conversations.refreshes)
        assertEquals(1, activity.refreshes)
        assertEquals(1, calendar.milestoneRefreshes)
        assertEquals(LocalDate.of(2026, 11, 1), calendar.refreshedRanges.single().start)
    }

    @Test
    fun open_staleSourcesRefreshOnOpenAndFreshOnesDoNot() = runTest(main.testDispatcher) {
        activity.state.value = Synced(ActivityFeed(), lastSyncedAt = TEST_NOW.minusSeconds(600))
        start()
        assertEquals(0, conversations.refreshes)
        assertEquals(1, activity.refreshes)
    }

    @Test
    fun reconnect_bannerShowsWhileReconnectingAndTheDataLayerOwnsTheResync() = runTest(main.testDispatcher) {
        val (viewModel, _) = start()
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Reconnecting(2, 4_000), isOnline = true)
        assertEquals(ConnectionBannerState.Reconnecting, viewModel.ui.connection)
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connected, isOnline = true)
        assertEquals(ConnectionBannerState.Connected, viewModel.ui.connection)
        // Core RealtimeSync refetches activity and the open schedule ranges after a reconnect.
        assertEquals(0, activity.refreshes, "the screen does not refetch on top of the data layer")
        assertEquals(0, calendar.refreshedRanges.size)
    }

    @Test
    fun reconnect_offlineShowsTheOfflineBannerAndRetrySkipsTheWait() = runTest(main.testDispatcher) {
        val (viewModel, _) = start()
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Reconnecting(3, 8_000), isOnline = false)
        assertEquals(ConnectionBannerState.Offline, viewModel.ui.connection)
        viewModel.onAction(TodayAction.RetryConnection)
        assertEquals(1, realtime.retries)
    }

    @Test
    fun markRead_tappingAnUnreadRowMarksItReadAndDropsTheCount() = runTest(main.testDispatcher) {
        activity.state.value = Synced(ActivityFeed(listOf(activity(1), activity(2)), false), lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        assertEquals(2, viewModel.ui.activity.unreadCount)
        viewModel.onAction(TodayAction.MarkActivityRead(1))
        assertEquals(listOf(1L), activity.markReadCalls)
        assertEquals(1, viewModel.ui.activity.unreadCount)
        viewModel.onAction(TodayAction.MarkActivityRead(1))
        assertEquals(listOf(1L), activity.markReadCalls, "an already read row is not sent again")
    }

    @Test
    fun markRead_aFailureReturnsTheRowToUnreadWithRetry() = runTest(main.testDispatcher) {
        activity.state.value = Synced(ActivityFeed(listOf(activity(1)), false), lastSyncedAt = TEST_NOW)
        activity.markReadResult = Result.failure(WaiveraError.Network())
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.MarkActivityRead(1))
        assertFalse(viewModel.ui.activity.rows.single().read)
        assertEquals(1, viewModel.ui.activity.unreadCount)
        assertEquals(TodayEffect.MarkReadFailed(1), effects.single())
        assertTrue(viewModel.ui.activity.isReady, "the list stays visible")

        activity.markReadResult = Result.success(Unit)
        viewModel.onAction(TodayAction.MarkActivityRead(1))
        assertTrue(viewModel.ui.activity.rows.single().read)
        assertEquals(0, viewModel.ui.activity.unreadCount)
    }

    @Test
    fun markAll_everyLoadedActivityBecomesReadAndAFailureRestoresThem() = runTest(main.testDispatcher) {
        val items = listOf(activity(1), activity(2), activity(3, read = true))
        activity.state.value = Synced(ActivityFeed(items, false), lastSyncedAt = TEST_NOW)
        activity.markAllResult = Result.failure(WaiveraError.Network())
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.MarkAllActivityRead)
        assertEquals(2, viewModel.ui.activity.unreadCount)
        assertEquals(TodayEffect.MarkAllReadFailed, effects.single())

        activity.markAllResult = Result.success(Unit)
        viewModel.onAction(TodayAction.MarkAllActivityRead)
        assertEquals(0, viewModel.ui.activity.unreadCount)
        assertEquals(TodayEffect.MarkedAllRead, effects.last())
    }

    @Test
    fun filters_unreadOnlyEmptiesWhenTheLastUnreadItemIsOpened() = runTest(main.testDispatcher) {
        val items = listOf(activity(1), activity(2, read = true))
        activity.state.value = Synced(ActivityFeed(items, false), lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.ToggleUnreadOnly)
        assertEquals(listOf(1L), viewModel.ui.activity.rows.map { it.id })
        viewModel.onAction(TodayAction.MarkActivityRead(1))
        assertTrue(viewModel.ui.activity.isFilteredEmpty)
        viewModel.onAction(TodayAction.SetActivityFilter(ActivityFilter.REACTIONS))
        viewModel.onAction(TodayAction.ToggleUnreadOnly)
        assertTrue(viewModel.ui.activity.isFilteredEmpty)
    }

    @Test
    fun filters_showAllClearsTheTypeAndUnread() = runTest(main.testDispatcher) {
        val items = listOf(activity(1), activity(2, read = true))
        activity.state.value = Synced(ActivityFeed(items, false), lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.SetActivityFilter(ActivityFilter.REACTIONS))
        viewModel.onAction(TodayAction.ToggleUnreadOnly)
        assertTrue(viewModel.ui.activity.isFilteredEmpty)
        viewModel.onAction(TodayAction.ShowAllActivity)
        assertEquals(ActivityFilter.ALL, viewModel.ui.activity.filter)
        assertFalse(viewModel.ui.activity.unreadOnly)
        assertEquals(listOf(1L, 2L), viewModel.ui.activity.rows.map { it.id }.sorted())
    }

    @Test
    fun realtime_aNewActivityAppearsAtTheTopWithoutARefetch() = runTest(main.testDispatcher) {
        activity.state.value = Synced(ActivityFeed(listOf(activity(2, read = true)), false), lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        activity.receive(activity(9, type = ChatActivityType.REACTION, createdAt = TEST_NOW, reaction = "🎉"))
        assertEquals(listOf(9L, 2L), viewModel.ui.activity.rows.map { it.id })
        assertEquals(0, activity.refreshes)
    }

    @Test
    fun paging_aFullPageWithUnreadAtItsEndLoadsTheNextPage() = runTest(main.testDispatcher) {
        val page1 = (1L..30L).map { activity(it, read = it < 30L) }
        activity.state.value = Synced(ActivityFeed(page1, hasMore = true), lastSyncedAt = TEST_NOW)
        activity.pages += (31L..45L).map { activity(it, read = true) }
        val (viewModel, _) = start()
        assertEquals(1, activity.loadMoreCalls)
        assertFalse(viewModel.ui.activity.hasMore)
        assertEquals(1, viewModel.ui.activity.unreadCount)
    }

    @Test
    fun paging_aShortPageMakesNoFurtherRequest() = runTest(main.testDispatcher) {
        val page = (1L..29L).map { activity(it) }
        activity.state.value = Synced(ActivityFeed(page, hasMore = false), lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        assertEquals(0, activity.loadMoreCalls)
        assertFalse(viewModel.ui.activity.hasMore, "the list never asks for more")
        assertEquals(29, viewModel.ui.activity.unreadCount)
    }

    @Test
    fun paging_stopsAtTheCapAndShowsTheCountAsALowerBound() = runTest(main.testDispatcher) {
        val unreadPage = { from: Long -> (from until from + 30).map { activity(it) } }
        activity.state.value = Synced(ActivityFeed(unreadPage(1), hasMore = true), lastSyncedAt = TEST_NOW)
        repeat(10) { activity.pages += unreadPage(31L + it * 30) }
        val (viewModel, _) = start()
        assertEquals(ActivityPager.MAX_AUTO_PAGES, activity.loadMoreCalls)
        assertTrue(viewModel.ui.activity.unreadIsLowerBound)
        assertEquals(30 * (ActivityPager.MAX_AUTO_PAGES + 1), viewModel.ui.activity.unreadCount)
    }

    @Test
    fun invitation_answeringHidesItAtOnceAndAFailureBringsItBack() = runTest(main.testDispatcher) {
        val invite = timedEvent("invite", "2026-11-02T15:00:00Z", "2026-11-02T16:00:00Z", MyInvitation.PENDING)
        calendar.feedFor.value = { range -> ScheduleFeed(range, TEST_ZONE.id, listOf(invite)) }
        calendar.respondResult = Result.failure(WaiveraError.Network())
        val gate = CompletableDeferred<Unit>()
        calendar.respondGate = gate
        val (viewModel, effects) = start()
        assertEquals(listOf("invite-invite"), viewModel.ui.needsYou.items.map { it.key })

        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = true))
        assertTrue(viewModel.ui.needsYou.items.isEmpty(), "optimistic")
        gate.complete(Unit)
        assertEquals(listOf("invite-invite"), viewModel.ui.needsYou.items.map { it.key })
        assertEquals(TodayEffect.InvitationFailed("invite", accept = true), effects.single())
    }

    @Test
    fun invitation_aSuccessfulAnswerConfirms() = runTest(main.testDispatcher) {
        val invite = timedEvent("invite", "2026-11-02T15:00:00Z", "2026-11-02T16:00:00Z", MyInvitation.PENDING)
        calendar.feedFor.value = { range -> ScheduleFeed(range, TEST_ZONE.id, listOf(invite)) }
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = false))
        assertEquals(listOf("invite" to false), calendar.responses)
        assertTrue(viewModel.ui.needsYou.items.isEmpty())
        assertEquals(TodayEffect.InvitationAnswered(accept = false), effects.single())
    }

    @Test
    fun time_relativeLabelsTickWithTheClock() = runTest(main.testDispatcher) {
        val visit = timedEvent("visit", "2026-11-01T16:55:00Z", "2026-11-01T18:00:00Z")
        calendar.feedFor.value = { range -> ScheduleFeed(range, TEST_ZONE.id, listOf(visit)) }
        val (viewModel, _) = start()
        assertEquals(UpcomingLabel.InMinutes(25), viewModel.ui.nextUp.items.single().time)
        now.clock.value = TEST_NOW.plusSeconds(600)
        assertEquals(UpcomingLabel.InMinutes(15), viewModel.ui.nextUp.items.single().time)
    }

    @Test
    fun time_theScheduleRangeStartsOnTheEmployeesDateNotUtc() = runTest(main.testDispatcher) {
        now.clock.value = Instant.parse("2026-11-02T03:00:00Z") // 21:00 CST on 1 November
        start()
        assertEquals(LocalDate.of(2026, 11, 1), calendar.observedRanges.last().start)
        assertEquals(LocalDate.of(2026, 11, 15), calendar.observedRanges.last().endExclusive)
    }
}
