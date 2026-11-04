package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.calendar.EventPayload
import com.waivera.waivera.core.data.model.Attendee
import com.waivera.waivera.core.data.model.EventDraft
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.dto.AttendeeType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EventPayloadTest {
    private val chicago = ZoneId.of("America/Chicago")

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun draft(date: LocalDate, allDay: Boolean = false) = EventDraft(
        title = " Team huddle ",
        date = date,
        start = LocalTime.of(9, 0),
        end = LocalTime.of(9, 30),
        allDay = allDay,
        inviteeIds = listOf("usr_priya", "usr_me", "usr_priya"),
    )

    @Test
    fun nine_am_stays_nine_am_on_both_sides_of_the_fall_back_change() {
        // Chicago is UTC-5 on Oct 31 2026 and UTC-6 from 2 AM Nov 1.
        val before = EventPayload.build(draft(LocalDate.of(2026, 10, 31)), chicago, "usr_me")
        val after = EventPayload.build(draft(LocalDate.of(2026, 11, 1)), chicago, "usr_me")
        assertEquals("2026-10-31T14:00:00Z", before.text("startTime"))
        assertEquals("2026-11-01T15:00:00Z", after.text("startTime"))
        assertEquals("2026-11-01T15:30:00Z", after.text("endTime"))
        assertEquals("America/Chicago", after.text("time_zone"))
    }

    @Test
    fun a_waivera_meeting_is_sent_as_the_web_sends_it() {
        val body = EventPayload.build(draft(LocalDate.of(2026, 11, 4)), chicago, "usr_me")
        assertEquals("Team huddle", body.text("title"))
        assertEquals("Waivera", body.text("location"))
        assertEquals("false", body.text("isSprout"))
        assertEquals("true", body.text("online"))
        assertEquals("usr_me", body.text("ownerUserID"))
        val refs = body.getValue("attendees").jsonArray.map { it.jsonObject.text("ref") }
        assertEquals(listOf("usr_priya"), refs)
        assertFalse(body.containsKey("isAllDay"))
    }

    @Test
    fun an_all_day_event_spans_zoned_midnights_on_the_day_clocks_change() {
        val body = EventPayload.build(draft(LocalDate.of(2026, 11, 1), allDay = true), chicago, "usr_me")
        assertEquals("2026-11-01T05:00:00Z", body.text("startTime"))
        // The day is 25 hours long: the next midnight is at UTC-6.
        assertEquals("2026-11-02T06:00:00Z", body.text("endTime"))
        assertEquals("2026-11-01", body.text("allDayStart"))
        assertEquals("2026-11-02", body.text("allDayEndExclusive"))
    }

    @Test
    fun an_edit_keeps_participants_and_outside_guests_it_does_not_show() {
        val existing = ScheduleEvent(
            id = "e1",
            ownerUserId = "usr_me",
            attendees = listOf(
                Attendee("a1", AttendeeType.EMPLOYEE, userId = "usr_old"),
                Attendee("a2", AttendeeType.WAIVER_PARTICIPANT, userId = "42"),
                Attendee("a3", AttendeeType.EXTERNAL, email = "guest@example.com"),
            ),
        )
        val body = EventPayload.build(
            draft(
                LocalDate.of(2026, 11, 4),
            ).copy(inviteeIds = listOf("usr_new"), waiveraVideo = false, location = "Room 2"),
            chicago,
            "usr_me",
            existing,
        )
        val attendees = body.getValue("attendees").jsonArray.map { it.jsonObject }
        assertEquals(listOf("EMPLOYEE", "WAIVER_PARTICIPANT", "EXTERNAL"), attendees.map { it.text("type") })
        assertEquals("guest@example.com", attendees.last().text("email"))
        assertEquals("Room 2", body.text("location"))
        assertEquals("true", body.text("isSprout"))
        assertEquals(listOf("usr_old"), EventPayload.invitees(existing))
    }
}
