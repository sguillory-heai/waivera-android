package com.waivera.waivera.feature.calendar.schedule

import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.designsystem.component.WaiveraTimelineTone
import com.waivera.waivera.feature.calendar.model.Agenda
import com.waivera.waivera.feature.calendar.model.AgendaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** What the Day view's timeline rail says beside each item, in the employee's saved zone. */
class DayRailTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val fallBack = LocalDate.of(2026, 11, 1)

    @Test
    fun aTimedEventShowsItsStartInTheSavedZone() {
        val visit = ScheduleEvent(
            id = "visit",
            startAt = Instant.parse("2026-11-01T17:00:00Z"),
            endAt = Instant.parse("2026-11-01T18:00:00Z"),
        )
        val rail = assertIs<RailTime.At>(railTime(Agenda.timing(visit, chicago, fallBack)))
        // 17:00 UTC is 11:00 CST once the clocks have gone back.
        assertEquals(11, rail.time.hour)
        assertEquals(ZoneOffset.ofHours(-6), rail.time.offset)
    }

    @Test
    fun anOvernightShiftAcrossTheFallBackShowsWhenItEnds() {
        val overnight = ScheduleEvent(
            id = "overnight",
            startAt = Instant.parse("2026-11-01T04:00:00Z"), // 23:00 CDT on 31 Oct
            endAt = Instant.parse("2026-11-01T13:00:00Z"), // 07:00 CST on 1 Nov
        )
        val rail = assertIs<RailTime.Until>(railTime(Agenda.timing(overnight, chicago, fallBack)))
        assertEquals(7, rail.time.hour)
        assertEquals(ZoneOffset.ofHours(-6), rail.time.offset)
        // On the day it starts, the same shift reads by its start, still on daylight time.
        val start = assertIs<RailTime.At>(railTime(Agenda.timing(overnight, chicago, fallBack.minusDays(1))))
        assertEquals(23, start.time.hour)
        assertEquals(ZoneOffset.ofHours(-5), start.time.offset)
    }

    @Test
    fun allDayItemsAndCelebrationsSayAllDay() {
        val dstEnds = ScheduleEvent(id = "dst", allDayStart = fallBack, allDayEndExclusive = fallBack.plusDays(1))
        assertEquals(RailTime.AllDay, railTime(Agenda.timing(dstEnds, chicago, fallBack)))
        assertEquals(RailTime.AllDay, railTime(timing = null))
    }

    @Test
    fun eventsAreTealAndEndedItemsAreQuiet() {
        val visit = ScheduleEvent(id = "visit", startAt = Instant.parse("2026-11-01T17:00:00Z"))
        val card = AgendaItem.EventCard(visit, Agenda.timing(visit, chicago, fallBack))
        assertEquals(WaiveraTimelineTone.Teal, railTone(card, ended = false))
        assertNull(railTone(card, ended = true))
    }
}
