package com.waivera.waivera.feature.today.model

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** When an upcoming schedule item happens, relative to now, in the employee's saved zone. */
@Immutable
sealed interface UpcomingLabel {
    /** In progress. [until] is the local end time, null when the end is unknown. */
    data class Now(val until: LocalTime?) : UpcomingLabel

    /** Starts within the hour; 0 means it starts this minute. */
    data class InMinutes(val minutes: Long) : UpcomingLabel

    /** Starts within [TimeLabels.RELATIVE_HOURS] hours. */
    data class InHours(val hours: Long, val minutes: Long) : UpcomingLabel

    data class TodayAt(val time: LocalTime) : UpcomingLabel

    data class TomorrowAt(val time: LocalTime) : UpcomingLabel

    data class OnDayAt(val date: LocalDate, val time: LocalTime) : UpcomingLabel

    data object AllDayToday : UpcomingLabel

    data object AllDayTomorrow : UpcomingLabel

    data class AllDayOn(val date: LocalDate) : UpcomingLabel
}

/** How long ago something happened, following the chat list's relative timestamp rules (02 §4). */
@Immutable
sealed interface PastLabel {
    data object JustNow : PastLabel

    data class MinutesAgo(val minutes: Long) : PastLabel

    data class TodayAt(val time: LocalTime) : PastLabel

    data object Yesterday : PastLabel

    data class OnDate(val date: LocalDate) : PastLabel
}

/**
 * Relative time rules for Today. Every calculation takes the zone explicitly (the employee's
 * saved zone), never the device zone, and measures durations between instants so DST days
 * count real minutes, not wall-clock minutes.
 */
object TimeLabels {
    const val RELATIVE_HOURS = 6L
    private const val MINUTES_PER_HOUR = 60L
    private const val SECONDS_PER_MINUTE = 60L

    fun upcoming(start: Instant, end: Instant?, now: Instant, zone: ZoneId): UpcomingLabel {
        if (!start.isAfter(now)) {
            return UpcomingLabel.Now(end?.atZone(zone)?.toLocalTime())
        }
        val minutes = ceilMinutes(Duration.between(now, start))
        val today = now.atZone(zone).toLocalDate()
        val local = start.atZone(zone)
        return when {
            minutes < MINUTES_PER_HOUR -> UpcomingLabel.InMinutes(minutes)
            minutes < RELATIVE_HOURS * MINUTES_PER_HOUR ->
                UpcomingLabel.InHours(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
            local.toLocalDate() == today -> UpcomingLabel.TodayAt(local.toLocalTime())
            local.toLocalDate() == today.plusDays(1) -> UpcomingLabel.TomorrowAt(local.toLocalTime())
            else -> UpcomingLabel.OnDayAt(local.toLocalDate(), local.toLocalTime())
        }
    }

    /** An all-day item that covers [today] reads "All day today", whatever day it started. */
    fun allDay(start: LocalDate, endExclusive: LocalDate?, today: LocalDate): UpcomingLabel {
        val end = endExclusive?.takeIf { it.isAfter(start) } ?: start.plusDays(1)
        return when {
            !today.isBefore(start) && today.isBefore(end) -> UpcomingLabel.AllDayToday
            start == today.plusDays(1) -> UpcomingLabel.AllDayTomorrow
            else -> UpcomingLabel.AllDayOn(start)
        }
    }

    fun past(at: Instant, now: Instant, zone: ZoneId): PastLabel {
        val elapsed = Duration.between(at, now)
        val today = now.atZone(zone).toLocalDate()
        val local = at.atZone(zone)
        return when {
            elapsed.seconds < SECONDS_PER_MINUTE -> PastLabel.JustNow
            elapsed.toMinutes() < MINUTES_PER_HOUR -> PastLabel.MinutesAgo(elapsed.toMinutes())
            local.toLocalDate() == today -> PastLabel.TodayAt(local.toLocalTime())
            local.toLocalDate() == today.minusDays(1) -> PastLabel.Yesterday
            else -> PastLabel.OnDate(local.toLocalDate())
        }
    }

    /** Rounds up, so 30 seconds away reads "in 1 min" rather than "in 0 min". */
    private fun ceilMinutes(duration: Duration): Long {
        val seconds = duration.seconds + if (duration.nano > 0) 1 else 0
        return (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    }
}
