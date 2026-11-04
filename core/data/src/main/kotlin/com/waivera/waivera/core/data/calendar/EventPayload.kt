package com.waivera.waivera.core.data.calendar

import com.waivera.waivera.core.data.model.EventDraft
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.ZoneId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * `dto/Request/CalendarEventDto.cs` for CreateEvent and UpdateEventbyId, built the way the web's
 * Add event form builds it: wall times in the saved zone become UTC instants (so 9 AM stays
 * 9 AM across a DST change), an all-day event spans zoned midnights plus its literal dates, a
 * Waivera video meeting is `isSprout: false` with location "Waivera", and `online` is always true.
 */
object EventPayload {
    const val WAIVERA_LOCATION = "Waivera"

    fun build(draft: EventDraft, zone: ZoneId, ownerUserId: String, existing: ScheduleEvent? = null): JsonObject {
        val (start, end) = if (draft.allDay) {
            draft.date.atStartOfDay(zone).toInstant() to draft.date.plusDays(1).atStartOfDay(zone).toInstant()
        } else {
            draft.date.atTime(draft.start).atZone(zone).toInstant() to
                draft.date.atTime(draft.end).atZone(zone).toInstant()
        }
        return buildJsonObject {
            put("title", draft.title.trim())
            put("description", draft.description?.trim().orEmpty())
            put("location", if (draft.waiveraVideo) WAIVERA_LOCATION else draft.location?.trim().orEmpty())
            put("meetingDetails", existing?.meetingDetails.orEmpty())
            put("ownerUserID", ownerUserId)
            put("startTime", WaiveraTime.formatQueryInstant(start))
            put("endTime", WaiveraTime.formatQueryInstant(end))
            if (draft.allDay) {
                put("isAllDay", true)
                put("allDayStart", draft.date.toString())
                put("allDayEndExclusive", draft.date.plusDays(1).toString())
            } else if (existing?.isAllDay == true) {
                put("isAllDay", false)
            }
            put("time_zone", zone.id)
            put("online", true)
            put("isSprout", !draft.waiveraVideo)
            putJsonArray("attendees") {
                draft.inviteeIds.filter { it != ownerUserId }.distinct().forEach { id ->
                    addJsonObject {
                        put("type", AttendeeType.EMPLOYEE.name)
                        put("ref", id)
                    }
                }
                // An edit keeps the guests the phone does not manage: participants and outside emails.
                existing?.attendees.orEmpty().forEach { attendee ->
                    when (attendee.type) {
                        AttendeeType.WAIVER_PARTICIPANT -> attendee.userId?.let { ref ->
                            addJsonObject {
                                put("type", AttendeeType.WAIVER_PARTICIPANT.name)
                                put("ref", ref)
                            }
                        }
                        AttendeeType.EXTERNAL -> attendee.email?.let { email ->
                            addJsonObject {
                                put("type", AttendeeType.EXTERNAL.name)
                                put("email", email)
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /** Coworkers already invited to [event], for the edit form. */
    fun invitees(event: ScheduleEvent): List<String> = event.attendees
        .filter { it.type == AttendeeType.EMPLOYEE && it.userId != null && it.userId != event.ownerUserId }
        .mapNotNull { it.userId }
        .distinct()
}
