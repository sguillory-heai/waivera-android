package com.waivera.waivera.feature.calendar.model

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Locale-aware schedule dates that do not need resources. Clock times are in [ScheduleClock]. */
object ScheduleText {
    private const val MONTH_YEAR = "MMMM yyyy"
    private const val WEEKDAY_MONTH_DAY = "EEE, MMMM d"
    private const val MONTH_DAY = "MMMM d"
    private const val DAY = "d"
    private const val YEAR = "yyyy"
    private const val FULL_DAY = "EEEE, MMMM d"
    private const val SHORT_DAY = "EEE, MMM d"
    private const val SHORT_DAY_YEAR = "EEE, MMM d, yyyy"

    fun monthYear(date: LocalDate, locale: Locale): String = format(date, MONTH_YEAR, locale)

    fun weekdayMonthDay(date: LocalDate, locale: Locale): String = format(date, WEEKDAY_MONTH_DAY, locale)

    fun monthDay(date: LocalDate, locale: Locale): String = format(date, MONTH_DAY, locale)

    fun dayOfMonth(date: LocalDate, locale: Locale): String = format(date, DAY, locale)

    fun year(date: LocalDate, locale: Locale): String = format(date, YEAR, locale)

    /** "Sunday, November 1", the agenda heading and the cell's spoken label. */
    fun fullDay(date: LocalDate, locale: Locale): String = format(date, FULL_DAY, locale)

    /** "Sun, Nov 1", or "Sun, Nov 1, 2027" when the date is not in [currentYear]. */
    fun shortDay(date: LocalDate, currentYear: Int, locale: Locale): String =
        format(date, if (date.year == currentYear) SHORT_DAY else SHORT_DAY_YEAR, locale)

    /** Sunday-first weekday headers; [narrow] gives "S M T W" for large font, where "Mon" no longer fits. */
    fun weekdayHeaders(locale: Locale, narrow: Boolean = false): List<String> {
        val style = if (narrow) TextStyle.NARROW else TextStyle.SHORT
        return sundayFirst().map { it.getDisplayName(style, locale) }
    }

    fun weekdayShort(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    /** "M" for Monday. Visual only: the cell's spoken label always carries the full day. */
    fun weekdayNarrow(date: LocalDate, locale: Locale): String = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
}

/**
 * Which zone the schedule is shown in, when that needs saying: the saved zone differs from the
 * device zone, or the profile has no usable zone and the documented fallback is in effect
 * (dates.md §2: never the device zone).
 */
@Immutable
data class ZoneNotice(val zone: ZoneId, val isFallback: Boolean) {
    companion object {
        fun of(zone: ZoneId, savedZoneId: String?, deviceZone: ZoneId): ZoneNotice? {
            val isFallback = WaiveraTime.zoneOrNull(savedZoneId) == null
            return if (isFallback || zone.id != deviceZone.id) {
                ZoneNotice(zone, isFallback)
            } else {
                null
            }
        }
    }
}

private fun format(date: LocalDate, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(date)

private fun sundayFirst(): List<DayOfWeek> =
    listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }

/**
 * Clock times and zone names. Clocks are 12-hour (11-pitfalls §39) and always formatted from a
 * [ZonedDateTime] already placed in the employee's zone, so the device zone never leaks in.
 */
object ScheduleClock {
    private const val CLOCK = "h:mm a"
    private const val CLOCK_WITH_ZONE = "h:mm a zzz"

    /**
     * "10:00 PM". A wall time that happens twice on a fall-back day also names the offset
     * ("1:30 AM CDT" and "1:30 AM CST") so the two are never confused.
     */
    fun clock(time: ZonedDateTime, locale: Locale): String {
        val pattern = if (isRepeatedWallTime(time)) CLOCK_WITH_ZONE else CLOCK
        return DateTimeFormatter.ofPattern(pattern, locale).format(time)
    }

    /** True when [time]'s local wall clock reading occurs twice in its zone (the fall-back hour). */
    fun isRepeatedWallTime(time: ZonedDateTime): Boolean =
        time.zone.rules.getValidOffsets(time.toLocalDateTime()).size > 1

    /** "Central Time" for America/Chicago. */
    fun zoneName(zone: ZoneId, locale: Locale): String = zone.getDisplayName(TextStyle.FULL, locale)
}

/** Labels and values read from server payloads (statuses, request types, owner colours). */
object ScheduleLabels {
    private const val HEX_RGB_LENGTH = 6
    private const val HEX_ARGB_LENGTH = 8
    private const val OPAQUE_ALPHA = 0xFF000000L
    private const val HEX_RADIX = 16
    private const val DEFAULT_REQUEST_TYPE = "FULL_DAY"

    /** "APPROVED" → "Approved", "CANCELLATION_PENDING" → "Cancellation pending". */
    fun sentenceCase(raw: String?): String? {
        val words = raw?.trim()?.takeIf(String::isNotEmpty)?.replace('_', ' ')?.lowercase(Locale.ROOT) ?: return null
        return words.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /** "HALF_DAY_AM" → "half day am" (07 §3 time-off subtitle). */
    fun requestTypeLabel(raw: String?): String =
        (raw?.trim()?.takeIf(String::isNotEmpty) ?: DEFAULT_REQUEST_TYPE).replace('_', ' ').lowercase(Locale.ROOT)

    /**
     * Parses an owner colour sent by the server ("#7E57C2", "7E57C2" or "#FF7E57C2") into an ARGB
     * value. Anything else returns null and the card falls back to the accent token.
     */
    fun parseOwnerColor(raw: String?): Long? {
        val hex = raw?.trim()?.removePrefix("#") ?: return null
        if (hex.length != HEX_RGB_LENGTH && hex.length != HEX_ARGB_LENGTH) return null
        val value = hex.toLongOrNull(HEX_RADIX) ?: return null
        return if (hex.length == HEX_RGB_LENGTH) value or OPAQUE_ALPHA else value
    }
}
