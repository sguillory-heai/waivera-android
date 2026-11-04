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
    /** A Waivera video meeting; otherwise [location] says where it happens. */
    val waiveraVideo: Boolean = true,
    val location: String? = null,
    val description: String? = null,
)

/** Someone the person may invite, from the server's calendar directory. */
data class AttendeeOption(val id: String, val name: String, val email: String?)
