package com.waivera.waivera.core.network.dto

import kotlinx.serialization.Serializable

/**
 * `ParticipantInfoResponse` from `GET api/participants/{id}` (the web's Participant Profile tab).
 * Dates are `DateTime`s that carry a calendar day only. The response also carries the SSN's last
 * four and device fields; they are not mirrored, so the phone never holds them.
 */
@Serializable
data class ParticipantInfoResponse(
    val id: Int = 0,
    val firstName: String? = null,
    val lastName: String? = null,
    val dateOfBirth: String? = null,
    val intakeType: String? = null,
    val region: ParticipantRegionResponse? = null,
    val deactivated: Boolean = false,
    val linkageDate: String? = null,
    val cpocDate: String? = null,
    val cpocEndDate: String? = null,
    val certificationDate: String? = null,
    val email: String? = null,
    val mobileNumber: String? = null,
    val waiver: ParticipantWaiverResponse? = null,
    val isCompliant: Boolean = false,
)

/** `RegionResponse`. */
@Serializable
data class ParticipantRegionResponse(val regionId: Int = 0, val regionName: String? = null)

/** `Waiver`. */
@Serializable
data class ParticipantWaiverResponse(val id: Int = 0, val waiverName: String? = null, val code: String? = null)

/** `PagedResult<TaskListResponse>` from `GET api/tasks/participant/{id}`, not enveloped. */
@Serializable
data class ParticipantTasksResponse(val items: List<TaskListResponse> = emptyList(), val totalCount: Int = 0)
