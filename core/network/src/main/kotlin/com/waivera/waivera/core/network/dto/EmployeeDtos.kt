@file:UseSerializers(UtcInstantSerializer::class)

package com.waivera.waivera.core.network.dto

import com.waivera.waivera.core.network.json.StringOrNumberSerializer
import com.waivera.waivera.core.network.time.LocalDateSerializer
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/** Reporter entry on [GetEmployeeProfileDto]. */
@Serializable
data class EmployeeReporterDto(
    @Serializable(with = StringOrNumberSerializer::class) val employeeId: String? = null,
    val name: String? = null,
    val avatar: String? = null,
)

/** Region entry on [GetEmployeeProfileDto]. */
@Serializable
data class EmployeeRegionDto(val regionId: Int = 0, val name: String? = null)

/** `dto/Response/EmployeeResponse.cs` GetEmployeeProfileDto. `timezone` is the saved IANA zone. */
@Serializable
data class GetEmployeeProfileDto(
    @Serializable(with = StringOrNumberSerializer::class) val employeeId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarURL: String? = null,
    val phoneNumber: String? = null,
    val joinDate: Instant? = null,
    @Serializable(with = LocalDateSerializer::class) val hireDate: LocalDate? = null,
    val address: String? = null,
    val positions: List<String> = emptyList(),
    val reporters: List<EmployeeReporterDto> = emptyList(),
    val regions: List<EmployeeRegionDto> = emptyList(),
    val agencyNames: List<String> = emptyList(),
    val waiverFamilyIds: List<Int>? = null,
    val waiverFamilies: List<String>? = null,
    @Serializable(with = LocalDateSerializer::class) val birthday: LocalDate? = null,
    val timezone: String? = null,
)

/** One row of `GET /api/employee/get-employee-files` (`data.items[]`). */
@Serializable
data class EmployeeFileDto(
    @Serializable(with = StringOrNumberSerializer::class) val id: String? = null,
    val fileName: String? = null,
    val documentCategoryName: String? = null,
    val dateAdded: Instant? = null,
    val fileUrl: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val userId: String? = null,
    val uploadedByName: String? = null,
)

/** `data` of `GET /api/employee/get-employee-files`. */
@Serializable
data class EmployeeFilesPageDto(
    val items: List<EmployeeFileDto> = emptyList(),
    val totalRecords: Int = 0,
    val pageNumber: Int = 0,
    val pageSize: Int = 0,
)

/**
 * One row of `GET /api/TimeOff/get-timeOffs-for-user-profile/{userId}`. The balance fields are
 * repeated on every row; the first row doubles as the summary. The dates are literal midnights.
 */
@Serializable
data class TimeOffProfileDto(
    val id: Long = 0,
    @Serializable(with = LocalDateSerializer::class) val timeOffStartDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val timeOffEndDate: LocalDate? = null,
    val typeOfLeave: String? = null,
    val comment: String? = null,
    val isApproved: Boolean = false,
    val isRejected: Boolean = false,
    val isCancellationPending: Boolean = false,
    val rejectionReason: String? = null,
    val requestType: String? = null,
    val pendingTimeOffCount: Double? = null,
    val approvedLeaveCount: Double? = null,
    val allocatedLeaveCount: Double? = null,
    val remainingLeaveCount: Double? = null,
    val remainingLeaveDays: Double? = null,
    val remainingLeaveHours: Double? = null,
    val usedLeaveDays: Double? = null,
    val usedLeaveHours: Double? = null,
    val usedLeavePercentage: Double? = null,
    val remainingLeavePercentage: Double? = null,
)

/** `Services/FileService.cs` Base64AvatarResultDto. `base64Image` is a full data URI. */
@Serializable
data class Base64AvatarResultDto(val base64Image: String? = null, val expiresInSeconds: Int = 0)

/** `dto/Request/Videocall/CreateMeetingRoomRequest.cs`. */
@Serializable
data class CreateMeetingRoomRequest(val calendarEventId: String? = null, val conversationId: Long? = null)

/** `dto/Response/videocall/CreateMeetingRoomResponse.cs`. */
@Serializable
data class CreateMeetingRoomResponse(
    val message: String? = null,
    val roomId: String? = null,
    @Serializable(with = StringOrNumberSerializer::class) val meetingRoomId: String? = null,
    val calendarEventId: String? = null,
    val conversationId: Long? = null,
    val reused: Boolean = false,
    val isPublisher: Boolean = false,
)

/** Hub `ReceiveGroupCallInvite` / `ReceiveExternalGroupCallInvite` (video hub). Note `calenderEventId`. */
@Serializable
data class ReceiveGroupCallInviteResponse(
    val currentUserId: String? = null,
    val callerDisplayName: String? = null,
    val roomId: String? = null,
    val calenderEventId: String? = null,
    val conversationId: Long? = null,
    val callerId: String? = null,
)

/** Video hub `CallRejected`. */
@Serializable
data class CallRejectedDto(val roomId: String? = null, val callerId: String? = null)
