package com.waivera.waivera.feature.calendar.model

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.Holiday
import com.waivera.waivera.core.data.model.MilestoneOccurrence
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.TimeOffEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** One card in the day agenda. [key] is stable across refreshes for lazy-list keys. */
@Immutable
sealed interface AgendaItem {
    val key: String

    data class MilestoneCard(val occurrence: MilestoneOccurrence, val celebration: Celebration) : AgendaItem {
        override val key: String =
            "milestone:${occurrence.milestone.userId}:${occurrence.milestone.type}:${occurrence.date}"
    }

    data class HolidayCard(val holiday: Holiday) : AgendaItem {
        override val key: String = "holiday:${holiday.id}"
    }

    data class TimeOffCard(val entry: TimeOffEntry, val timing: EventTiming) : AgendaItem {
        override val key: String = "timeoff:${entry.id}"
    }

    data class EventCard(val event: ScheduleEvent, val timing: EventTiming) : AgendaItem {
        override val key: String = "event:${event.id}"
    }
}

/** When an event happens relative to the agenda day, already placed in the employee's zone. */
@Immutable
sealed interface EventTiming {
    /** Literal dates, never shifted by a zone; [endInclusive] equals [start] for a one-day event. */
    data class AllDay(val start: LocalDate, val endInclusive: LocalDate) : EventTiming {
        val isMultiDay: Boolean get() = endInclusive.isAfter(start)
    }

    /**
     * A timed span. [startsBeforeDay] and [endsAfterDay] say whether it continues from the
     * previous day or into the next, so overnight shifts read correctly on both days.
     */
    data class Timed(
        val start: ZonedDateTime,
        val end: ZonedDateTime?,
        val startsBeforeDay: Boolean,
        val endsAfterDay: Boolean,
    ) : EventTiming
}

/** What a grid cell marks: counts so the label can say "2 events, 1 celebration", not just a dot. */
@Immutable
data class DayMarker(val events: Int = 0, val milestones: Int = 0) {
    val isEmpty: Boolean get() = events == 0 && milestones == 0
}

/**
 * Builds the day agenda and the grid markers from a feed. Every day boundary is computed in
 * [ZoneId] (the employee's saved zone), never the device zone; an event ending exactly at local
 * midnight does not touch the next day (fixes the Expo inclusive-end test).
 */
object Agenda {
    /** Milestones, then holidays, then time off, then events (all-day first, then by start instant). */
    fun forDay(feed: ScheduleFeed, zone: ZoneId, milestones: MilestoneIndex, date: LocalDate): List<AgendaItem> {
        val milestoneCards = milestones.on(date).mapNotNull { occurrence ->
            MilestoneIndex.celebrationOf(listOf(occurrence))?.let { AgendaItem.MilestoneCard(occurrence, it) }
        }
        val holidays = feed.holidays.filter { CalendarMath.occursOn(it, date) }
            .sortedBy { it.title.orEmpty() }
            .map(AgendaItem::HolidayCard)
        val timeOff = feed.timeOff.filter { CalendarMath.occursOn(it, date, zone) }
            .sortedBy { it.startAt }
            .mapNotNull { entry ->
                val start = entry.startAt ?: return@mapNotNull null
                AgendaItem.TimeOffCard(entry, timed(start, entry.endAt, zone, date))
            }
        val events = eventsOn(feed.events, zone, date).map { AgendaItem.EventCard(it, timing(it, zone, date)) }
        return milestoneCards + holidays + timeOff + events
    }

    /** Events touching [date], sorted by parsed instant (never by string), all-day first. */
    fun eventsOn(events: List<ScheduleEvent>, zone: ZoneId, date: LocalDate): List<ScheduleEvent> =
        events.filter { CalendarMath.occursOn(it, date, zone) }
            .sortedWith(
                compareBy<ScheduleEvent>({ !it.isAllDay }, { it.allDayStart }, { it.startAt }, { it.title.orEmpty() }),
            )

    /** Event and milestone counts for each of [cells], bucketing each event once. */
    fun markers(
        feed: ScheduleFeed,
        zone: ZoneId,
        milestones: MilestoneIndex,
        cells: List<LocalDate>,
    ): Map<LocalDate, DayMarker> {
        if (cells.isEmpty()) return emptyMap()
        val first = cells.first()
        val last = cells.last()
        val eventCounts = HashMap<LocalDate, Int>()
        feed.events.forEach { event ->
            val span = daysOf(event, zone) ?: return@forEach
            var day = maxOf(span.start, first)
            val end = minOf(span.endInclusive, last)
            while (!day.isAfter(end)) {
                eventCounts[day] = (eventCounts[day] ?: 0) + 1
                day = day.plusDays(1)
            }
        }
        return cells.associateWith { day ->
            DayMarker(events = eventCounts[day] ?: 0, milestones = milestones.on(day).size)
        }
    }

    fun timing(event: ScheduleEvent, zone: ZoneId, date: LocalDate): EventTiming {
        val allDayStart = event.allDayStart
        if (allDayStart != null) {
            val endInclusive = event.allDayEndExclusive?.takeIf { it.isAfter(allDayStart) }?.minusDays(1)
                ?: allDayStart
            return EventTiming.AllDay(allDayStart, endInclusive)
        }
        val start = requireNotNull(event.startAt) { "timed event without a start" }
        return timed(start, event.endAt, zone, date)
    }

    private fun timed(start: java.time.Instant, end: java.time.Instant?, zone: ZoneId, date: LocalDate) =
        CalendarMath.daysTouched(start, end, zone).let { days ->
            EventTiming.Timed(
                start = start.atZone(zone),
                end = end?.takeIf { it.isAfter(start) }?.atZone(zone),
                startsBeforeDay = days.start.isBefore(date),
                endsAfterDay = days.endInclusive.isAfter(date),
            )
        }

    private fun daysOf(event: ScheduleEvent, zone: ZoneId): ClosedRange<LocalDate>? {
        val allDayStart = event.allDayStart
        if (allDayStart != null) {
            val endInclusive = event.allDayEndExclusive?.takeIf { it.isAfter(allDayStart) }?.minusDays(1)
                ?: allDayStart
            return allDayStart..endInclusive
        }
        val start = event.startAt ?: return null
        return CalendarMath.daysTouched(start, event.endAt, zone)
    }
}
