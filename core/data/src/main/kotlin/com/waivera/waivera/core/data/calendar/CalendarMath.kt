package com.waivera.waivera.core.data.calendar

import com.waivera.waivera.core.data.model.Attendee
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Holiday
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MilestoneOccurrence
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.TimeOffEntry
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.CalendarEventResponseDto
import com.waivera.waivera.core.network.dto.CalendarFeedResponseDto
import com.waivera.waivera.core.network.dto.CalendarEmployeeMilestoneResponseDto
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MilestoneType
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

/**
 * Calendar rules from dates.md and 07-calendar.md. Weeks start on Sunday; ranges are half-open
 * `[start, endExclusive)` days in the employee's zone; all-day values are literal dates that a
 * zone never shifts. No RRULE expansion happens here: the server returns occurrences.
 */
object CalendarMath {
    private const val GRID_DAYS = 42L
    private const val WEEK_DAYS = 7L
    private const val FEBRUARY = 2
    private const val LEAP_DAY = 29
    private const val JOIN_LEAD_MINUTES = 30L
    private val JOIN_LEAD: Duration = Duration.ofMinutes(JOIN_LEAD_MINUTES)

    fun startOfWeek(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value % DayOfWeek.entries.size).toLong())

    fun day(date: LocalDate): DateRange = DateRange(date, date.plusDays(1))

    fun week(date: LocalDate): DateRange = startOfWeek(date).let { DateRange(it, it.plusDays(WEEK_DAYS)) }

    /** Always 42 cells from the Sunday on or before the 1st, so spill days get their events too. */
    fun monthGrid(month: YearMonth): DateRange = startOfWeek(month.atDay(1)).let { DateRange(it, it.plusDays(GRID_DAYS)) }

    /** The instants bounding [range] in [zone]; DST days are 23 or 25 hours long. */
    fun window(range: DateRange, zone: ZoneId): Pair<Instant, Instant> =
        range.start.atStartOfDay(zone).toInstant() to range.endExclusive.atStartOfDay(zone).toInstant()

    /** Days a timed span touches in [zone]; an end exactly at midnight does not touch the next day. */
    fun daysTouched(start: Instant, end: Instant?, zone: ZoneId): ClosedRange<LocalDate> {
        val first = start.atZone(zone).toLocalDate()
        val last = end?.takeIf { it.isAfter(start) }?.minusNanos(1)?.atZone(zone)?.toLocalDate() ?: first
        return first..last
    }

    @Suppress("ReturnCount")
    fun occursOn(event: ScheduleEvent, date: LocalDate, zone: ZoneId): Boolean {
        val allDayStart = event.allDayStart
        if (allDayStart != null) {
            val end = event.allDayEndExclusive?.takeIf { it.isAfter(allDayStart) } ?: allDayStart.plusDays(1)
            return !date.isBefore(allDayStart) && date.isBefore(end)
        }
        val start = event.startAt ?: return false
        return date in daysTouched(start, event.endAt, zone)
    }

    fun occursOn(entry: TimeOffEntry, date: LocalDate, zone: ZoneId): Boolean {
        val start = entry.startAt ?: return false
        return date in daysTouched(start, entry.endAt, zone)
    }

    fun occursOn(holiday: Holiday, date: LocalDate): Boolean =
        !date.isBefore(holiday.start) && !date.isAfter(holiday.endInclusive)

    /** Events on [date]: all-day first, then by start time. */
    fun eventsOn(feed: ScheduleFeed, date: LocalDate): List<ScheduleEvent> {
        val zone = ZoneId.of(feed.zoneId)
        return feed.events.filter { occursOn(it, date, zone) }
            .sortedWith(compareBy<ScheduleEvent>({ !it.isAllDay }, { it.startAt }, { it.title }))
    }

    /** Resolves milestones on [date]: Feb 29 falls on Feb 28 in common years; anniversaries need 1+ years. */
    fun milestonesOn(milestones: List<Milestone>, date: LocalDate): List<MilestoneOccurrence> =
        milestones.mapNotNull { resolve(it, date) }

    fun resolve(milestone: Milestone, date: LocalDate): MilestoneOccurrence? {
        val effectiveDay = if (milestone.month == FEBRUARY && milestone.day == LEAP_DAY && !Year.isLeap(date.year.toLong())) {
            LEAP_DAY - 1
        } else {
            milestone.day
        }
        if (milestone.month != date.monthValue || effectiveDay != date.dayOfMonth) return null
        return when (milestone.type) {
            MilestoneType.BIRTHDAY -> MilestoneOccurrence(milestone, date, null)
            MilestoneType.WORK_ANNIVERSARY -> {
                val years = milestone.startYear?.let { date.year - it }
                if (years == null || years < 1) null else MilestoneOccurrence(milestone, date, years)
            }
            MilestoneType.UNKNOWN -> null
        }
    }

    @Suppress("ReturnCount")
    fun joinWindowOpen(event: ScheduleEvent, now: Instant): Boolean {
        val start = event.startAt ?: return false
        val end = event.endAt ?: return false
        return !now.isBefore(start.minus(JOIN_LEAD)) && !now.isAfter(end)
    }

    /** Accept/Decline are offered only when the API says INVITED and the event has not started. */
    fun canRespond(event: ScheduleEvent, now: Instant): Boolean =
        event.myInvitation == MyInvitation.PENDING && event.startAt?.let(now::isBefore) != false

    @Suppress("ReturnCount")
    fun myInvitation(dto: CalendarEventResponseDto, myUserId: String?): MyInvitation {
        if (myUserId == null) return MyInvitation.NOT_INVITED_OR_UNKNOWN
        if (dto.ownerUserID == myUserId || dto.createdby == myUserId) return MyInvitation.ORGANIZER
        val me = dto.attendees.firstOrNull { it.type == AttendeeType.EMPLOYEE && it.ref == myUserId }
            ?: return MyInvitation.NOT_INVITED_OR_UNKNOWN
        return when (me.inviteStatus) {
            InviteStatus.INVITED -> MyInvitation.PENDING
            InviteStatus.ACCEPTED -> MyInvitation.ACCEPTED
            InviteStatus.DECLINED, InviteStatus.CANCELLED -> MyInvitation.DECLINED
            InviteStatus.TENTATIVE -> MyInvitation.TENTATIVE
            // A null isInvitationAccept means "not invited or unknown", never pending (PLAN parity fix).
            InviteStatus.UNKNOWN -> when (dto.isInvitationAccept) {
                true -> MyInvitation.ACCEPTED
                false -> MyInvitation.DECLINED
                null -> MyInvitation.NOT_INVITED_OR_UNKNOWN
            }
        }
    }

    @Suppress("ReturnCount")
    fun event(dto: CalendarEventResponseDto, myUserId: String?): ScheduleEvent? {
        val id = dto.id?.takeIf(String::isNotBlank) ?: return null
        if (dto.allDayStart == null && dto.startTime == null) return null
        return ScheduleEvent(
            id = id,
            title = dto.title,
            description = dto.description,
            location = dto.location,
            meetingDetails = dto.meetingDetails,
            startAt = dto.startTime,
            endAt = dto.endTime,
            allDayStart = dto.allDayStart,
            allDayEndExclusive = dto.allDayEndExclusive,
            ownerUserId = dto.ownerUserID ?: dto.createdby,
            ownerName = dto.ownerName,
            ownerColor = dto.ownerColor,
            attendees = dto.attendees.mapNotNull { a ->
                a.attendeeId?.let { Attendee(it, a.type, a.ref, a.email, a.displayName, a.inviteStatus, a.respondedAt) }
            },
            online = dto.online,
            isSprout = dto.isSprout,
            meetingType = dto.meetingType,
            recurrenceRule = dto.recurrenceRule,
            parentEventId = dto.parentEventId,
            taskId = dto.taskId,
            taskTitle = dto.taskTitle,
            myInvitation = myInvitation(dto, myUserId),
        )
    }

    fun feed(dto: CalendarFeedResponseDto, range: DateRange, zone: ZoneId, myUserId: String?): ScheduleFeed = ScheduleFeed(
        range = range,
        zoneId = zone.id,
        events = dto.events.mapNotNull { event(it, myUserId) },
        timeOff = dto.timeOff.filter { it.startTime != null }.map {
            TimeOffEntry(
                it.id, it.userId, it.startTime, it.endTime, it.leaveType, it.requestType,
                it.durationMinutes, it.status, it.isCancellationPending,
            )
        },
        holidays = dto.holidays.mapNotNull { h ->
            val start = h.startDate ?: return@mapNotNull null
            // Holiday end dates are exclusive like all-day events (Thanksgiving is 26 to 27).
            Holiday(h.id, h.title, start, h.endDate?.takeIf { it.isAfter(start) }?.minusDays(1) ?: start)
        },
    )

    @Suppress("ReturnCount")
    fun milestone(dto: CalendarEmployeeMilestoneResponseDto): Milestone? {
        val userId = dto.userId ?: return null
        if (dto.type == MilestoneType.UNKNOWN) return null
        // Validates month/day (MonthDay accepts Feb 29) so a bad row cannot break the grid.
        runCatching { MonthDay.of(dto.month, dto.day) }.getOrNull() ?: return null
        return Milestone(dto.type, userId, dto.displayName, dto.avatarUrl, dto.roles, dto.month, dto.day, dto.startYear)
    }
}
