package com.waivera.waivera.feature.today.ui

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Clock and date formats. Times are always 12-hour (pitfall 39); values arrive already in the
 * employee's zone. The parts of a time ("12:00 PM") and of a short date ("Tue, Oct 20") are
 * joined by no-break spaces (U+00A0), so at 200% a row wraps before or after them, never
 * inside (UI4-07).
 */
internal class TodayFormats(private val locale: Locale) {
    private val time = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val day = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val longDay = DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)
    private val hour = DateTimeFormatter.ofPattern("h a", locale)
    private val bareHour = DateTimeFormatter.ofPattern("h", locale)
    private val clock = DateTimeFormatter.ofPattern("h:mm", locale)
    private val meridiem = DateTimeFormatter.ofPattern("a", locale)

    fun time(value: LocalTime): String = time.format(value)

    /**
     * The two parts of [time] for a display clock: "11:00" set large and "AM" set small beside it,
     * so the period does not weigh the numeral down (UI10-10). Times are always 12-hour here.
     */
    fun clock(value: LocalTime): String = clock.format(value)

    fun meridiem(value: LocalTime): String = meridiem.format(value)

    fun day(value: LocalDate): String = day.format(value)

    fun longDay(value: LocalDate): String = longDay.format(value)

    /** The hero's date line, capitalised for the eyebrow style ("SUNDAY, NOVEMBER 1"). */
    fun eyebrowDay(value: LocalDate): String = upper(longDay(value))

    /** Capitals for an eyebrow style, in the formats' own locale (the string keeps sentence case). */
    fun upper(text: String): String = text.uppercase(locale)

    /** A whole hour on the day track: "8 AM", or the bare "10" between the ends and noon. */
    fun hour(value: LocalTime, withPeriod: Boolean): String = (if (withPeriod) hour else bareHour).format(value)
}
