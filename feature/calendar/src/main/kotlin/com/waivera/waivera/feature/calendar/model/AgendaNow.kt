package com.waivera.waivera.feature.calendar.model

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Where "now" falls in today's agenda (UI4-12): the "Now · 10:30 AM" divider goes before the item
 * with [beforeKey] (after the last item when it is null), and the items in [endedKeys] are drawn
 * as ended, so a shift that finished at 6:00 AM no longer looks like the visit in 30 minutes.
 */
@Immutable
data class AgendaNow(val time: ZonedDateTime, val beforeKey: String?, val endedKeys: Set<String>)

object AgendaNowRules {
    /**
     * Null unless [date] is today in [zone] and the agenda has a timed item to place the divider
     * among. Everything compares instants, so a fall-back day's repeated hour cannot misplace it.
     */
    fun of(items: List<AgendaItem>, now: Instant, zone: ZoneId, date: LocalDate): AgendaNow? {
        if (now.atZone(zone).toLocalDate() != date) return null
        val timed = items.mapNotNull { item -> item.timedSpan()?.let { item to it } }
        if (timed.isEmpty()) return null
        val ended = timed.filter { (_, span) -> span.hasEnded(now) }.map { (item, _) -> item.key }.toSet()
        // All-day cards, celebrations and holidays come first and never end during the day.
        val firstOpen = items.firstOrNull { item -> item.timedSpan()?.hasEnded(now) == false }
        return AgendaNow(time = now.atZone(zone), beforeKey = firstOpen?.key, endedKeys = ended)
    }

    private fun AgendaItem.timedSpan(): EventTiming.Timed? = when (this) {
        is AgendaItem.EventCard -> timing as? EventTiming.Timed
        is AgendaItem.TimeOffCard -> timing as? EventTiming.Timed
        is AgendaItem.MilestoneCard, is AgendaItem.HolidayCard -> null
    }

    /** An item with no end is a moment, so it has ended once its start has passed. */
    private fun EventTiming.Timed.hasEnded(now: Instant): Boolean {
        val endsAt = (end ?: start).toInstant()
        return !endsAt.isAfter(now)
    }
}
