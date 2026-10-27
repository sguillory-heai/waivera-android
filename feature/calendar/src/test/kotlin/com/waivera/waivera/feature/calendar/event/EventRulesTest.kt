package com.waivera.waivera.feature.calendar.event

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.Attendee
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.dto.AttendeeResponseDto
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.CalendarEventResponseDto
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.calendar.model.CelebrationType
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.WebLinks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventRulesTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val me = "usr_me"
    private val start = Instant.parse("2026-11-02T15:00:00Z")
    private val end = Instant.parse("2026-11-02T16:00:00Z")
    private val before = Instant.parse("2026-11-01T16:30:00Z")

    private fun event(
        invitation: MyInvitation = MyInvitation.PENDING,
        owner: String? = "usr_priya",
        isSprout: Boolean = false,
        online: Boolean = false,
        meetingDetails: String? = null,
        location: String? = null,
        attendees: List<Attendee> = emptyList(),
    ) = ScheduleEvent(
        id = "e1",
        title = "Care plan review",
        startAt = start,
        endAt = end,
        ownerUserId = owner,
        isSprout = isSprout,
        online = online,
        meetingDetails = meetingDetails,
        location = location,
        attendees = attendees,
        myInvitation = invitation,
    )

    @Test
    fun invitedAttendeeBeforeTheStartCanAcceptOrDecline() {
        val ui = assertNotNull(EventRules.invitation(event(), me, before, chicago))
        assertTrue(ui.canChange)
        assertEquals(MyInvitation.PENDING, ui.response)
        assertEquals(ResponsePhase.IDLE, ui.phase)
    }

    @Test
    fun ownerNeverSeesAcceptOrDecline() {
        assertNull(EventRules.invitation(event(invitation = MyInvitation.ORGANIZER), me, before, chicago))
        // Owner by backend id, even if the invitation field says otherwise.
        assertNull(EventRules.invitation(event(owner = me), me, before, chicago))
    }

    @Test
    fun startedEventHidesTheButtonsAndJoinGoverns() {
        val atStart = start.plusSeconds(60)
        val ui = assertNotNull(EventRules.invitation(event(), me, atStart, chicago))
        assertFalse(ui.canChange)
        val join = assertNotNull(EventRules.join(event(online = true), atStart, isBusy = false, error = null))
        assertEquals(JoinState.OPEN, join.state)
    }

    @Test
    fun nullAcceptanceMeansNotInvitedOrUnknownNeverPending() {
        // The Expo app showed Accept/Decline to every attendee when isInvitationAccept was null.
        val dto = CalendarEventResponseDto(
            id = "e1",
            startTime = start,
            endTime = end,
            ownerUserID = "usr_priya",
            isInvitationAccept = null,
            attendees = listOf(AttendeeResponseDto(attendeeId = "a1", type = AttendeeType.EMPLOYEE, ref = me)),
        )
        val mapped = assertNotNull(CalendarMath.event(dto, me))
        assertEquals(MyInvitation.NOT_INVITED_OR_UNKNOWN, mapped.myInvitation)
        assertNull(EventRules.invitation(mapped, me, before, chicago))

        // An explicit DECLINED status is shown as declined, not as pending.
        val declined = dto.copy(
            attendees = listOf(
                AttendeeResponseDto(
                    attendeeId = "a1",
                    type = AttendeeType.EMPLOYEE,
                    ref = me,
                    inviteStatus = InviteStatus.DECLINED,
                ),
            ),
        )
        val ui =
            assertNotNull(
                EventRules.invitation(assertNotNull(CalendarMath.event(declined, me)), me, before, chicago),
            )
        assertEquals(MyInvitation.DECLINED, ui.response)
    }

    @Test
    fun optimisticAnswerShowsAtOnceAndLocksWhileSaving() {
        val waiting = EventRules.invitation(
            event(),
            me,
            before,
            chicago,
            EventInteraction(optimistic = OptimisticResponse(true, ResponsePhase.UNDO_WINDOW)),
        )
        assertEquals(MyInvitation.ACCEPTED, waiting?.response)
        assertEquals(true, waiting?.canChange)
        val saving = EventRules.invitation(
            event(),
            me,
            before,
            chicago,
            EventInteraction(optimistic = OptimisticResponse(false, ResponsePhase.SAVING)),
        )
        assertEquals(MyInvitation.DECLINED, saving?.response)
        assertEquals(false, saving?.canChange)
        val failed = EventRules.invitation(
            event(),
            me,
            before,
            chicago,
            EventInteraction(failure = ResponseFailure(true, WaiveraError.Network())),
        )
        assertEquals(MyInvitation.PENDING, failed?.response)
        assertEquals(true, failed?.failedChoiceAccept)
    }

    @Test
    fun joinWindowOpens30MinutesBeforeTheStartAndClosesAfterTheEnd() {
        val online = event(online = true)
        assertEquals(JoinState.TOO_EARLY, EventRules.join(online, start.minusSeconds(31 * 60), false, null)?.state)
        assertEquals(JoinState.OPEN, EventRules.join(online, start.minusSeconds(29 * 60), false, null)?.state)
        assertEquals(JoinState.OPEN, EventRules.join(online, end, false, null)?.state)
        assertEquals(JoinState.ENDED, EventRules.join(online, end.plusSeconds(1), false, null)?.state)
        assertEquals(start.minusSeconds(30 * 60), EventRules.join(online, before, false, null)?.opensAt)
    }

    @Test
    fun joinOpensCountsCalendarDaysInTheSavedZoneAcrossTheFallBack() {
        // Join opens Monday 2 Nov 08:30 CST; the clocks fell back in Chicago early on Sunday 1 Nov.
        fun daysAway(now: String) = EventRules.details(
            event = event(online = true),
            zone = chicago,
            milestones = MilestoneIndex.Empty,
            now = Instant.parse(now),
            myUserId = me,
            savedZoneId = "America/Chicago",
            deviceZone = chicago,
        ).join?.opensInDays
        assertEquals(2L, daysAway("2026-11-01T04:30:00Z"), "Sat 23:30 CDT")
        assertEquals(1L, daysAway("2026-11-01T05:30:00Z"), "Sun 00:30 CDT, the DST day")
        assertEquals(1L, daysAway("2026-11-02T05:59:00Z"), "Sun 23:59 CST, a 25-hour day")
        assertEquals(0L, daysAway("2026-11-02T06:00:00Z"), "Mon 00:00 CST")
        assertEquals(7L, EventRules.daysUntil(start, LocalDate.of(2026, 10, 26), chicago))
    }

    @Test
    fun inPersonEventsWithoutALinkHaveNoJoinButton() {
        assertNull(EventRules.join(event(location = "1200 W Lake St"), before, false, null))
        assertNotNull(EventRules.join(event(meetingDetails = "https://meet.example.test/x"), before, false, null))
        assertNotNull(EventRules.join(event(isSprout = true), before, false, null))
        assertNotNull(EventRules.join(event(online = true), before, false, null))
    }

    @Test
    fun allDayEventsHaveNoJoinTimeAndAnswerUntilTheirFirstMidnight() {
        val allDay = ScheduleEvent(
            id = "a",
            online = true,
            allDayStart = LocalDate.of(2026, 11, 2),
            allDayEndExclusive = LocalDate.of(2026, 11, 3),
            myInvitation = MyInvitation.PENDING,
        )
        assertEquals(JoinState.NO_TIME, EventRules.join(allDay, before, false, null)?.state)
        assertTrue(EventRules.notStarted(allDay, Instant.parse("2026-11-02T05:59:00Z"), chicago))
        assertFalse(EventRules.notStarted(allDay, Instant.parse("2026-11-02T06:00:00Z"), chicago))
    }

    @Test
    fun meetingLinksMustBeAbsoluteWebUrls() {
        assertTrue(WebLinks.isWebUrl("https://meet.example.test/x"))
        assertTrue(WebLinks.isWebUrl("HTTP://meet.example.test"))
        assertFalse(WebLinks.isWebUrl("javascript:alert(1)"))
        assertFalse(WebLinks.isWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(WebLinks.isWebUrl("https://"))
        assertFalse(WebLinks.isWebUrl("ftp://files.example.test"))
        assertFalse(WebLinks.isWebUrl("Room 4"))
        assertFalse(WebLinks.isWebUrl(null))
    }

    @Test
    fun nonEmployeeAttendeesGetNoMilestoneBadge() {
        val milestones = MilestoneIndex(
            listOf(
                Milestone(MilestoneType.BIRTHDAY, "usr_priya", "Priya", month = 11, day = 1),
                // Same id as the participant below: must not leak onto a non-employee.
                Milestone(MilestoneType.BIRTHDAY, "p-17", "Morgan", month = 11, day = 1),
            ),
        )
        val attendees = listOf(
            Attendee(
                "1",
                AttendeeType.EMPLOYEE,
                "usr_priya",
                displayName = "Priya",
                inviteStatus = InviteStatus.ACCEPTED,
            ),
            Attendee("2", AttendeeType.WAIVER_PARTICIPANT, "p-17", displayName = "Morgan"),
            Attendee("3", AttendeeType.EMPLOYEE, me, displayName = "Me", inviteStatus = InviteStatus.INVITED),
        )
        val rows = EventRules.attendees(
            event(attendees = attendees),
            me,
            OptimisticResponse(true, ResponsePhase.UNDO_WINDOW),
            milestones,
            LocalDate.of(2026, 11, 1),
        )
        assertEquals(CelebrationType.BIRTHDAY, rows[0].celebration?.type)
        assertNull(rows[1].celebration)
        assertNull(rows[1].userId)
        assertTrue(rows[2].isMe)
        assertEquals(InviteStatus.ACCEPTED, rows[2].status)
    }

    @Test
    fun chatActionIsMeetingChatForSproutAndMessageOrganizerOtherwise() {
        assertEquals(ChatActionKind.MEETING_CHAT, EventRules.chat(event(isSprout = true), me, false)?.kind)
        assertEquals(ChatActionKind.MESSAGE_ORGANIZER, EventRules.chat(event(), me, false)?.kind)
        assertNull(EventRules.chat(event(owner = me, invitation = MyInvitation.ORGANIZER), me, false))
    }

    @Test
    fun detailsPlaceTheEventInTheSavedZone() {
        val details = EventRules.details(
            event = event(),
            zone = chicago,
            milestones = MilestoneIndex.Empty,
            now = before,
            myUserId = me,
            savedZoneId = "America/Chicago",
            deviceZone = ZoneId.of("Asia/Tokyo"),
        )
        val timing = details.timing as com.waivera.waivera.feature.calendar.model.EventTiming.Timed
        assertEquals(9, timing.start.hour)
        assertNotNull(details.zoneNotice)
        assertEquals(2026, details.currentYear)
        assertFalse(details.isOrganizer)
    }
}
