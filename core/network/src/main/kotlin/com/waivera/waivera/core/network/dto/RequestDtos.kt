package com.waivera.waivera.core.network.dto

import kotlinx.serialization.Serializable

/*
 * Mirrors of the sprout_api change-request contract: administrative requests
 * (`api/admin/request`), task compliance requests (`api/tasks/{id}/extensions`, `/delays`,
 * `/request-close`, `/compliance-request-eligibility`) and their reason lists. Request and
 * decision times are UTC instants; due dates and delay periods are literal calendar dates.
 */

/** `dto/Response/AdministrativeTransactionRequestResponse.cs`. */
@Serializable
data class AdminRequestResponse(
    val id: Int = 0,
    val requesterName: String? = null,
    val requesterId: String? = null,
    val approverName: String? = null,
    val transactionType: String? = null,
    val status: String? = null,
    val requestTime: String? = null,
    val completionTime: String? = null,
    val taskId: Int? = null,
    val taskTitle: String? = null,
    val transactionReasonText: String? = null,
    val extensionReason: String? = null,
    val extensionOriginalDueDate: String? = null,
    val extensionRequestedDueDate: String? = null,
    val extensionApprovedDueDate: String? = null,
    val delayReason: String? = null,
    val delayStartDate: String? = null,
    val delayEndDate: String? = null,
    val timeOffDetails: TimeOffDetailsResponse? = null,
    val isTrainingTask: Boolean = false,
)

/** `EmployeeTimeOffResponseDto`, only the period. */
@Serializable
data class TimeOffDetailsResponse(
    val timeOffStartDate: String? = null,
    val timeOffEndDate: String? = null,
    val typeOfLeave: String? = null,
)

/** `PagedResults<T>` inside an `ApiResponse`. */
@Serializable
data class PagedResults<T>(
    val items: List<T> = emptyList(),
    val totalCount: Int = 0,
    val pageNumber: Int = 0,
    val pageSize: Int = 0,
)

/** `TaskComplianceRequestEligibilityResult`. Delay dates are literal `YYYY-MM-DD` strings. */
@Serializable
data class ComplianceEligibilityResponse(
    val taskId: Int = 0,
    val currentDueDate: String? = null,
    val isOverdue: Boolean = false,
    val hasPendingRequest: Boolean = false,
    val canRequestExtension: Boolean = false,
    val canRequestDelay: Boolean = false,
    val delayStartDate: String? = null,
    val delayEndDate: String? = null,
    val isDelayRenewal: Boolean = false,
    val hasQueuedApprovedDelay: Boolean = false,
    val isEmployeeTask: Boolean = false,
)

/** `Models/ComplianceEngine/Compliance.cs` `ComplianceReasonCode`, the fields a requester picks by. */
@Serializable
data class ComplianceReasonResponse(
    val id: Long = 0,
    val name: String? = null,
    val userFacingDescription: String? = null,
    val isHiddenFromUserSelection: Boolean = false,
    val canBeUsedForExtension: Boolean = false,
    val canBeUsedForDelay: Boolean = false,
    val requiresEvidence: Boolean = false,
    val isActive: Boolean = false,
)

/** `TransactionReasonResponseDto` from `GET api/lookup/reasons?type=CLOSURE`. */
@Serializable
data class TransactionReasonResponse(val id: Int = 0, val reasonText: String? = null)

/** `TaskExtensionRequestDto`. [requestedDueDate] is the chosen day at UTC midnight, as the web sends it. */
@Serializable
data class TaskExtensionRequest(
    val requestedDueDate: String,
    val reasonCodeId: Long,
    val explanation: String,
    val evidenceAttachmentId: Int? = null,
)

/** The extension endpoint answers with a bare object, not an `ApiResponse`. */
@Serializable
data class TaskExtensionResult(val adjustmentId: Long = 0, val status: String? = null)

/** `TaskDelayRequestDto`. The period is echoed back as literal dates so a stale one is refused. */
@Serializable
data class TaskDelayRequest(
    val reasonCodeId: Long,
    val explanation: String,
    val delayStartDate: String,
    val delayEndDate: String,
    val evidenceAttachmentId: Int? = null,
)

@Serializable
data class TaskDelayResult(val delayId: Long = 0, val status: String? = null)

/** `CreateAdministrativeTransactionRequest` for `POST api/tasks/{id}/request-close`. */
@Serializable
data class TaskClosureRequest(
    val requesterId: String,
    val taskId: Int,
    val transactionReasonId: Int,
    val transactionReasonText: String,
    val transactionTypeId: String = "Task Deletion Request",
    val statusId: String = "PENDING",
)

/** `AttachmentResponse` from `GET api/tasks/attachments/{taskId}`: the files a request can cite. */
@Serializable
data class TaskAttachmentResponse(
    val id: Int = 0,
    val file: TaskAttachmentFileResponse? = null,
    val employeeFileDeleted: Boolean = false,
)

@Serializable
data class TaskAttachmentFileResponse(val id: String? = null, val fileName: String? = null)
