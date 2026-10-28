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
internal class TodayFormats(locale: Locale) {
    private val time = DateTimeFormatter.ofPattern("h:mm\u00A0a", locale)
    private val day = DateTimeFormatter.ofPattern("EEE,\u00A0MMM\u00A0d", locale)
    private val longDay = DateTimeFormatter.ofPattern("EEEE, MMMM\u00A0d", locale)

    fun time(value: LocalTime): String = time.format(value)

    fun day(value: LocalDate): String = day.format(value)

    fun longDay(value: LocalDate): String = longDay.format(value)
}
