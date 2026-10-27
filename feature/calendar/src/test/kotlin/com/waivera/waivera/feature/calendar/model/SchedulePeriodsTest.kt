package com.waivera.waivera.feature.calendar.model

import com.waivera.waivera.core.data.calendar.CalendarMath
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulePeriodsTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val sydney = ZoneId.of("Australia/Sydney")

    @Test
    fun monthViewIn_october2026_has42CellsFromTheSundayOnOrBefore1October() {
        val cells = SchedulePeriods.cellsFor(ScheduleView.MONTH, LocalDate.of(2026, 10, 27))
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 9, 27), cells.first())
        assertEquals(DayOfWeek.SUNDAY, cells.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 7), cells.last())
    }

    @Test
    fun monthViewFetchesTheWholeRenderedGridNotJustTheMonth() {
        val range = SchedulePeriods.rangeFor(ScheduleView.MONTH, LocalDate.of(2026, 10, 15))
        assertEquals(LocalDate.of(2026, 9, 27), range.start)
        assertEquals(LocalDate.of(2026, 11, 8), range.endExclusive)
        assertEquals(SchedulePeriods.cellsFor(ScheduleView.MONTH, LocalDate.of(2026, 10, 15)), range.days().toList())
    }

    @Test
    fun steppingMonthFrom31JanuaryLandsOn28FebruaryThen28March() {
        val feb = SchedulePeriods.step(ScheduleView.MONTH, LocalDate.of(2027, 1, 31), 1)
        val mar = SchedulePeriods.step(ScheduleView.MONTH, feb, 1)
        assertEquals(LocalDate.of(2027, 2, 28), feb)
        assertEquals(LocalDate.of(2027, 3, 28), mar)
        assertEquals(
            LocalDate.of(2026, 12, 28),
            SchedulePeriods.step(ScheduleView.MONTH, LocalDate.of(2027, 1, 31), -1),
        )
    }

    @Test
    fun weekStepAdvancesTheRangeByExactlySevenDays() {
        val date = LocalDate.of(2026, 10, 28)
        val before = SchedulePeriods.rangeFor(ScheduleView.WEEK, date)
        val after = SchedulePeriods.rangeFor(ScheduleView.WEEK, SchedulePeriods.step(ScheduleView.WEEK, date, 1))
        assertEquals(before.start.plusDays(7), after.start)
        assertEquals(before.endExclusive.plusDays(7), after.endExclusive)
        assertEquals(DayOfWeek.SUNDAY, before.start.dayOfWeek)
    }

    @Test
    fun dayStepMovesOneDayAndDayViewHasNoGrid() {
        val date = LocalDate.of(2026, 10, 31)
        assertEquals(LocalDate.of(2026, 11, 1), SchedulePeriods.step(ScheduleView.DAY, date, 1))
        assertTrue(SchedulePeriods.cellsFor(ScheduleView.DAY, date).isEmpty())
        assertEquals(CalendarMath.day(date), SchedulePeriods.rangeFor(ScheduleView.DAY, date))
    }

    @Test
    fun periodTitlesFollowTheSpecShapes() {
        assertEquals(
            PeriodTitle.Month(YearMonth.of(2026, 10)),
            SchedulePeriods.title(ScheduleView.MONTH, LocalDate.of(2026, 10, 27)),
        )
        assertEquals(
            PeriodTitle.Day(LocalDate.of(2026, 10, 27)),
            SchedulePeriods.title(ScheduleView.DAY, LocalDate.of(2026, 10, 27)),
        )
        assertEquals(
            PeriodTitle.WeekInMonth(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 31)),
            SchedulePeriods.title(ScheduleView.WEEK, LocalDate.of(2026, 10, 27)),
        )
        // Spec example "October 28 – November 3" is a Wednesday-start week; ours start Sunday.
        assertEquals(
            PeriodTitle.WeekAcrossMonths(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 5)),
            SchedulePeriods.title(ScheduleView.WEEK, LocalDate.of(2026, 12, 1)),
        )
    }

    @Test
    fun monthGridContainingChicagoFallBackIsOneHourLongerThan42Days() {
        // November 2026 grid: 1 Nov (fall back) to 12 Dec, in the employee's zone.
        val grid = SchedulePeriods.rangeFor(ScheduleView.MONTH, LocalDate.of(2026, 11, 15))
        val (start, end) = CalendarMath.window(grid, chicago)
        assertEquals(Duration.ofDays(42).plusHours(1), Duration.between(start, end))
        assertEquals(java.time.Instant.parse("2026-11-01T05:00:00Z"), start)
    }

    @Test
    fun weekContainingChicagoSpringForwardIsOneHourShort() {
        val week = SchedulePeriods.rangeFor(ScheduleView.WEEK, LocalDate.of(2026, 3, 10))
        val (start, end) = CalendarMath.window(week, chicago)
        assertEquals(Duration.ofDays(7).minusHours(1), Duration.between(start, end))
    }

    @Test
    fun southernHemisphereGridsShiftTheOtherWay() {
        // Sydney springs forward on 4 Oct 2026 and falls back on 5 Apr 2026.
        val october = SchedulePeriods.rangeFor(ScheduleView.MONTH, LocalDate.of(2026, 10, 10))
        val (octStart, octEnd) = CalendarMath.window(october, sydney)
        assertEquals(Duration.ofDays(42).minusHours(1), Duration.between(octStart, octEnd))

        val april = SchedulePeriods.rangeFor(ScheduleView.MONTH, LocalDate.of(2026, 4, 10))
        val (aprStart, aprEnd) = CalendarMath.window(april, sydney)
        assertEquals(Duration.ofDays(42).plusHours(1), Duration.between(aprStart, aprEnd))

        // The Sydney spring-forward day is 23 hours long.
        val (dayStart, dayEnd) = CalendarMath.window(CalendarMath.day(LocalDate.of(2026, 10, 4)), sydney)
        assertEquals(Duration.ofHours(23), Duration.between(dayStart, dayEnd))
    }
}
