package com.waivera.waivera.core.data.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * A quick event as the person fills it in: wall-clock values in the employee's saved zone,
 * turned into UTC instants only when sent. Recurrence and linked tasks stay on the web.
 */
data class EventDraft(
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val allDay: Boolean = false,
    /** Backend user ids of invited coworkers, never the organizer. */
    val inviteeIds: List<String> = emptyList(),
    /** Participant ids (the server's numeric ids, as text) of invited participants. */
    val participantIds: List<String> = emptyList(),
    /**
     * Email invited participants who have an email on file (a join link and Accept/Decline).
     * Off keeps them un-emailed, as the web always has. On an edit it covers that save only.
     */
    val notifyParticipants: Boolean = false,
    /** A Waivera video meeting; otherwise [location] says where it happens. */
    val waiveraVideo: Boolean = true,
    val location: String? = null,
    val description: String? = null,
)

/**
 * Someone the person may invite, from the server's calendar directory: a coworker, or a
 * participant ([participant] true, [id] the participant id). Ids are unique only within a kind.
 */
data class AttendeeOption(val id: String, val name: String, val email: String?, val participant: Boolean = false) {
    val key: String get() = if (participant) "participant:$id" else "employee:$id"
}
