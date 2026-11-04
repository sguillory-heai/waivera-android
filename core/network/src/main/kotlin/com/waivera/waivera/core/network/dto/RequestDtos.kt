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
    val taskFriendlyId: Int? = null,
    val requiredAttachmentId: Int? = null,
    val taskTitle: String? = null,
    val transactionReasonText: String? = null,
    val participantRequest: ParticipantChangeResponse? = null,
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

/** `EmployeeTimeOffResponseDto`, the fields a reviewer sees. Times are UTC, sometimes without a suffix. */
@Serializable
data class TimeOffDetailsResponse(
    val id: Int = 0,
    val timeOffStartDate: String? = null,
    val timeOffEndDate: String? = null,
    val typeOfLeave: String? = null,
    val typeOfLeaveId: Int = 0,
    val comment: String? = null,
    /** `FULL_DAY` or `PARTIAL_DAY`. */
    val requestType: String? = null,
    val durationMinutes: Int = 0,
    val intelligenceSnapshotJson: String? = null,
    val currentIntelligenceJson: String? = null,
    val reviewRevision: Int = 0,
    val impactAcknowledgedRevision: Int? = null,
    val impactAcknowledgedAt: String? = null,
)

/** sprout_web `LeaveIntelligencePreview`, stored as JSON text on the time-off request. */
@Serializable
data class LeaveIntelligenceResponse(
    val overdueCount: Int = 0,
    val dueDuringLeaveCount: Int = 0,
    val warnings: List<LeaveWarningResponse> = emptyList(),
)

@Serializable
data class LeaveWarningResponse(val code: String? = null, val message: String? = null)

/** `VIewParticipantRequest`; the server's spelling of the two profiles is kept. */
@Serializable
data class ParticipantChangeResponse(
    val id: Int = 0,
    val participantId: Int = 0,
    val newProfileChanegs: ParticipantProfileResponse? = null,
    val orignalProfile: ParticipantProfileResponse? = null,
)

/** `NewProfileChanegsDto` / `OrignalProfileDto`: the fields the web compares. */
@Serializable
data class ParticipantProfileResponse(
    val dateOfBirth: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val mobileNumber: String? = null,
    val waiverName: String? = null,
    val waiverId: Int? = null,
    val participantSsnLast4: String? = null,
    val certificationDate: String? = null,
    val cpocDate: String? = null,
    val cpocEndDate: String? = null,
)

/** `TimeOffActionDto` for both `TimeOff/approve-reject` and the profile change route. */
@Serializable
data class RequestActionBody(val action: String, val reason: String)

/** `EmployeeLeaveSummary` from `leave-management/employees/{id}/balances` (balances only). */
@Serializable
data class LeaveBalancesResponse(val balances: List<LeaveBalanceResponse> = emptyList())

@Serializable
data class LeaveBalanceResponse(
    val id: Int = 0,
    val name: String? = null,
    val tracksBalance: Boolean = false,
    val availableMinutes: Int = 0,
)

/** `leave-management/settings`, the zone partial days are told in. */
@Serializable
data class LeaveSettingsResponse(val timeZone: String? = null)

/**
 * `TrainingExternalService.RequestDetailsAsync` (or the standalone credit's), a bare object.
 * [standaloneCreditId] marks a credit reviewed on the web's training page instead.
 */
@Serializable
data class TrainingCreditResponse(
    val courseTitle: String? = null,
    val employeeName: String? = null,
    val trainerName: String? = null,
    val provider: String? = null,
    val completedDate: String? = null,
    val hours: Double = 0.0,
    val fileName: String? = null,
    val status: String? = null,
    val revision: Int? = null,
    val canApprove: Boolean = false,
    val requiresOverrideReason: Boolean = false,
    val approvalBlockedReason: String? = null,
    val uploadedForEmployee: Boolean = false,
    val submittedByName: String? = null,
    val standaloneCreditId: Int? = null,
)

/** `TrainingCreditDecision`. */
@Serializable
data class TrainingDecisionBody(val revision: Int, val approve: Boolean, val reason: String)

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

/**
 * `AttachmentResponse` from `GET api/tasks/attachments/{taskId}`: the files a request can cite
 * and the task's files the viewer opens.
 */
@Serializable
data class TaskAttachmentResponse(
    val id: Int = 0,
    val file: TaskAttachmentFileResponse? = null,
    val employeeFileDeleted: Boolean = false,
    /** "caseFile" or "employeeFile": which download route serves it. */
    val fileStorageKind: String? = null,
)

/** `CaseFileResponse` (the fields read here). */
@Serializable
data class TaskAttachmentFileResponse(
    val id: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val size: Long = 0,
    val createDate: String? = null,
    val author: TaskUserResponse? = null,
)
