package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The hero's stats and the "Your day" track. Day and week boundaries are local midnights in the
 * employee's saved zone, turned into instants with the zone's own rules, so a 23- or 25-hour day
 * is measured as it really runs (`sprout_api/docs/timezone-standard.md`).
 */
internal object DayRules {
    const val DEFAULT_START_HOUR = 8
    const val DEFAULT_END_HOUR = 18
    const val LABEL_EVERY_HOURS = 2
    private const val HOURS_PER_DAY = 24
    private const val TONES = 3

    /** Events the person is going to: not an unanswered invitation, not one they declined. */
    fun attending(feed: ScheduleFeed?): List<ScheduleEvent> = feed?.events.orEmpty().filter {
        it.myInvitation !in
            SKIPPED
    }

    fun stats(
        conversations: Synced<List<Conversation>>,
        schedule: Synced<ScheduleFeed>?,
        zone: ZoneId,
        today: LocalDate,
    ): TodayStats {
        val chatsReady = conversations.hasContent() || conversations.lastSyncedAt != null
        val scheduleReady = schedule != null && (schedule.hasContent() || schedule.lastSyncedAt != null)
        val events = attending(schedule?.data)
        val weekStart = CalendarMath.startOfWeek(today)
        return TodayStats(
            eventsToday = events.count { overlaps(it, today, today.plusDays(1), zone) }.takeIf { scheduleReady },
            unreadChats = conversations.data.count { it.unreadCount > 0 }.takeIf { chatsReady },
            eventsThisWeek = events.count { overlaps(it, weekStart, weekStart.plusDays(WEEK_DAYS), zone) }
                .takeIf { scheduleReady },
        )
    }

    /** Whether [event] covers any of `[from, until)`, local days in [zone]. */
    fun overlaps(event: ScheduleEvent, from: LocalDate, until: LocalDate, zone: ZoneId): Boolean {
        val allDayStart = event.allDayStart
        val start = event.startAt
        return when {
            allDayStart != null -> {
                val end = event.allDayEndExclusive?.takeIf { it.isAfter(allDayStart) } ?: allDayStart.plusDays(1)
                allDayStart.isBefore(until) && end.isAfter(from)
            }
            start != null -> {
                val rangeStart = from.atStartOfDay(zone).toInstant()
                val rangeEnd = until.atStartOfDay(zone).toInstant()
                val end = event.endAt?.takeIf { it.isAfter(start) }
                if (end == null) {
                    !start.isBefore(rangeStart) && start.isBefore(rangeEnd)
                } else {
                    start.isBefore(rangeEnd) && end.isAfter(rangeStart)
                }
            }
            else -> false
        }
    }

    /** Today's track, or null until the schedule has loaded. All-day items stay off the track. */
    fun timeline(schedule: Synced<ScheduleFeed>?, zone: ZoneId, today: LocalDate, now: Instant): DayTimeline? {
        if (schedule == null || !(schedule.hasContent() || schedule.lastSyncedAt != null)) return null
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val spans = attending(schedule.data)
            .filter { !it.isAllDay && it.startAt != null && overlaps(it, today, today.plusDays(1), zone) }
            .mapNotNull { event ->
                val start = event.startAt ?: return@mapNotNull null
                val end = event.endAt?.takeIf { it.isAfter(start) } ?: start
                Span(event.id, maxOf(start, dayStart), minOf(end, dayEnd))
            }
            .sortedWith(compareBy<Span>({ it.start }, { it.end }))
        val startHour = minOf(
            DEFAULT_START_HOUR,
            spans.minOfOrNull { floorHour(it.start, zone, dayStart) } ?: HOURS_PER_DAY,
        )
        val endHour = maxOf(DEFAULT_END_HOUR, spans.maxOfOrNull { ceilHour(it.end, zone, dayEnd) } ?: 0)
        val window = Window(hourAt(today, startHour, zone, dayEnd), hourAt(today, endHour, zone, dayEnd))
        return DayTimeline(
            windowStart = LocalTime.of(startHour % HOURS_PER_DAY, 0),
            windowEnd = LocalTime.of(endHour % HOURS_PER_DAY, 0),
            blocks = spans.mapIndexed { index, span ->
                DayBlock(
                    eventId = span.id,
                    start = window.at(span.start),
                    end = window.at(span.end),
                    tone =
                    index % TONES,
                )
            },
            ticks = (startHour + 1 until endHour).map { window.at(hourAt(today, it, zone, dayEnd)) },
            labels = (startHour..endHour step LABEL_EVERY_HOURS).map {
                DayHourLabel(
                    at = window.at(hourAt(today, it, zone, dayEnd)),
                    time = LocalTime.of(it % HOURS_PER_DAY, 0),
                )
            },
            now = window.at(now).takeIf { !now.isBefore(window.start) && !now.isAfter(window.end) },
            eventCount = spans.size,
            scheduled = covered(spans),
        )
    }

    /** The instant of a whole local hour today; hour 24 is the next local midnight. */
    private fun hourAt(today: LocalDate, hour: Int, zone: ZoneId, dayEnd: Instant): Instant =
        if (hour >= HOURS_PER_DAY) dayEnd else today.atTime(hour, 0).atZone(zone).toInstant()

    private fun floorHour(at: Instant, zone: ZoneId, dayStart: Instant): Int =
        if (!at.isAfter(dayStart)) 0 else at.atZone(zone).hour

    private fun ceilHour(at: Instant, zone: ZoneId, dayEnd: Instant): Int {
        if (!at.isBefore(dayEnd)) return HOURS_PER_DAY
        val local = at.atZone(zone).toLocalTime()
        return if (local.minute == 0 && local.second == 0 && local.nano == 0) local.hour else local.hour + 1
    }

    /** The union of the spans' durations: two overlapping visits count their shared hour once. */
    private fun covered(spans: List<Span>): Duration {
        var total = Duration.ZERO
        var runStart: Instant? = null
        var runEnd: Instant? = null
        for (span in spans) {
            val end = runEnd
            if (end == null || span.start.isAfter(end)) {
                runStart?.let { start -> total += Duration.between(start, checkNotNull(end)) }
                runStart = span.start
                runEnd = span.end
            } else if (span.end.isAfter(end)) {
                runEnd = span.end
            }
        }
        runStart?.let { start -> total += Duration.between(start, checkNotNull(runEnd)) }
        return total
    }

    private data class Span(val id: String, val start: Instant, val end: Instant)

    private class Window(val start: Instant, val end: Instant) {
        private val length = Duration.between(start, end).toMillis().coerceAtLeast(1).toFloat()

        fun at(instant: Instant): Float = (Duration.between(start, instant).toMillis() / length).coerceIn(0f, 1f)
    }

    private const val WEEK_DAYS = 7L
    private val SKIPPED = setOf(MyInvitation.PENDING, MyInvitation.DECLINED)
}
