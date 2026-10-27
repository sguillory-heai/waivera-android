package com.waivera.waivera.feature.calendar.event

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.calendar.model.Agenda
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.ZoneNotice
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A local answer that the server does not know about yet (or has just confirmed). */
data class OptimisticResponse(val accept: Boolean, val phase: ResponsePhase)

/** A failed save, kept so the screen can offer Retry for the same choice. */
data class ResponseFailure(val accept: Boolean, val error: WaiveraError)

/** What the person is doing on the screen right now, layered over the server's event. */
data class EventInteraction(
    val optimistic: OptimisticResponse? = null,
    val failure: ResponseFailure? = null,
    val joinBusy: Boolean = false,
    val joinError: JoinError? = null,
    val chatBusy: Boolean = false,
)

/**
 * Pure rules for the event screen (07 §7), kept apart from the ViewModel so each acceptance
 * check has a direct test.
 */
object EventRules {
    private val JOIN_LEAD: Duration = Duration.ofMinutes(30)

    /** Answers an invited attendee can give; organizers and unknowns never see buttons. */
    private val ANSWERABLE = setOf(
        MyInvitation.PENDING,
        MyInvitation.ACCEPTED,
        MyInvitation.DECLINED,
        MyInvitation.TENTATIVE,
    )

    /** The whole screen model; every value is placed in [zone], the employee's saved zone. */
    @Suppress("LongParameterList")
    fun details(
        event: ScheduleEvent,
        zone: ZoneId,
        milestones: MilestoneIndex,
        now: Instant,
        myUserId: String?,
        savedZoneId: String?,
        deviceZone: ZoneId,
        interaction: EventInteraction = EventInteraction(),
        isRefreshing: Boolean = false,
        refreshError: WaiveraError? = null,
    ): EventDetails {
        val today = now.atZone(zone).toLocalDate()
        val anchor = event.allDayStart ?: event.startAt?.atZone(zone)?.toLocalDate() ?: today
        val organizer = event.ownerUserId?.let { PersonRow(it, event.ownerName, milestones.celebrationFor(it, today)) }
            ?: event.ownerName?.let { PersonRow(null, it, null) }
        return EventDetails(
            event = event,
            zone = zone,
            zoneNotice = ZoneNotice.of(zone, savedZoneId, deviceZone),
            timing = Agenda.timing(event, zone, anchor),
            currentYear = today.year,
            isOrganizer = isOrganizer(event, myUserId),
            organizer = organizer,
            attendees = attendees(event, myUserId, interaction.optimistic, milestones, today),
            invitation = invitation(event, myUserId, now, zone, interaction),
            join = join(event, now, interaction.joinBusy, interaction.joinError)
                ?.let { it.copy(opensInDays = it.opensAt?.let { at -> daysUntil(at, today, zone) }) },
            chat = chat(event, myUserId, interaction.chatBusy),
            isRefreshing = isRefreshing,
            refreshError = refreshError,
        )
    }

    fun isOrganizer(event: ScheduleEvent, myUserId: String?): Boolean =
        event.myInvitation == MyInvitation.ORGANIZER || (myUserId != null && event.ownerUserId == myUserId)

    /** True until the event starts: the start instant, or local midnight of an all-day start in [zone]. */
    fun notStarted(event: ScheduleEvent, now: Instant, zone: ZoneId): Boolean {
        val start = event.startAt ?: event.allDayStart?.atStartOfDay(zone)?.toInstant() ?: return true
        return now.isBefore(start)
    }

    /**
     * Invitation block, or null when there is nothing to answer. Driven by the API's explicit
     * invite status: a null `isInvitationAccept` is "not invited or unknown", never pending
     * (PLAN parity fix; the Expo app showed Accept/Decline to everyone in that case).
     */
    fun invitation(
        event: ScheduleEvent,
        myUserId: String?,
        now: Instant,
        zone: ZoneId,
        interaction: EventInteraction = EventInteraction(),
    ): InvitationUi? {
        if (isOrganizer(event, myUserId)) return null
        val server = event.myInvitation
        if (server !in ANSWERABLE) return null
        val optimistic = interaction.optimistic
        val failure = interaction.failure
        val phase = optimistic?.phase ?: ResponsePhase.IDLE
        val shown = optimistic?.let { if (it.accept) MyInvitation.ACCEPTED else MyInvitation.DECLINED } ?: server
        return InvitationUi(
            response = shown,
            canChange = notStarted(event, now, zone) && phase != ResponsePhase.SAVING,
            phase = phase,
            failure = failure?.error,
            failedChoiceAccept = failure?.accept,
        )
    }

    /** Join button state, or null for events with nothing to join (in person, no link). */
    fun join(event: ScheduleEvent, now: Instant, isBusy: Boolean, error: JoinError?): JoinUi? {
        if (!event.isSprout && !event.online && event.joinLink == null) return null
        val start = event.startAt
        val end = event.endAt
        val state = when {
            start == null || end == null -> JoinState.NO_TIME
            CalendarMath.joinWindowOpen(event, now) -> JoinState.OPEN
            now.isBefore(start.minus(JOIN_LEAD)) -> JoinState.TOO_EARLY
            else -> JoinState.ENDED
        }
        return JoinUi(state, start?.minus(JOIN_LEAD), isBusy, error)
    }

    /** Calendar days from [today] to [instant]'s date in [zone]; DST-safe because it counts dates, not hours. */
    fun daysUntil(instant: Instant, today: LocalDate, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(today, instant.atZone(zone).toLocalDate())

    fun chat(event: ScheduleEvent, myUserId: String?, isBusy: Boolean): ChatAction? = when {
        event.isSprout -> ChatAction(ChatActionKind.MEETING_CHAT, isBusy)
        !isOrganizer(event, myUserId) && !event.ownerUserId.isNullOrBlank() ->
            ChatAction(ChatActionKind.MESSAGE_ORGANIZER, isBusy)
        else -> null
    }

    /**
     * Attendee rows. My own row shows the optimistic answer; only employees get a milestone
     * badge, so nothing is computed for participants or external guests.
     */
    fun attendees(
        event: ScheduleEvent,
        myUserId: String?,
        optimistic: OptimisticResponse?,
        milestones: MilestoneIndex,
        today: LocalDate,
    ): List<AttendeeRow> = event.attendees.map { attendee ->
        val isEmployee = attendee.type == AttendeeType.EMPLOYEE
        val isMe = isEmployee && myUserId != null && attendee.userId == myUserId
        AttendeeRow(
            id = attendee.id,
            userId = attendee.userId.takeIf { isEmployee },
            name = attendee.displayName?.takeIf(String::isNotBlank),
            email = attendee.email?.takeIf(String::isNotBlank),
            status = if (isMe && optimistic != null) statusFor(optimistic.accept) else attendee.inviteStatus,
            celebration = if (isEmployee) milestones.celebrationFor(attendee.userId, today) else null,
            isMe = isMe,
        )
    }

    private fun statusFor(accept: Boolean) = if (accept) InviteStatus.ACCEPTED else InviteStatus.DECLINED
}
