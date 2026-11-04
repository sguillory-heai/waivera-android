package com.waivera.waivera.feature.profile.timeoff

import com.waivera.waivera.core.data.model.LeaveDraft
import com.waivera.waivera.core.data.model.TimeOffRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Turns the form into what the server is sent, and the checks the form can make on its own. */
internal object TimeOffDrafts {
    /** The draft once every field is filled; null until then. */
    fun draft(state: TimeOffFormState, zone: ZoneId): LeaveDraft? {
        val typeId = state.typeId
        val start = start(state, zone)
        val end = end(state, zone)
        if (typeId == null || start == null || end == null) return null
        return LeaveDraft(
            leaveTypeId = typeId,
            partialDay = state.partialDay,
            start = start,
            end = end,
            note = state.note.trim().takeIf(String::isNotEmpty),
        )
    }

    // sprout_web sends calendar days as UTC bounds (T00:00:00.000Z to T23:59:59.999Z) and partial days
    // as instants; partial days are read in the employee's saved zone.
    private fun start(state: TimeOffFormState, zone: ZoneId): Instant? = if (state.partialDay) {
        state.day?.let { day -> state.from?.let { day.atTime(it).atZone(zone).toInstant() } }
    } else {
        state.firstDay?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
    }

    private fun end(state: TimeOffFormState, zone: ZoneId): Instant? = if (state.partialDay) {
        state.day?.let { day -> state.to?.let { day.atTime(it).atZone(zone).toInstant() } }
    } else {
        state.lastDay?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.minusMillis(1)
    }

    fun issue(state: TimeOffFormState, zone: ZoneId): LeaveIssue? = if (state.partialDay) {
        val start = start(state, zone)
        val end = end(state, zone)
        val minutes = if (start != null && end != null) Duration.between(start, end).toMinutes() else null
        when {
            minutes == null -> null
            minutes < TimeOffFormViewModel.MIN_PARTIAL_MINUTES -> LeaveIssue.TooShort
            minutes > TimeOffFormViewModel.MAX_PARTIAL_MINUTES -> LeaveIssue.TooLong
            else -> null
        }
    } else {
        val first = state.firstDay
        val last = state.lastDay
        if (first != null && last != null && last.isBefore(first)) LeaveIssue.EndBeforeStart else null
    }

    fun past(state: TimeOffFormState, today: LocalDate): Boolean =
        (if (state.partialDay) state.day else state.firstDay)?.isBefore(today) == true

    fun pending(state: TimeOffFormState) = TimeOffRequest(
        id = TimeOffFormViewModel.PENDING_ID,
        start = if (state.partialDay) state.day else state.firstDay,
        end = if (state.partialDay) state.day else state.lastDay,
        leaveType = state.type?.name,
        requestType = if (state.partialDay) "PARTIAL_DAY" else "FULL_DAY",
        comment = state.note.trim().takeIf(String::isNotEmpty),
    )
}
