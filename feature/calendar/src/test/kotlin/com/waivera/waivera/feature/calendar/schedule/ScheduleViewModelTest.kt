package com.waivera.waivera.feature.calendar.schedule

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.realtime.ConnectionState
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.core.testing.TestSessionRepository
import com.waivera.waivera.feature.calendar.FakeCalendarRepository
import com.waivera.waivera.feature.calendar.FakeConversationRepository
import com.waivera.waivera.feature.calendar.FakeRealtimeStatusRepository
import com.waivera.waivera.feature.calendar.data.DeviceZone
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.SchedulePeriods
import com.waivera.waivera.feature.calendar.model.ScheduleView
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class ScheduleViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val chicago = ZoneId.of("America/Chicago")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val clock = TestClock(Instant.parse("2026-11-01T16:30:00Z")) // 10:30 CST, fall-back day
    private val calendar = FakeCalendarRepository(clock)
    private val realtime = FakeRealtimeStatusRepository()
    private val session = TestSessionRepository()
    private val conversations = FakeConversationRepository()
    private var device: ZoneId = chicago

    private val shift = ScheduleEvent(
        id = "shift",
        title = "Morning shift",
        startAt = Instant.parse("2026-11-01T14:00:00Z"),
        endAt = Instant.parse("2026-11-01T22:00:00Z"),
    )

    private fun TestScope.viewModel(): ScheduleViewModel {
        val vm = ScheduleViewModel(
            calendar = calendar,
            session = session,
            realtime = realtime,
            clock = clock,
            deviceZone = DeviceZone { device },
            conversations = conversations,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun loadingUntilTheFirstRefreshLands() = runTest {
        calendar.events = listOf(shift)
        val gate = CompletableDeferred<Unit>()
        calendar.refreshGate = gate
        val vm = viewModel()
        assertEquals(AgendaState.Loading, vm.uiState.value.agenda)

        gate.complete(Unit)
        val content = assertIs<AgendaState.Content>(vm.uiState.value.agenda)
        assertEquals("shift", (content.items.single() as AgendaItem.EventCard).event.id)
    }

    @Test
    fun emptyDayAfterASyncIsEmptyAndTheWholeMonthGridIsRequested() = runTest {
        val vm = viewModel()
        assertEquals(AgendaState.Empty, vm.uiState.value.agenda)
        val grid = CalendarMath.monthGrid(YearMonth.of(2026, 11))
        assertEquals(listOf(grid), calendar.observedRanges)
        assertEquals(42, grid.days().count())
        assertEquals(42, vm.uiState.value.cells.size)
    }

    @Test
    fun coldFailureShowsTheErrorAndRetryRecovers() = runTest {
        calendar.refreshError = WaiveraError.Network()
        calendar.events = listOf(shift)
        val vm = viewModel()
        assertIs<AgendaState.Error>(vm.uiState.value.agenda)

        calendar.refreshError = null
        vm.retry()
        assertIs<AgendaState.Content>(vm.uiState.value.agenda)
        assertNull(vm.uiState.value.feedError)
    }

    @Test
    fun failedRefreshOverCachedDataKeepsTheAgendaAndShowsABanner() = runTest {
        calendar.events = listOf(shift)
        val vm = viewModel()
        calendar.refreshError = WaiveraError.Server(503)
        vm.refresh()
        assertIs<AgendaState.Content>(vm.uiState.value.agenda)
        assertNotNull(vm.uiState.value.feedError)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun reconnectRefetchesTheVisibleRangeAndMilestones() = runTest {
        val vm = viewModel()
        val range = CalendarMath.monthGrid(YearMonth.of(2026, 11))
        val refreshesBefore = calendar.refreshedRanges.size
        val milestonesBefore = calendar.milestoneRefreshes

        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Reconnecting(1, 1_000), isOnline = true)
        assertEquals(refreshesBefore, calendar.refreshedRanges.size)
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connected, isOnline = true)

        assertEquals(listOf(range), calendar.refreshedRanges.drop(refreshesBefore))
        assertEquals(milestonesBefore + 1, calendar.milestoneRefreshes)
        assertFalse(vm.uiState.value.isOffline)
    }

    @Test
    fun goingOfflineShowsTheOfflineBannerAndComingBackRefetches() = runTest {
        val vm = viewModel()
        val before = calendar.refreshedRanges.size
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Reconnecting(1, 1_000), isOnline = false)
        assertTrue(vm.uiState.value.isOffline)
        realtime.mutableStatus.value = RealtimeStatus(ConnectionState.Connected, isOnline = true)
        assertFalse(vm.uiState.value.isOffline)
        assertEquals(before + 1, calendar.refreshedRanges.size)
    }

    @Test
    fun todayIsTheEmployeesDateNotTheDevicesOnTheFallBackNight() = runTest {
        // 04:30Z on 1 Nov: 23:30 on 31 Oct in Chicago, 13:30 on 1 Nov in Tokyo.
        clock.now = Instant.parse("2026-11-01T04:30:00Z")
        device = tokyo
        val vm = viewModel()
        val state = vm.uiState.value
        assertEquals(LocalDate.of(2026, 10, 31), state.today)
        assertEquals(LocalDate.of(2026, 10, 31), state.cells.single { it.isToday }.date)
        assertEquals(CalendarMath.monthGrid(YearMonth.of(2026, 10)), calendar.observedRanges.last())
        assertFalse(assertNotNull(state.zoneNotice).isFallback)
    }

    @Test
    fun changingTheSavedZoneMovesTodayAndRefetches() = runTest {
        val vm = viewModel()
        assertEquals(LocalDate.of(2026, 11, 1), vm.uiState.value.today)
        calendar.zoneState.value = ZoneId.of("Australia/Sydney") // 16:30Z is 03:30 on 2 Nov there
        assertEquals(LocalDate.of(2026, 11, 2), vm.uiState.value.today)
        assertEquals(ZoneId.of("Australia/Sydney"), vm.uiState.value.zone)
        assertEquals(2, calendar.observedRanges.size)
    }

    @Test
    fun weekStepRequestsTheNextSevenDays() = runTest {
        val vm = viewModel()
        vm.selectView(ScheduleView.WEEK)
        val first = calendar.observedRanges.last()
        vm.step(1)
        val next = calendar.observedRanges.last()
        assertEquals(first.start.plusDays(7), next.start)
        assertEquals(first.endExclusive.plusDays(7), next.endExclusive)
        assertEquals(LocalDate.of(2026, 11, 8), vm.uiState.value.selectedDate)
        assertFalse(vm.uiState.value.isTodaySelected)

        vm.goToToday()
        assertEquals(LocalDate.of(2026, 11, 1), vm.uiState.value.selectedDate)
        assertEquals(ScheduleView.WEEK, vm.uiState.value.view)
    }

    @Test
    fun jumpToTodayIsOfferedOnlyWhileTodayIsOutOfSight() = runTest {
        val vm = viewModel()
        vm.selectView(ScheduleView.WEEK)
        assertFalse(vm.uiState.value.showsJumpToToday, "today is in this week")
        vm.selectDate(LocalDate.of(2026, 11, 3))
        assertFalse(vm.uiState.value.showsJumpToToday, "another day of the same week keeps today in view")
        vm.step(1)
        assertTrue(vm.uiState.value.showsJumpToToday)
        vm.goToToday()
        assertFalse(vm.uiState.value.showsJumpToToday)

        vm.selectView(ScheduleView.DAY)
        assertFalse(vm.uiState.value.showsJumpToToday)
        vm.step(1)
        assertTrue(vm.uiState.value.showsJumpToToday, "the day view shows one day")
    }

    @Test
    fun switchingViewsKeepsTheSelectedDate() = runTest {
        val vm = viewModel()
        vm.selectDate(LocalDate.of(2026, 11, 19))
        vm.selectView(ScheduleView.DAY)
        assertEquals(LocalDate.of(2026, 11, 19), vm.uiState.value.selectedDate)
        assertEquals(
            SchedulePeriods.rangeFor(ScheduleView.DAY, LocalDate.of(2026, 11, 19)),
            calendar.observedRanges.last(),
        )
        assertTrue(vm.uiState.value.cells.isEmpty())
    }

    @Test
    fun resumeAfterMidnightMovesToday() = runTest {
        val vm = viewModel()
        clock.now = Instant.parse("2026-11-02T06:05:00Z") // 00:05 CST on 2 Nov
        vm.onResume()
        assertEquals(LocalDate.of(2026, 11, 2), vm.uiState.value.today)
        assertEquals(LocalDate.of(2026, 11, 2), vm.uiState.value.selectedDate)
    }

    @Test
    fun staleMilestonesRefreshOnOpenButFreshOnesDoNot() = runTest {
        viewModel()
        assertEquals(1, calendar.milestoneRefreshes)

        val fresh = FakeCalendarRepository(clock)
        fresh.milestoneState.value = Synced(emptyList(), lastSyncedAt = clock.instant().minusSeconds(5 * 60))
        val vm = ScheduleViewModel(
            calendar = fresh,
            session = session,
            realtime = realtime,
            clock = clock,
            deviceZone = DeviceZone { device },
            conversations = conversations,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(0, fresh.milestoneRefreshes)
    }

    @Test
    fun milestoneFailureIsSurfacedWithoutHidingTheAgenda() = runTest {
        calendar.events = listOf(shift)
        calendar.milestoneError = WaiveraError.Network()
        val vm = viewModel()
        assertNotNull(vm.uiState.value.milestonesError)
        assertIs<AgendaState.Content>(vm.uiState.value.agenda)

        calendar.milestoneError = null
        calendar.milestones = listOf(Milestone(MilestoneType.BIRTHDAY, "usr_priya", "Priya", month = 11, day = 1))
        vm.retryMilestones()
        assertNull(vm.uiState.value.milestonesError)
        assertEquals(1, vm.uiState.value.cells.single { it.isToday }.marker.milestones)
    }

    @Test
    fun pullToRefreshRefreshesFeedAndMilestonesTogether() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        calendar.refreshGate = gate
        val milestonesBefore = calendar.milestoneRefreshes
        vm.refresh()
        assertTrue(vm.uiState.value.isRefreshing)
        assertEquals(milestonesBefore + 1, calendar.milestoneRefreshes)
        gate.complete(Unit)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun theSignedInPersonIsKnownSoTheirOwnCardOffersNoWishes() = runTest {
        val vm = viewModel()
        assertEquals("usr_alex_rivera", vm.uiState.value.myUserId)
    }

    @Test
    fun sendWishesOpensTheDirectChat() = runTest {
        val vm = viewModel()
        val effects = mutableListOf<ScheduleEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        vm.sendWishes("usr_priya")
        runCurrent()
        assertEquals(listOf(listOf("usr_priya")), conversations.opened)
        assertEquals(ScheduleEffect.OpenConversation(FakeConversationRepository.OPENED_CONVERSATION_ID), effects.last())
    }

    @Test
    fun sendWishesShowsItsButtonBusyUntilTheChatOpens() = runTest {
        val gate = CompletableDeferred<Unit>()
        conversations.openGate = gate
        val vm = viewModel()
        vm.sendWishes("usr_priya")
        runCurrent()
        assertEquals("usr_priya", vm.uiState.value.openingWishesFor, "busy from the tap")
        vm.sendWishes("usr_priya")
        runCurrent()
        assertEquals(1, conversations.opened.size, "a second tap while opening is ignored")
        gate.complete(Unit)
        runCurrent()
        assertNull(vm.uiState.value.openingWishesFor)
    }

    @Test
    fun sendWishesThatFailsOffersRetry() = runTest {
        conversations.openResult = Result.failure(WaiveraError.Network())
        val vm = viewModel()
        val effects = mutableListOf<ScheduleEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        vm.sendWishes("usr_priya")
        runCurrent()
        assertEquals(ScheduleEffect.WishesFailed("usr_priya"), effects.last())
    }
}
