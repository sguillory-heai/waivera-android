@file:UseSerializers(UtcInstantSerializer::class)

package com.waivera.waivera.core.network.dto

import com.waivera.waivera.core.network.json.StringOrNumberSerializer
import com.waivera.waivera.core.network.time.LocalDateSerializer
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement

/** `dto/Response/Calendar/CalendarFeedResponseDto.cs`. All three lists are always present. */
@Serializable
data class CalendarFeedResponseDto(
    val events: List<CalendarEventResponseDto> = emptyList(),
    val timeOff: List<CalendarTimeOffResponseDto> = emptyList(),
    val holidays: List<HolidayDto> = emptyList(),
)

/** `AttendeeResponseDto`. `ref` is the employee user id for `EMPLOYEE` attendees. */
@Serializable
data class AttendeeResponseDto(
    @Serializable(with = StringOrNumberSerializer::class) val attendeeId: String? = null,
    val type: AttendeeType = AttendeeType.UNKNOWN,
    @Serializable(with = StringOrNumberSerializer::class) val ref: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val inviteStatus: InviteStatus = InviteStatus.UNKNOWN,
    val respondedAt: Instant? = null,
)

/** `CalendarEventResponseDto`. Timed values are UTC instants; all-day values are literal dates, end exclusive. */
@Serializable
data class CalendarEventResponseDto(
    val cancelledAllDayDates: List<
        @Serializable(with = LocalDateSerializer::class)
        LocalDate,
        > = emptyList(),
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val title: String? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val description: String? = null,
    val ownerUserID: String? = null,
    val ownerName: String? = null,
    val meetingDetails: String? = null,
    val location: String? = null,
    val createdby: String? = null,
    val attendees: List<AttendeeResponseDto> = emptyList(),
    val ownerColor: String? = null,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val online: Boolean = false,
    val isSprout: Boolean = false,
    val timeOffEmpData: JsonElement? = null,
    val recurrenceRule: String? = null,
    val timeZoneId: String? = null,
    @Serializable(with = LocalDateSerializer::class) val allDayStart: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val allDayEndExclusive: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val allDayExceptionDate: LocalDate? = null,
    val parentEventId: String? = null,
    val exceptionDate: Instant? = null,
    val isInvitationAccept: Boolean? = null,
    val holidays: List<HolidayDto> = emptyList(),
    val meetingType: String? = null,
    val crossesMidnight: Boolean = false,
    val displayDate: Instant? = null,
    val formattedDateRange: String? = null,
)

/** `CalendarTimeOffResponseDto`. `startTime`/`endTime` carry `Z` (normalised in the setter). */
@Serializable
data class CalendarTimeOffResponseDto(
    val id: Long = 0,
    val userId: String? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val leaveType: String? = null,
    val requestType: String? = null,
    val durationMinutes: Int = 0,
    val comment: String? = null,
    val status: String? = null,
    val isCancellationPending: Boolean = false,
    val canDelete: Boolean = false,
)

/**
 * `HolidayDto`. The server types the dates as `DateTime` but sends literal midnights, so only the
 * date part is read; converting them to instants would shift the holiday a day west of UTC.
 */
@Serializable
data class HolidayDto(
    val id: Long = 0,
    val title: String? = null,
    val description: String? = null,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate? = null,
)

/** `CalendarEmployeeMilestoneResponseDto`. `month` is 1-based; birth years are never sent. */
@Serializable
data class CalendarEmployeeMilestoneResponseDto(
    val type: MilestoneType = MilestoneType.UNKNOWN,
    @Serializable(with = StringOrNumberSerializer::class) val userId: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val roles: List<String> = emptyList(),
    val month: Int = 0,
    val day: Int = 0,
    val startYear: Int? = null,
)

/** `InternalInvitationResponseReq`. The actor comes from the token; `userId` is ignored server-side. */
@Serializable
data class InternalInvitationResponseReq(
    val calendarEventId: String,
    val action: String? = null,
    val isInvitationAccept: Boolean? = null,
    val userId: String? = null,
)

/** Body of `POST /api/public/invitation/respond` (external attendees, token based). */
@Serializable
data class PublicInvitationResponseReq(val token: String, val action: String)
