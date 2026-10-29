package com.waivera.waivera.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import com.waivera.waivera.core.testing.InMemoryHandledTodayRepository
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestSessionRepository
import com.waivera.waivera.feature.today.model.ActivityFilter
import com.waivera.waivera.feature.today.model.DayPart
import com.waivera.waivera.feature.today.model.TodayProgress
import com.waivera.waivera.feature.today.model.TodayStats
import com.waivera.waivera.feature.today.model.TodayUiState
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
    private val handled = InMemoryHandledTodayRepository()
    private val window = InvitationAnswers.UNDO_WINDOW_MILLIS

    // Owning the ViewModel through a store lets a test clear it the way navigation does.
    private val viewModels = ViewModelStore()

    private fun TestScope.start(): Pair<TodayViewModel, MutableList<TodayEffect>> {
        val created = TodayViewModel(
            session,
            conversations,
            activity,
            calendar,
            realtime,
            handled,
            now,
            EnglishConversationTitles,
            backgroundScope,
        )
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = created as T
        }
        val viewModel = ViewModelProvider(viewModels, factory)[TodayViewModel::class.java]
        val effects = mutableListOf<TodayEffect>()
        backgroundScope.launch { viewModel.state.collect {} }
        backgroundScope.launch { viewModel.effects.toList(effects) }
        return viewModel to effects
    }

    private val TodayViewModel.ui: TodayUiState get() = state.value

    @Test
    fun greeting_usesTheFirstWordOfTheDisplayNameInTheEmployeesZone() = runTest(main.testDispatcher) {
        val (viewModel, _) = start()
        val greeting = viewModel.ui.greeting
        assertEquals("Alex", greeting?.firstName)
        // 16:30 UTC on 1 November is 10:30 in Chicago (CST, the day DST ends): morning.
        assertEquals(DayPart.MORNING, greeting?.part)
    }

    @Test
    fun sendWishes_opensTheDirectConversation() = runTest(main.testDispatcher) {
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.SendWishes("usr_jordan_lee"))
        runCurrent()
        assertEquals(listOf(listOf("usr_jordan_lee")), conversations.opened)
        assertEquals(TodayEffect.OpenConversation(OPENED_CONVERSATION), effects.last())
    }

    @Test
    fun sendWishes_showsTheButtonBusyUntilTheChatOpens() = runTest(main.testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        conversations.openGate = gate
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.SendWishes("usr_jordan_lee"))
        runCurrent()
        assertEquals("usr_jordan_lee", viewModel.ui.openingWishesFor, "busy from the tap, before the network answers")
        viewModel.onAction(TodayAction.SendWishes("usr_jordan_lee"))
        runCurrent()
        assertEquals(1, conversations.opened.size, "a second tap while opening is ignored")
        gate.complete(Unit)
        runCurrent()
        assertNull(viewModel.ui.openingWishesFor)
        assertEquals(TodayEffect.OpenConversation(OPENED_CONVERSATION), effects.last())
    }

    @Test
    fun sendWishes_failureClearsTheBusyButton() = runTest(main.testDispatcher) {
        conversations.openResult = Result.failure(WaiveraError.Network())
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.SendWishes("usr_jordan_lee"))
        runCurrent()
        assertNull(viewModel.ui.openingWishesFor)
    }

    @Test
    fun sendWishes_failureOffersRetry() = runTest(main.testDispatcher) {
        conversations.openResult = Result.failure(WaiveraError.Network())
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.SendWishes("usr_jordan_lee"))
        runCurrent()
        assertEquals(TodayEffect.WishesFailed("usr_jordan_lee"), effects.last())
    }

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
        givenInvitation()
        calendar.respondResult = Result.failure(WaiveraError.Network())
        val gate = CompletableDeferred<Unit>()
        calendar.respondGate = gate
        val (viewModel, effects) = start()
        assertEquals(listOf("invite-invite"), viewModel.ui.needsYou.items.map { it.key })

        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = true))
        assertTrue(viewModel.ui.needsYou.items.isEmpty(), "optimistic")
        advanceTimeBy(window + 1)
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("invite-invite"), viewModel.ui.needsYou.items.map { it.key })
        val offer = TodayEffect.OfferUndo("invite", accept = true, token = 1)
        assertEquals(listOf(offer, TodayEffect.InvitationFailed("invite", accept = true)), effects)
    }

    @Test
    fun invitation_anAnswerIsSentOnlyWhenTheUndoWindowCloses() = runTest(main.testDispatcher) {
        givenInvitation()
        val (viewModel, effects) = start()
        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = false))
        assertTrue(viewModel.ui.needsYou.items.isEmpty())
        assertEquals(listOf<TodayEffect>(TodayEffect.OfferUndo("invite", accept = false, token = 1)), effects)

        advanceTimeBy(window - 1)
        assertTrue(calendar.responses.isEmpty(), "still inside the undo window")
        advanceTimeBy(2)
        assertEquals(listOf("invite" to false), calendar.responses)
        assertTrue(viewModel.ui.needsYou.items.isEmpty())
        assertEquals(1, effects.size, "the Undo snackbar already confirmed the answer")
    }

    @Test
    fun invitation_undoBringsItBackAndNothingIsSent() = runTest(main.testDispatcher) {
        givenInvitation()
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = true))
        viewModel.onAction(TodayAction.UndoInvitation("invite", token = 1))
        assertEquals(listOf("invite-invite"), viewModel.ui.needsYou.items.map { it.key })
        advanceTimeBy(window * 2)
        assertTrue(calendar.responses.isEmpty())
    }

    @Test
    fun invitation_closingTheSnackbarSendsAtOnceAndAStaleUndoIsIgnored() = runTest(main.testDispatcher) {
        givenInvitation()
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = true))
        viewModel.onAction(TodayAction.InvitationUndoClosed("invite", token = 1))
        runCurrent()
        assertEquals(listOf("invite" to true), calendar.responses)
        viewModel.onAction(TodayAction.UndoInvitation("invite", token = 1))
        assertTrue(viewModel.ui.needsYou.items.isEmpty(), "the answer was already sent")
        advanceTimeBy(window * 2)
        assertEquals(1, calendar.responses.size)
    }

    @Test
    fun invitation_leavingTodayDuringTheUndoWindowStillSendsTheAnswer() = runTest(main.testDispatcher) {
        givenInvitation()
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = true))
        viewModels.clear()
        runCurrent()
        assertEquals(listOf("invite" to true), calendar.responses)
        advanceTimeBy(window * 2)
        assertEquals(1, calendar.responses.size)
    }

    @Test
    fun invitation_retryAfterAFailureSendsStraightAway() = runTest(main.testDispatcher) {
        givenInvitation()
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.RetryInvitation("invite", accept = true))
        runCurrent()
        assertEquals(listOf("invite" to true), calendar.responses)
        assertTrue(viewModel.ui.needsYou.items.isEmpty())
    }

    private fun givenInvitation() {
        val invite = timedEvent("invite", "2026-11-02T15:00:00Z", "2026-11-02T16:00:00Z", MyInvitation.PENDING)
        calendar.feedFor.value = { range -> ScheduleFeed(range, TEST_ZONE.id, listOf(invite)) }
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

    @Test
    fun handled_aChatReadElsewhereCountsAndIsSaved() = runTest(main.testDispatcher) {
        val unread = listOf(conversation(7, unread = 2), conversation(8, unread = 1))
        conversations.state.value = Synced(unread, lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        assertEquals(TodayProgress(handled = 0, open = 2), viewModel.ui.progress)

        val oneRead = listOf(conversation(7), conversation(8, unread = 1))
        conversations.state.value = Synced(oneRead, lastSyncedAt = TEST_NOW)
        runCurrent()
        assertEquals(TodayProgress(handled = 1, open = 1), viewModel.ui.progress)
        assertEquals(1, handled.current?.handled, "the tally is saved, not only shown")
        assertEquals(LocalDate.of(2026, 11, 1), handled.current?.day)
    }

    @Test
    fun handled_anUndoneInvitationAnswerTakesItsCountBack() = runTest(main.testDispatcher) {
        givenInvitation()
        val (viewModel, _) = start()
        viewModel.onAction(TodayAction.RespondToInvitation("invite", accept = true))
        runCurrent()
        assertEquals(TodayProgress(handled = 1, open = 0), viewModel.ui.progress)
        viewModel.onAction(TodayAction.UndoInvitation("invite", token = 1))
        runCurrent()
        assertEquals(TodayProgress(handled = 0, open = 1), viewModel.ui.progress)
        assertEquals(0, handled.current?.handled)
    }

    @Test
    fun handled_nothingIsRecordedWhileASourceBehindNeedsYouIsLoading() = runTest(main.testDispatcher) {
        conversations.state.value = Synced(listOf(conversation(7, unread = 2)), lastSyncedAt = TEST_NOW)
        activity.state.value = Synced(ActivityFeed(), SyncStatus.Refreshing, null)
        val (viewModel, _) = start()
        runCurrent()
        assertNull(viewModel.ui.handledObservation)
        assertTrue(handled.recorded.isEmpty(), "a list missing its mentions must not read as handled")
        assertEquals(TodayProgress(handled = 0, open = 1), viewModel.ui.progress)
    }

    @Test
    fun handled_reconnectKeepsTheCount() = runTest(main.testDispatcher) {
        conversations.state.value = Synced(listOf(conversation(7, unread = 2)), lastSyncedAt = TEST_NOW)
        val (viewModel, _) = start()
        conversations.state.value = Synced(listOf(conversation(7)), lastSyncedAt = TEST_NOW)
        runCurrent()
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Reconnecting(1, 1_000), isOnline = true)
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connected, isOnline = true)
        runCurrent()
        assertEquals(TodayProgress(handled = 1, open = 0), viewModel.ui.progress)
    }

    @Test
    fun stats_countUnreadChatsAndTheEventsTodayAndThisWeek() = runTest(main.testDispatcher) {
        conversations.state.value = Synced(
            listOf(conversation(7, unread = 2), conversation(8), conversation(9, unread = 1)),
            lastSyncedAt = TEST_NOW,
        )
        val events = listOf(
            timedEvent("visit", "2026-11-01T16:55:00Z", "2026-11-01T18:00:00Z"),
            timedEvent("later", "2026-11-04T15:00:00Z", "2026-11-04T16:00:00Z"),
            timedEvent("next-week", "2026-11-09T15:00:00Z", "2026-11-09T16:00:00Z"),
            timedEvent("pending", "2026-11-01T20:00:00Z", "2026-11-01T21:00:00Z", MyInvitation.PENDING),
        )
        calendar.feedFor.value = { range -> ScheduleFeed(range, TEST_ZONE.id, events) }
        val (viewModel, _) = start()
        assertEquals(TodayStats(eventsToday = 1, unreadChats = 2, eventsThisWeek = 2), viewModel.ui.stats)
        assertEquals(1, viewModel.ui.day?.eventCount)
    }
}
