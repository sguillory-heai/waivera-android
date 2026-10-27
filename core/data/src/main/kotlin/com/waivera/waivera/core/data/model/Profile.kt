@file:UseSerializers(UtcInstantSerializer::class, LocalDateSerializer::class)

package com.waivera.waivera.core.data.model

import com.waivera.waivera.core.network.time.LocalDateSerializer
import com.waivera.waivera.core.network.time.UtcInstantSerializer
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class EmployeeProfile(
    val employeeId: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrl: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    /** Calendar dates, shown as-is (never shifted by a zone). */
    val hireDate: LocalDate? = null,
    val birthday: LocalDate? = null,
    val joinedAt: Instant? = null,
    val positions: List<String> = emptyList(),
    val managers: List<Person> = emptyList(),
    val regions: List<String> = emptyList(),
    val agencies: List<String> = emptyList(),
    val timeZoneId: String? = null,
    /** `GetEmployeeProfileDto.waiverFamilies`. */
    val waiverFamilies: List<String> = emptyList(),
    /** `UserWithRoleDto.scId`, known for the signed-in person (from the session bootstrap). */
    val scId: String? = null,
)

/** The signed-in person's organization, matched by id (08 §5); never by name. */
@Serializable
data class Organization(
    val id: Int,
    val name: String? = null,
    val address: String? = null,
    val contactNumber: String? = null,
)

/** Another person's account details (06 §C2), from `GetUserDetailsByUserID`. */
@Serializable
data class PersonDetails(
    val userId: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val roles: List<String> = emptyList(),
    val organizationName: String? = null,
    val scId: String? = null,
) {
    /** Display name, else first + last; null when the server sent neither. */
    val name: String?
        get() = displayName?.takeIf(String::isNotBlank)
            ?: listOfNotNull(firstName, lastName).joinToString(" ").trim().takeIf(String::isNotEmpty)
}

@Serializable
data class TimeOffRequest(
    val id: Long,
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val leaveType: String? = null,
    val requestType: String? = null,
    val comment: String? = null,
    val isApproved: Boolean = false,
    val isRejected: Boolean = false,
    val isCancellationPending: Boolean = false,
)

@Serializable
data class TimeOffBalance(
    /** `remainingLeaveDays`, else `remainingLeaveCount`. */
    val remainingDays: Double? = null,
    val remainingHours: Double? = null,
    val usedDays: Double? = null,
    val usedHours: Double? = null,
    val allocated: Double? = null,
    val pending: Double? = null,
    /** `usedLeavePercentage` as sent (0..100); screens derive it from days only when absent. */
    val usedPercentage: Double? = null,
)

@Serializable
data class TimeOffSummary(val requests: List<TimeOffRequest> = emptyList(), val balance: TimeOffBalance? = null)

@Serializable
data class EmployeeFile(
    val id: String,
    val fileName: String? = null,
    val category: String? = null,
    val addedAt: Instant? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val uploadedBy: String? = null,
)

data class EmployeeFilePage(val items: List<EmployeeFile>, val totalRecords: Int, val pageNumber: Int) {
    val hasMore: Boolean get() = pageNumber * PAGE_SIZE < totalRecords

    companion object {
        const val PAGE_SIZE = 20
    }
}
