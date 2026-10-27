package com.waivera.waivera.feature.calendar.model

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.DateRange
import java.time.LocalDate
import java.time.YearMonth

/** The three schedule views. Month is the default (07 §1). */
enum class ScheduleView { DAY, WEEK, MONTH }

/** How the navigator title reads for the current period (07 §1 "Period title"). */
sealed interface PeriodTitle {
    /** "October 2026". */
    data class Month(val month: YearMonth) : PeriodTitle

    /** "Tue, October 27". */
    data class Day(val date: LocalDate) : PeriodTitle

    /** "October 25–31, 2026". */
    data class WeekInMonth(val start: LocalDate, val endInclusive: LocalDate) : PeriodTitle

    /** "October 28 – November 3", without a year. */
    data class WeekAcrossMonths(val start: LocalDate, val endInclusive: LocalDate) : PeriodTitle
}

/**
 * Period maths for the schedule screen. Every value is a literal date in the employee's zone;
 * converting to instants happens only at the repository boundary.
 */
object SchedulePeriods {
    /** Month stepping keeps the day but never past the 28th, so Jan 31 steps to Feb 28. */
    private const val MONTH_STEP_MAX_DAY = 28

    /**
     * The range the feed is fetched for. Month view fetches the whole 42-cell grid, not the
     * calendar month, so spill days show their events (fixes the Expo month-only fetch).
     */
    fun rangeFor(view: ScheduleView, date: LocalDate): DateRange = when (view) {
        ScheduleView.DAY -> CalendarMath.day(date)
        ScheduleView.WEEK -> CalendarMath.week(date)
        ScheduleView.MONTH -> CalendarMath.monthGrid(YearMonth.from(date))
    }

    /** Cells the grid draws: 42 for month, 7 for week, none for day. */
    fun cellsFor(view: ScheduleView, date: LocalDate): List<LocalDate> = when (view) {
        ScheduleView.DAY -> emptyList()
        ScheduleView.WEEK -> CalendarMath.week(date).days().toList()
        ScheduleView.MONTH -> CalendarMath.monthGrid(YearMonth.from(date)).days().toList()
    }

    /** The selected date after one press of Previous (-1) or Next (+1). */
    fun step(view: ScheduleView, date: LocalDate, direction: Int): LocalDate = when (view) {
        ScheduleView.DAY -> date.plusDays(direction.toLong())
        ScheduleView.WEEK -> date.plusWeeks(direction.toLong())
        ScheduleView.MONTH -> YearMonth.from(date).plusMonths(direction.toLong())
            .atDay(minOf(date.dayOfMonth, MONTH_STEP_MAX_DAY))
    }

    fun title(view: ScheduleView, date: LocalDate): PeriodTitle = when (view) {
        ScheduleView.MONTH -> PeriodTitle.Month(YearMonth.from(date))
        ScheduleView.DAY -> PeriodTitle.Day(date)
        ScheduleView.WEEK -> {
            val week = CalendarMath.week(date)
            val last = week.endExclusive.minusDays(1)
            if (YearMonth.from(week.start) == YearMonth.from(last)) {
                PeriodTitle.WeekInMonth(week.start, last)
            } else {
                PeriodTitle.WeekAcrossMonths(week.start, last)
            }
        }
    }
}
