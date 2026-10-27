package com.waivera.waivera.feature.chat.list

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Inbox time labels (spec 02 §4 "Timestamp"): Now, minutes, a 12-hour clock for today
 * (pitfall 39), Yesterday, then a short date. Calendar days are taken in the employee's saved
 * zone so the label agrees with the rest of the app, never the device zone.
 */
class RelativeTime(private val text: ChatListText, locale: Locale = Locale.getDefault()) {
    private val clock = DateTimeFormatter.ofPattern("h:mm a", locale)
    private val shortDate = DateTimeFormatter.ofPattern("MMM d", locale)

    @Suppress("ReturnCount")
    fun label(at: Instant?, now: Instant, zone: ZoneId): String {
        if (at == null) return ""
        val minutes = Duration.between(at, now).toMinutes().coerceAtLeast(0)
        if (minutes < 1) return text.now
        if (minutes < MINUTES_PER_HOUR) return text.minutesAgo(minutes.toInt())
        val day = at.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when (day) {
            today -> clock.format(at.atZone(zone))
            today.minusDays(1) -> text.yesterday
            else -> shortDate.format(at.atZone(zone))
        }
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60L

        /** Relative labels refresh on this shared clock (the Expo list never ticked). */
        const val TICK_MILLIS = 30_000L
    }
}
