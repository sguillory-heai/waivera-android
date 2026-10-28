package com.waivera.waivera.feature.today.ui

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Times and short dates never split inside at 200%: their parts are joined by no-break spaces (UI4-07). */
class TodayFormatsTest {
    private val formats = TodayFormats(Locale.US)

    @Test
    fun timeKeepsAmPmWithTheClock() {
        assertEquals("12:00\u00A0PM", formats.time(LocalTime.NOON))
        assertEquals("9:05\u00A0AM", formats.time(LocalTime.of(9, 5)))
    }

    /** The display clock's parts put back together are the time, either side of noon (UI10-10). */
    @Test
    fun displayClockSplitsTheTimeFromItsPeriod() {
        assertEquals("11:00", formats.clock(LocalTime.of(11, 0)))
        assertEquals("AM", formats.meridiem(LocalTime.of(11, 0)))
        assertEquals("12:00", formats.clock(LocalTime.NOON))
        assertEquals("PM", formats.meridiem(LocalTime.NOON))
        assertEquals("12:30", formats.clock(LocalTime.of(0, 30)))
        assertEquals("AM", formats.meridiem(LocalTime.of(0, 30)))
    }

    /**
     * On the day DST ends, 1:30 happens twice in Chicago; each wall time, placed in the
     * employee's zone, splits into the same clock and period as the joined time reads.
     */
    @Test
    fun displayClockFollowsTheWallTimeAcrossTheFallBackHour() {
        val chicago = ZoneId.of("America/Chicago")
        val firstPass = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, chicago).withEarlierOffsetAtOverlap()
        val secondPass = firstPass.withLaterOffsetAtOverlap()
        assertNotEquals(firstPass.toInstant(), secondPass.toInstant())
        for (time in listOf(firstPass, secondPass)) {
            val wall = time.toLocalTime()
            assertEquals("1:30", formats.clock(wall))
            assertEquals("1:30\u00A0AM", "${formats.clock(wall)}\u00A0${formats.meridiem(wall)}")
            assertEquals(formats.time(wall), "${formats.clock(wall)}\u00A0${formats.meridiem(wall)}")
        }
    }

    @Test
    fun shortDayKeepsWeekdayMonthAndDayTogether() {
        assertEquals("Tue,\u00A0Oct\u00A020", formats.day(LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun longDayKeepsMonthAndDayTogether() {
        assertEquals("Sunday, November\u00A01", formats.longDay(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun heroEyebrowIsTheLongDayInCapitals() {
        assertEquals("SUNDAY, NOVEMBER\u00A01", formats.eyebrowDay(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun dayTrackHoursNameTheirPeriodOnlyWhereAsked() {
        assertEquals("8\u00A0AM", formats.hour(LocalTime.of(8, 0), withPeriod = true))
        assertEquals("10", formats.hour(LocalTime.of(10, 0), withPeriod = false))
        assertEquals("12\u00A0PM", formats.hour(LocalTime.NOON, withPeriod = true))
        assertEquals("2", formats.hour(LocalTime.of(14, 0), withPeriod = false))
    }
}
