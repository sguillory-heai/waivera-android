package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.dto.MilestoneType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedule rules for Today: pending invitations, the next three items and today's celebrations.
 * Day boundaries and clock times use the employee's saved zone from [TodayContext.zone].
 */
internal object ScheduleRules {
    fun invitations(feed: ScheduleFeed?, context: TodayContext, today: LocalDate): List<NeedsYouItem> =
        feed?.events.orEmpty()
            .filter { CalendarMath.canRespond(it, context.now) && it.id !in context.local.answeredInvitations }
            .sortedWith(compareBy(nullsLast<Instant>()) { it.startAt })
            .map { event ->
                NeedsYouItem.Invitation(
                    eventId = event.id,
                    title = event.title?.takeIf(String::isNotBlank),
                    organizer = event.ownerName?.takeIf(String::isNotBlank),
                    time = upcomingLabel(event, context, today),
                )
            }

    fun nextUp(schedule: Synced<ScheduleFeed>?, context: TodayContext, today: LocalDate): NextUpSection {
        if (schedule == null) return NextUpSection()
        val zone = context.zone
        val items = schedule.data.events
            .filter { it.myInvitation !in HIDDEN_FROM_NEXT_UP && !hasEnded(it, context.now, today) }
            .sortedWith(compareBy<ScheduleEvent>({ firstDay(it, zone, today) }, { !it.isAllDay }, { it.startAt }))
            .take(NextUpSection.COUNT)
            .mapNotNull { event -> nextUpItem(event, context, today) }
        val ready = schedule.hasContent() || schedule.lastSyncedAt != null
        return NextUpSection(items = items, isReady = ready, failed = !ready && schedule.error != null)
    }

    private fun nextUpItem(event: ScheduleEvent, context: TodayContext, today: LocalDate): NextUpItem? {
        val time = upcomingLabel(event, context, today) ?: return null
        val start = event.startAt?.atZone(context.zone)
        val end = event.endAt?.atZone(context.zone)
        return NextUpItem(
            eventId = event.id,
            title = event.title?.takeIf(String::isNotBlank),
            time = time,
            start = start?.toLocalTime().takeIf { !event.isAllDay },
            end = end?.toLocalTime().takeIf { !event.isAllDay },
            endsOnLaterDay = !event.isAllDay && endsOnLaterDay(start, end),
            location = event.location?.takeIf(String::isNotBlank),
            isOnline = event.online,
        )
    }

    private fun upcomingLabel(event: ScheduleEvent, context: TodayContext, today: LocalDate): UpcomingLabel? {
        val allDayStart = event.allDayStart
        val start = event.startAt
        return when {
            allDayStart != null -> TimeLabels.allDay(allDayStart, event.allDayEndExclusive, today)
            start != null -> TimeLabels.upcoming(start, event.endAt, context.now, context.zone)
            else -> null
        }
    }

    /** An overnight shift ends on a later day; an end at exactly midnight still belongs to the start day. */
    private fun endsOnLaterDay(start: ZonedDateTime?, end: ZonedDateTime?): Boolean {
        if (start == null || end == null) return false
        val lastDay = if (end.toLocalTime() == LocalTime.MIDNIGHT) end.toLocalDate().minusDays(1) else end.toLocalDate()
        return lastDay.isAfter(start.toLocalDate())
    }

    /** All-day items end when their last day ends; timed items when their end instant passes. */
    private fun hasEnded(event: ScheduleEvent, now: Instant, today: LocalDate): Boolean {
        val allDayStart = event.allDayStart
        val start = event.startAt
        return when {
            allDayStart != null -> {
                val end = event.allDayEndExclusive?.takeIf { it.isAfter(allDayStart) } ?: allDayStart.plusDays(1)
                !today.isBefore(end)
            }
            start != null -> !(event.endAt?.takeIf { it.isAfter(start) } ?: start).isAfter(now)
            else -> true
        }
    }

    /** The first day the item shows on, from today on; all-day items that started earlier count as today. */
    private fun firstDay(event: ScheduleEvent, zone: ZoneId, today: LocalDate): LocalDate {
        val day = event.allDayStart ?: event.startAt?.atZone(zone)?.toLocalDate() ?: today
        return if (day.isBefore(today)) today else day
    }

    fun celebrations(milestones: List<Milestone>, today: LocalDate): List<CelebrationItem> =
        CalendarMath.milestonesOn(milestones, today)
            .groupBy { it.milestone.userId }
            .map { (userId, occurrences) ->
                CelebrationItem(
                    userId = userId,
                    name = occurrences.firstNotNullOfOrNull { it.milestone.displayName?.takeIf(String::isNotBlank) },
                    isBirthday = occurrences.any { it.milestone.type == MilestoneType.BIRTHDAY },
                    anniversaryYears = occurrences.firstNotNullOfOrNull { it.years },
                )
            }
            .sortedBy { it.name.orEmpty().lowercase() }

    private val HIDDEN_FROM_NEXT_UP = setOf(MyInvitation.PENDING, MyInvitation.DECLINED)
}
