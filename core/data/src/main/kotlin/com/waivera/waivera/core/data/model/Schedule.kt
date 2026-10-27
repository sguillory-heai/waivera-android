@file:UseSerializers(UtcInstantSerializer::class, LocalDateSerializer::class)

package com.waivera.waivera.core.data.model

import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.network.time.LocalDateSerializer
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/** A half-open run of calendar days, `[start, endExclusive)`, in the employee's zone. */
@Serializable
data class DateRange(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(endExclusive.isAfter(start)) { "empty range" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    fun days(): Sequence<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(endExclusive) }

    val key: String get() = "$start/$endExclusive"
}

/** What the signed-in user has answered; [NOT_INVITED_OR_UNKNOWN] when the API does not say. */
enum class MyInvitation { ORGANIZER, PENDING, ACCEPTED, DECLINED, TENTATIVE, NOT_INVITED_OR_UNKNOWN }

@Serializable
data class Attendee(
    val id: String,
    val type: AttendeeType = AttendeeType.UNKNOWN,
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val inviteStatus: InviteStatus = InviteStatus.UNKNOWN,
    val respondedAt: Instant? = null,
)

/**
 * A calendar event. Timed events carry instants ([startAt]/[endAt]); all-day events carry literal
 * dates ([allDayStart] inclusive, [allDayEndExclusive]) that are never shifted by a zone.
 */
@Serializable
data class ScheduleEvent(
    val id: String,
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val meetingDetails: String? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val allDayStart: LocalDate? = null,
    val allDayEndExclusive: LocalDate? = null,
    val ownerUserId: String? = null,
    val ownerName: String? = null,
    val ownerColor: String? = null,
    val attendees: List<Attendee> = emptyList(),
    val online: Boolean = false,
    val isSprout: Boolean = false,
    val meetingType: String? = null,
    val recurrenceRule: String? = null,
    val parentEventId: String? = null,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val myInvitation: MyInvitation = MyInvitation.NOT_INVITED_OR_UNKNOWN,
) {
    val isAllDay: Boolean get() = allDayStart != null

    /** The first `http(s)` link in [meetingDetails] or [location], for non-Sprout meetings. */
    val joinLink: String?
        get() = listOfNotNull(meetingDetails, location).firstOrNull {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
}

@Serializable
data class TimeOffEntry(
    val id: Long,
    val userId: String? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val leaveType: String? = null,
    val requestType: String? = null,
    val durationMinutes: Int = 0,
    val status: String? = null,
    val isCancellationPending: Boolean = false,
)

/** Holidays are date-only; [endInclusive] is the last day off. */
@Serializable
data class Holiday(val id: Long, val title: String? = null, val start: LocalDate, val endInclusive: LocalDate)

@Serializable
data class ScheduleFeed(
    val range: DateRange,
    val zoneId: String,
    val events: List<ScheduleEvent> = emptyList(),
    val timeOff: List<TimeOffEntry> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
)

/** A recurring birthday or work anniversary (no year is stored for birthdays). */
@Serializable
data class Milestone(
    val type: MilestoneType,
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val roles: List<String> = emptyList(),
    val month: Int,
    val day: Int,
    val startYear: Int? = null,
)

/** A [Milestone] resolved onto a date; [years] is set for anniversaries only. */
data class MilestoneOccurrence(val milestone: Milestone, val date: LocalDate, val years: Int?)
