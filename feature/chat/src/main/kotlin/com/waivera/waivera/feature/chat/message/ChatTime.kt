package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time for chat screens. Instants come from the shared UTC parser in core; chat clocks and day
 * boundaries use the device zone, as in the reference app (03 §3), unlike schedules, which use
 * the employee's saved zone. Both values are injected so tests can pin them.
 */
class ChatClock(private val clock: Clock, private val zoneProvider: () -> ZoneId) {
    fun now(): Instant = clock.instant()

    val zone: ZoneId get() = zoneProvider()
}

/** `formatChatTimestamp` as data, so the copy lives in resources and the rules are testable. */
@Immutable
sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class Seconds(val value: Int) : RelativeTime

    data class Minutes(val value: Int) : RelativeTime

    data class Hours(val value: Int) : RelativeTime

    data class Days(val value: Int) : RelativeTime

    /** Older than a week: "Oct 20", with the year only when it is not the current year. */
    data class OnDate(val date: LocalDate, val showYear: Boolean) : RelativeTime
}

object ChatTime {
    private const val JUST_NOW_SECONDS = 10L
    private const val MINUTE = 60L
    private const val HOUR = 3_600L
    private const val DAY = 86_400L
    private const val WEEK = 7 * DAY

    fun relative(value: Instant, now: Instant, zone: ZoneId): RelativeTime {
        val seconds = Duration.between(value, now).seconds
        return when {
            seconds <= 0 || seconds < JUST_NOW_SECONDS -> RelativeTime.JustNow
            seconds < MINUTE -> RelativeTime.Seconds(seconds.toInt())
            seconds < HOUR -> RelativeTime.Minutes((seconds / MINUTE).toInt())
            seconds < DAY -> RelativeTime.Hours((seconds / HOUR).toInt())
            seconds < WEEK -> RelativeTime.Days((seconds / DAY).toInt())
            else -> {
                val date = value.atZone(zone).toLocalDate()
                RelativeTime.OnDate(date, showYear = date.year != now.atZone(zone).year)
            }
        }
    }

    /** 12-hour clock in [zone] (11 §39), for call logs and the accessibility label of a row. */
    fun clock(value: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("h:mm a", locale).format(value.atZone(zone))

    fun shortDate(date: LocalDate, showYear: Boolean, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (showYear) "MMM d, yyyy" else "MMM d", locale).format(date)

    /** The local calendar day of [value], used for grouping and day separators. */
    fun day(value: Instant, zone: ZoneId): LocalDate = value.atZone(zone).toLocalDate()
}

/**
 * Day separator copy: "Today", "Yesterday", else "Oct 20", with the year only when it is not
 * this year, the way the inbox and Bookmarks write dates (UI3-13). [today] is the saved zone's.
 */
@Immutable
sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class OnDate(val date: LocalDate, val showYear: Boolean = false) : DayLabel

    companion object {
        fun of(day: LocalDate, today: LocalDate): DayLabel = when (day) {
            today -> Today
            today.minusDays(1) -> Yesterday
            else -> OnDate(day, showYear = day.year != today.year)
        }
    }
}
