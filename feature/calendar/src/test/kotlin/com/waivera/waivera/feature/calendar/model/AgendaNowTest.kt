package com.waivera.waivera.feature.calendar.model

import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Holiday
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Today's "Now" divider and ended items (UI4-12), always placed by instant in the saved zone. */
class AgendaNowTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val fallBack = LocalDate.of(2026, 11, 1)
    private val range = DateRange(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 7))

    private fun timed(id: String, start: String, end: String?) = ScheduleEvent(
        id = id,
        title = id,
        startAt = Instant.parse(start),
        endAt = end?.let(Instant::parse),
    )

    private fun items(vararg events: ScheduleEvent, holidays: List<Holiday> = emptyList(), date: LocalDate = fallBack) =
        Agenda.forDay(
            ScheduleFeed(range, chicago.id, events.toList(), emptyList(), holidays),
            chicago,
            MilestoneIndex.Empty,
            date,
        )

    // The demo day: an overnight shift that ended at 6:00 AM and a visit at 11:00 AM; it is 10:30 AM.
    private val overnight = timed("overnight", "2026-11-01T03:00:00Z", "2026-11-01T12:00:00Z")
    private val visit = timed("visit", "2026-11-01T17:00:00Z", "2026-11-01T18:00:00Z")
    private val tenThirty = Instant.parse("2026-11-01T16:30:00Z")

    @Test
    fun theDividerGoesBeforeTheFirstItemThatHasNotEnded() {
        val now = requireNotNull(AgendaNowRules.of(items(overnight, visit), tenThirty, chicago, fallBack))
        assertEquals("event:visit", now.beforeKey)
        assertEquals(setOf("event:overnight"), now.endedKeys)
        assertEquals(tenThirty, now.time.toInstant())
        assertEquals(chicago, now.time.zone)
    }

    @Test
    fun anEventInProgressHasNotEndedSoTheDividerSitsAboveIt() {
        val during = Instant.parse("2026-11-01T17:30:00Z")
        val now = requireNotNull(AgendaNowRules.of(items(overnight, visit), during, chicago, fallBack))
        assertEquals("event:visit", now.beforeKey)
        assertEquals(setOf("event:overnight"), now.endedKeys)
    }

    @Test
    fun afterTheLastItemTheDividerGoesAtTheEnd() {
        val evening = Instant.parse("2026-11-02T01:00:00Z")
        val now = requireNotNull(AgendaNowRules.of(items(overnight, visit), evening, chicago, fallBack))
        assertNull(now.beforeKey)
        assertEquals(setOf("event:overnight", "event:visit"), now.endedKeys)
    }

    @Test
    fun allDayItemsNeverEndAndStayAboveTheDivider() {
        val holiday = Holiday(1, "Founders day", fallBack, fallBack)
        val now = requireNotNull(
            AgendaNowRules.of(items(overnight, visit, holidays = listOf(holiday)), tenThirty, chicago, fallBack),
        )
        assertEquals("event:visit", now.beforeKey)
        assertEquals(setOf("event:overnight"), now.endedKeys)
    }

    @Test
    fun anotherDayHasNoDivider() {
        val tomorrow = fallBack.plusDays(1)
        val other = timed("other", "2026-11-02T17:00:00Z", "2026-11-02T18:00:00Z")
        assertNull(AgendaNowRules.of(items(other, date = tomorrow), tenThirty, chicago, tomorrow))
    }

    @Test
    fun aDayWithNothingTimedHasNoDivider() {
        val holiday = Holiday(1, "Founders day", fallBack, fallBack)
        assertNull(AgendaNowRules.of(items(holidays = listOf(holiday)), tenThirty, chicago, fallBack))
    }

    @Test
    fun theRepeatedFallBackHourIsPlacedByInstantNotByWallClock() {
        // 1:30 AM happens twice: 06:30Z (CDT) and 07:30Z (CST). At 07:00Z it is 1:00 AM CST, a
        // wall time earlier than the first 1:30, yet the first event has already ended.
        val first = timed("first", "2026-11-01T06:30:00Z", "2026-11-01T06:45:00Z")
        val second = timed("second", "2026-11-01T07:30:00Z", "2026-11-01T07:45:00Z")
        val now = requireNotNull(
            AgendaNowRules.of(items(first, second), Instant.parse("2026-11-01T07:00:00Z"), chicago, fallBack),
        )
        assertEquals("event:second", now.beforeKey)
        assertEquals(setOf("event:first"), now.endedKeys)
    }

    @Test
    fun anItemWithNoEndHasEndedOnceItStarts() {
        val reminder = timed("reminder", "2026-11-01T15:00:00Z", null)
        val now = requireNotNull(AgendaNowRules.of(items(reminder, visit), tenThirty, chicago, fallBack))
        assertEquals("event:visit", now.beforeKey)
        assertEquals(setOf("event:reminder"), now.endedKeys)
    }
}
