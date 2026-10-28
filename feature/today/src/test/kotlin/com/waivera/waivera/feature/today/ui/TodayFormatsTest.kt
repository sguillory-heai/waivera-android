package com.waivera.waivera.feature.today.ui

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Times and short dates never split inside at 200%: their parts are joined by no-break spaces (UI4-07). */
class TodayFormatsTest {
    private val formats = TodayFormats(Locale.US)

    @Test
    fun timeKeepsAmPmWithTheClock() {
        assertEquals("12:00\u00A0PM", formats.time(LocalTime.NOON))
        assertEquals("9:05\u00A0AM", formats.time(LocalTime.of(9, 5)))
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
