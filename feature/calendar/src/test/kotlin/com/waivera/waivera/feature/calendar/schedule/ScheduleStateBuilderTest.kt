package com.waivera.waivera.feature.calendar.schedule

import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.SchedulePeriods
import com.waivera.waivera.feature.calendar.model.ScheduleView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleStateBuilderTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val today = LocalDate.of(2026, 11, 1)
    private val synced = Instant.parse("2026-11-01T16:00:00Z")

    private fun period(view: ScheduleView = ScheduleView.MONTH, date: LocalDate = today) =
        SchedulePeriod(view, date, today, chicago)

    private fun feed(vararg events: ScheduleEvent, date: LocalDate = today, view: ScheduleView = ScheduleView.MONTH) =
        ScheduleFeed(SchedulePeriods.rangeFor(view, date), chicago.id, events = events.toList())

    private fun build(
        feed: Synced<ScheduleFeed>?,
        period: SchedulePeriod = period(),
        milestones: MilestoneIndex = MilestoneIndex.Empty,
        deviceZone: ZoneId? = chicago,
    ) = ScheduleStateBuilder.build(
        period = period,
        feed = feed,
        milestones = milestones,
        milestonesError = null,
        savedZoneId = chicago.id,
        deviceZone = deviceZone,
        isOffline = false,
        isRefreshing = false,
    )

    private val overnight = ScheduleEvent(
        id = "overnight",
        title = "Overnight shift",
        startAt = Instant.parse("2026-11-01T04:00:00Z"), // 23:00 CDT on 31 Oct
        endAt = Instant.parse("2026-11-01T13:00:00Z"), // 07:00 CST on 1 Nov
    )

    @Test
    fun todayWithANowGetsTheDividerAndOtherDaysDoNot() {
        val tenThirty = Instant.parse("2026-11-01T16:30:00Z")
        val visit = ScheduleEvent(
            id = "visit",
            title = "Client home visit",
            startAt = Instant.parse("2026-11-01T17:00:00Z"),
            endAt = Instant.parse("2026-11-01T18:00:00Z"),
        )
        val todayState = build(
            Synced(feed(overnight, visit), lastSyncedAt = synced),
            period = SchedulePeriod(ScheduleView.MONTH, today, today, chicago, now = tenThirty),
        )
        val now = assertNotNull(todayState.agendaNow)
        assertEquals("event:visit", now.beforeKey)
        assertEquals(setOf("event:overnight"), now.endedKeys)

        val tomorrow = today.plusDays(1)
        val otherDay = build(
            Synced(feed(overnight, visit, date = tomorrow), lastSyncedAt = synced),
            period = SchedulePeriod(ScheduleView.MONTH, tomorrow, today, chicago, now = tenThirty),
        )
        assertNull(otherDay.agendaNow)
        assertNull(build(Synced(feed(overnight, visit), lastSyncedAt = synced)).agendaNow, "no clock, no divider")
    }

    @Test
    fun unknownFeedIsLoading() {
        assertEquals(AgendaState.Loading, build(feed = null).agenda)
        assertEquals(AgendaState.Loading, build(Synced(feed(), SyncStatus.Refreshing)).agenda)
    }

    @Test
    fun syncedFeedWithNothingOnTheDayIsEmpty() {
        val state = build(Synced(feed(), lastSyncedAt = synced))
        assertEquals(AgendaState.Empty, state.agenda)
        assertNull(state.feedError)
    }

    @Test
    fun coldFailureIsAFullErrorButCachedFailureKeepsContentWithABanner() {
        val error = WaiveraError.Network()
        val cold = build(Synced(feed(), SyncStatus.Failed(error)))
        assertIs<AgendaState.Error>(cold.agenda)
        assertNull(cold.feedError)

        val cached = build(Synced(feed(overnight), SyncStatus.Failed(error), lastSyncedAt = synced))
        val content = assertIs<AgendaState.Content>(cached.agenda)
        assertEquals("overnight", (content.items.single() as AgendaItem.EventCard).event.id)
        assertEquals(error, cached.feedError)
    }

    @Test
    fun gridMarksTodayTheSelectionAndOutOfMonthCells() {
        val selected = LocalDate.of(2026, 11, 3)
        val state = build(Synced(feed(date = selected), lastSyncedAt = synced), period = period(date = selected))
        assertEquals(42, state.cells.size)
        assertEquals(today, state.cells.single { it.isToday }.date)
        assertEquals(selected, state.cells.single { it.isSelected }.date)
        assertTrue(state.cells.first().inPeriod) // 1 Nov 2026 is a Sunday, so the grid starts in month.
        assertTrue(state.cells.filter { it.date.monthValue == 12 }.none { it.inPeriod })
        assertTrue(!state.isTodaySelected)
    }

    @Test
    fun overnightEventMarksBothDaysItTouchesAcrossFallBack() {
        val october = LocalDate.of(2026, 10, 31)
        val state =
            build(Synced(feed(overnight, date = october), lastSyncedAt = synced), period = period(date = october))
        val marked = state.cells.filter { it.marker.events > 0 }.map { it.date }
        assertEquals(listOf(october, today), marked)
    }

    @Test
    fun milestoneDotsComeFromTheIndex() {
        val milestones = MilestoneIndex(listOf(Milestone(MilestoneType.BIRTHDAY, "u1", "Priya", month = 11, day = 2)))
        val state = build(Synced(feed(), lastSyncedAt = synced), milestones = milestones)
        val cell = state.cells.single { it.marker.milestones > 0 }
        assertEquals(LocalDate.of(2026, 11, 2), cell.date)
    }

    @Test
    fun weekViewHasSevenInPeriodCellsAndDayViewNone() {
        val week =
            build(Synced(feed(view = ScheduleView.WEEK), lastSyncedAt = synced), period = period(ScheduleView.WEEK))
        assertEquals(7, week.cells.size)
        assertTrue(week.cells.all { it.inPeriod })
        val day = build(Synced(feed(view = ScheduleView.DAY), lastSyncedAt = synced), period = period(ScheduleView.DAY))
        assertTrue(day.cells.isEmpty())
    }

    @Test
    fun zoneNoticeWaitsForTheDeviceZoneAndShowsWhenItDiffers() {
        assertNull(build(feed = null, deviceZone = null).zoneNotice)
        assertNull(build(feed = null, deviceZone = chicago).zoneNotice)
        assertNotNull(build(feed = null, deviceZone = ZoneId.of("Asia/Tokyo")).zoneNotice)
    }
}
