package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.calendar.CalendarMath
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.network.dto.AttendeeResponseDto
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.CalendarEventResponseDto
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class CalendarTest {
    private val chicago = ZoneId.of("America/Chicago")
    private val testScope = TestScope()
    private val graph = TestGraph(testScope.backgroundScope)

    @AfterTest
    fun tearDown() = graph.close()

    @Test
    fun monthGridIs42CellsFromTheSundayOnOrBeforeThe1st() {
        val october = CalendarMath.monthGrid(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 9, 27), october.start)
        assertEquals(42, october.days().count())
        assertEquals(java.time.DayOfWeek.SUNDAY, october.start.dayOfWeek)
        // A month starting on Sunday starts on its own 1st.
        assertEquals(LocalDate.of(2026, 11, 1), CalendarMath.monthGrid(YearMonth.of(2026, 11)).start)
        assertEquals(LocalDate.of(2026, 10, 25), CalendarMath.week(LocalDate.of(2026, 10, 31)).start)
    }

    @Test
    fun dstDaysAre23And25HoursInChicago() {
        val (springStart, springEnd) = CalendarMath.window(CalendarMath.day(LocalDate.of(2026, 3, 8)), chicago)
        assertEquals(Duration.ofHours(23), Duration.between(springStart, springEnd))
        val (fallStart, fallEnd) = CalendarMath.window(CalendarMath.day(LocalDate.of(2026, 11, 1)), chicago)
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), fallStart)
        assertEquals(Duration.ofHours(25), Duration.between(fallStart, fallEnd))
    }

    @Test
    fun overnightEventAcrossFallBackTouchesBothDays() {
        // 22:00 CDT on Oct 31 to 06:00 CST on Nov 1 is nine hours because of the repeated hour.
        val event = ScheduleEvent(
            id = "overnight",
            startAt = Instant.parse("2026-11-01T03:00:00Z"),
            endAt = Instant.parse("2026-11-01T12:00:00Z"),
        )
        assertTrue(CalendarMath.occursOn(event, LocalDate.of(2026, 10, 31), chicago))
        assertTrue(CalendarMath.occursOn(event, LocalDate.of(2026, 11, 1), chicago))
        assertFalse(CalendarMath.occursOn(event, LocalDate.of(2026, 11, 2), chicago))
        // In UTC the same event sits on one day only: the zone matters.
        assertFalse(CalendarMath.occursOn(event, LocalDate.of(2026, 10, 31), ZoneId.of("UTC")))
    }

    @Test
    fun eventEndingAtMidnightDoesNotSpillIntoTheNextDay() {
        val event = ScheduleEvent(id = "e", startAt = Instant.parse("2026-03-08T05:00:00Z"), endAt = Instant.parse("2026-03-09T05:00:00Z"))
        assertTrue(CalendarMath.occursOn(event, LocalDate.of(2026, 3, 8), chicago))
        assertFalse(CalendarMath.occursOn(event, LocalDate.of(2026, 3, 9), chicago))
    }

    @Test
    fun allDayEventsUseLiteralDatesInAnyZone() {
        val event = ScheduleEvent(id = "a", allDayStart = LocalDate.of(2026, 11, 5), allDayEndExclusive = LocalDate.of(2026, 11, 6))
        listOf(chicago, ZoneId.of("Pacific/Auckland"), ZoneId.of("UTC")).forEach { zone ->
            assertTrue(CalendarMath.occursOn(event, LocalDate.of(2026, 11, 5), zone))
            assertFalse(CalendarMath.occursOn(event, LocalDate.of(2026, 11, 6), zone))
            assertFalse(CalendarMath.occursOn(event, LocalDate.of(2026, 11, 4), zone))
        }
    }

    @Test
    fun allDayEventsSortFirst() {
        val feed = ScheduleFeed(
            CalendarMath.day(LocalDate.of(2026, 11, 5)),
            chicago.id,
            events = listOf(
                ScheduleEvent(id = "t", startAt = Instant.parse("2026-11-05T15:00:00Z"), endAt = Instant.parse("2026-11-05T16:00:00Z")),
                ScheduleEvent(id = "a", allDayStart = LocalDate.of(2026, 11, 5)),
            ),
        )
        assertEquals(listOf("a", "t"), CalendarMath.eventsOn(feed, LocalDate.of(2026, 11, 5)).map { it.id })
    }

    @Test
    fun leapDayBirthdaysFallOnFeb28InCommonYears() {
        val leapling = Milestone(MilestoneType.BIRTHDAY, "u", month = 2, day = 29)
        assertEquals(LocalDate.of(2027, 2, 28), CalendarMath.resolve(leapling, LocalDate.of(2027, 2, 28))?.date)
        assertNull(CalendarMath.resolve(leapling, LocalDate.of(2028, 2, 28)))
        assertEquals(LocalDate.of(2028, 2, 29), CalendarMath.resolve(leapling, LocalDate.of(2028, 2, 29))?.date)
    }

    @Test
    fun anniversariesNeedAtLeastOneYear() {
        val hire = Milestone(MilestoneType.WORK_ANNIVERSARY, "u", month = 3, day = 4, startYear = 2024)
        assertNull(CalendarMath.resolve(hire, LocalDate.of(2024, 3, 4)))
        assertEquals(2, CalendarMath.resolve(hire, LocalDate.of(2026, 3, 4))?.years)
        assertNull(CalendarMath.resolve(hire.copy(startYear = null), LocalDate.of(2026, 3, 4)))
    }

    @Test
    fun invitationStateComesFromInviteStatusNotNullAcceptance() {
        val base = CalendarEventResponseDto(id = "e", ownerUserID = "owner", startTime = Instant.parse("2026-11-03T15:00:00Z"))
        fun me(status: InviteStatus) = AttendeeResponseDto(attendeeId = "1", type = AttendeeType.EMPLOYEE, ref = "me", inviteStatus = status)
        assertEquals(MyInvitation.PENDING, CalendarMath.myInvitation(base.copy(attendees = listOf(me(InviteStatus.INVITED))), "me"))
        assertEquals(MyInvitation.ACCEPTED, CalendarMath.myInvitation(base.copy(attendees = listOf(me(InviteStatus.ACCEPTED))), "me"))
        assertEquals(MyInvitation.DECLINED, CalendarMath.myInvitation(base.copy(attendees = listOf(me(InviteStatus.DECLINED))), "me"))
        assertEquals(MyInvitation.NOT_INVITED_OR_UNKNOWN, CalendarMath.myInvitation(base, "me"), "null acceptance is not pending")
        assertEquals(MyInvitation.ORGANIZER, CalendarMath.myInvitation(base, "owner"))
        val pending = assertIs<ScheduleEvent>(CalendarMath.event(base.copy(attendees = listOf(me(InviteStatus.INVITED))), "me"))
        assertTrue(CalendarMath.canRespond(pending, Instant.parse("2026-11-03T14:00:00Z")))
        assertFalse(CalendarMath.canRespond(pending, Instant.parse("2026-11-03T15:00:01Z")), "not once started")
    }

    @Test
    fun joinWindowOpensThirtyMinutesEarly() {
        val event = ScheduleEvent(id = "e", startAt = Instant.parse("2026-11-03T15:00:00Z"), endAt = Instant.parse("2026-11-03T16:00:00Z"))
        assertFalse(CalendarMath.joinWindowOpen(event, Instant.parse("2026-11-03T14:29:59Z")))
        assertTrue(CalendarMath.joinWindowOpen(event, Instant.parse("2026-11-03T14:30:00Z")))
        assertFalse(CalendarMath.joinWindowOpen(event, Instant.parse("2026-11-03T16:00:01Z")))
    }

    @Test
    fun refreshQueriesTheGridInTheEmployeeZoneAndMapsTheFixture() = testScope.runTest {
        graph.server.fixture("GET /api/CalendarEvent/GetAllEvent", "rest-calendar-get-all-event.json")
        val range = CalendarMath.monthGrid(YearMonth.of(2026, 10))
        graph.calendar.refresh(range).getOrThrow()
        val url = graph.server.requests.single().url
        assertEquals("2026-09-27T05:00:00Z", url.queryParameter("startTime"))
        // The grid ends on Nov 8, after fall-back, so the offset changes to -6.
        assertEquals("2026-11-08T06:00:00Z", url.queryParameter("endTime"))
        assertEquals("2026-09-27", url.queryParameter("calendarStartDate"))
        assertEquals("2026-11-08", url.queryParameter("calendarEndDateExclusive"))

        val feed = graph.calendar.observeRange(range).first().data
        val checkIn = feed.events.single { it.id == "evt_8f2b19c4" }
        assertEquals(MyInvitation.ACCEPTED, checkIn.myInvitation)
        assertTrue(feed.events.single { it.id == "evt_allday_31" }.isAllDay)
        val thanksgiving = feed.holidays.single()
        assertEquals(LocalDate.of(2026, 11, 26), thanksgiving.start)
        assertEquals(LocalDate.of(2026, 11, 26), thanksgiving.endInclusive)
        assertEquals(1, feed.timeOff.size)
    }

    @Test
    fun unknownSavedZoneFallsBackToChicagoNotTheDevice() = testScope.runTest {
        graph.session.mutableState.value = SessionState.SignedIn(CurrentUser("usr_alex_rivera", timeZoneId = "Not/AZone"))
        assertEquals(chicago, graph.calendar.zone.first())
        graph.session.mutableState.value = SessionState.SignedIn(CurrentUser("usr_alex_rivera", timeZoneId = "America/New_York"))
        assertEquals(ZoneId.of("America/New_York"), graph.calendar.zone.first())
    }

    @Test
    fun milestonesLoadAndInvalidRowsAreDropped() = testScope.runTest {
        graph.server.fixture("GET /api/CalendarEvent/milestones", "rest-calendar-get-milestones.json")
        graph.calendar.refreshMilestones().getOrThrow()
        val milestones = graph.calendar.observeMilestones().first().data
        assertEquals(2, milestones.size)
        assertEquals(1, CalendarMath.milestonesOn(milestones, LocalDate.of(2026, 11, 2)).size)
    }

    @Test
    fun invitationResponseFailureIsTyped() = testScope.runTest {
        graph.server.json("POST /api/invitation/AcceptDenyMeeting", """{"data":false,"statusCode":400,"success":false}""", code = 400)
        assertIs<WaiveraError>(graph.calendar.respondToInvitation("evt_8f2b19c4", accept = true).exceptionOrNull())
        graph.server.fixture("POST /api/invitation/AcceptDenyMeeting", "rest-invitation-accept-deny-meeting.json")
        graph.calendar.respondToInvitation("evt_8f2b19c4", accept = true).getOrThrow()
        assertTrue("\"isInvitationAccept\":true" in graph.server.requests.last().body?.utf8().orEmpty())
    }

    @Test
    fun meetingLobbyUrlEncodesStateLikeTheWebClient() = testScope.runTest {
        graph.server.fixture("POST /api/video/createmeetingroom", "rest-video-create-meeting-room.json")
        val url = graph.calendar.meetingLobbyUrl("evt_8f2b19c4").getOrThrow()
        assertTrue(url.startsWith("https://app.example.test/lobby/"))
        val state = url.substringAfter("?state=")
        val decoded = java.net.URLDecoder.decode(
            String(java.util.Base64.getDecoder().decode(java.net.URLDecoder.decode(state, "UTF-8"))),
            "UTF-8",
        )
        assertTrue("\"calenderEventId\":\"evt_8f2b19c4\"" in decoded)
        assertTrue("\"relatedChatConversationId\":null" in decoded)
        assertTrue("\"isOneToOneCall\":false" in decoded)
    }

    @Test
    fun rangeRejectsEmpty() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)) }
    }
}
