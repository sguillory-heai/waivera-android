package com.waivera.waivera.feature.profile

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Date formatting for profile screens. Birthdays and hire dates are calendar dates
 * ([LocalDate]) and are formatted from their components only, so they never shift by a day
 * in any zone (the Expo app parsed them as UTC midnight and rendered them in the device zone).
 */
internal object ProfileFormat {
    private const val DATE_PATTERN = "MMM d, yyyy"
    private const val MONTH_DAY_PATTERN = "MMM d"

    /** "Mar 14, 2022". */
    fun date(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(DATE_PATTERN, locale).format(value)

    /** "Apr 2": birthdays never show the year. */
    fun birthday(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(MONTH_DAY_PATTERN, locale).format(value)

    /** An instant (for example a file upload time) as a date in the employee's saved [zone]. */
    fun instantDate(value: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        date(value.atZone(zone).toLocalDate(), locale)

    /** "Nov 3, 2026" for one day, "Nov 3, 2026 – Nov 5, 2026" for a range; null when there is no start. */
    fun dateRange(start: LocalDate?, end: LocalDate?, locale: Locale = Locale.getDefault()): String? {
        val first = start ?: end ?: return null
        val last = end?.takeIf { start != null && it != start }
        return if (last == null) date(first, locale) else "${date(first, locale)} – ${date(last, locale)}"
    }

    /** "Central Time" for America/Chicago, falling back to the id when the JDK has no name. */
    fun zoneName(zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        zone.getDisplayName(TextStyle.FULL, locale).takeIf { it.isNotBlank() && it != zone.id } ?: zone.id

    /**
     * Account roles arrive as system names ("SUPPORT COORDINATOR"); show them in title case like
     * the profile's positions, so the header reads the same before and after the profile loads.
     * Mixed-case values are already display text and stay as they are.
     */
    fun roleLabel(raw: String, locale: Locale = Locale.getDefault()): String {
        val trimmed = raw.trim()
        if (trimmed.any(Char::isLowerCase)) return trimmed
        return trimmed.replace('_', ' ')
            .split(' ')
            .filter(String::isNotEmpty)
            .joinToString(" ") { word ->
                word.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
            }
    }
}
