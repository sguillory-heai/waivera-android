package com.waivera.waivera.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Mirrors of the sprout_api task review contract: Submit, Approve and Reject on a task
 * (`Controllers/WorkflowController/TaskWorkflowController.cs`), the late-justification steps
 * (`Controllers/Compliance/TaskJustificationController.cs`) and the employee closure choice
 * (`TaskController` employee-closure-*). Enums travel as their names (JsonStringEnumConverter).
 */

/** `TaskCapabilitiesDto` on `GET api/tasks/{id}`: what the caller may do on this task now. */
@Serializable
data class TaskCapabilitiesResponse(
    val canSubmit: Boolean = false,
    val canApprove: Boolean = false,
    val canApproveAllRemaining: Boolean = false,
    val remainingApprovalStageCount: Int = 0,
    val canReject: Boolean = false,
)

/** `WorkflowStepDto`: one approval stage. [status] is `COMPLETED`, `ACTIVE` or `FUTURE`. */
@Serializable
data class WorkflowStepResponse(
    val stepOrder: Int = 0,
    val stepRole: String? = null,
    val status: String? = null,
    val actualApprover: TaskUserResponse? = null,
)

/** `WorkflowApprovalRequirementsResponse` from `GET api/workflow/task/{id}/approval-requirements`. */
@Serializable
data class ApprovalRequirementsResponse(
    val taskId: Int = 0,
    val approvalMode: String? = null,
    val willCompleteTask: Boolean = false,
    /** A literal date (`DateOnly`): the Drive section a reopened task was filed in before. */
    val previousDriveFilingDate: String? = null,
    val canApproveImmediately: Boolean = false,
    val requirementsVersion: String? = null,
    val workflowVersion: String? = null,
    val canApproveAllRemaining: Boolean = false,
    val remainingStageCount: Int = 0,
    val requirements: List<WorkflowRequirementResponse> = emptyList(),
)

/** `WorkflowTransitionRequirementDto`: information a stage needs before it can be approved. */
@Serializable
data class WorkflowRequirementResponse(
    val ruleId: Int = 0,
    val actionId: Int = 0,
    val requirementKey: String = "",
    val actionType: String? = null,
    val title: String? = null,
    val description: String? = null,
    val isBlocking: Boolean = false,
    val fields: List<WorkflowFieldResponse> = emptyList(),
)

/** `WorkflowTransitionFieldDto`. Today the server only sends `date` fields. */
@Serializable
data class WorkflowFieldResponse(
    val key: String = "",
    val label: String? = null,
    val fieldType: String? = null,
    val required: Boolean = false,
    val defaultValue: String? = null,
)

/** `ApproveTaskStageRequest` for `POST api/workflow/approve/{id}`. */
@Serializable
data class ApproveTaskStageBody(
    val mode: String,
    val expectedWorkflowVersion: String?,
    val idempotencyKey: String,
    val requirementsVersion: String?,
    val requirementResponses: List<RequirementAnswerBody> = emptyList(),
    val acknowledgeRelationshipWarnings: Boolean = false,
    val closeUnfinalizedDrafts: Boolean = false,
    val useOriginalDriveSection: Boolean = false,
    val expectedPreviousDriveFilingDate: String? = null,
)

/** `WorkflowTransitionRequirementResponse`: the answers to one requirement, keyed by field. */
@Serializable
data class RequirementAnswerBody(val actionId: Int, val requirementKey: String, val values: JsonObject)

/** `WorkflowApprovalResult`. */
@Serializable
data class ApprovalResultResponse(
    val taskId: Int = 0,
    val approvedStageCount: Int = 0,
    val stageApproved: Boolean = false,
    val workflowCompleted: Boolean = false,
    val taskCompleted: Boolean = false,
)

/**
 * The 409/422 body of an approval the server refused: [code] says why (TASK_COMPLETION_BLOCKED,
 * TASK_COMPLETION_WARNING_REQUIRED, WORKFLOW_REQUIREMENTS_CHANGED, ...). [message] is never shown.
 */
@Serializable
data class ApprovalErrorResponse(
    val code: String? = null,
    val completion: CompletionEvaluationResponse? = null,
    val requirementsVersion: String? = null,
    val requirements: List<WorkflowRequirementResponse>? = null,
)

/** `TaskCompletionEvaluation`: the related tasks that hold this one up. */
@Serializable
data class CompletionEvaluationResponse(
    val blockers: List<CompletionDependencyResponse> = emptyList(),
    val warnings: List<CompletionDependencyResponse> = emptyList(),
)

/** `TaskCompletionDependency`: one related task and how it relates. */
@Serializable
data class CompletionDependencyResponse(
    val relationshipId: Int = 0,
    val sourceTaskId: Int = 0,
    val sourceTaskFriendlyId: Int = 0,
    val sourceTaskTitle: String? = null,
    val sourceTaskStatus: String? = null,
    val organizationAbbreviation: String? = null,
    val relationshipName: String? = null,
    val sourceToTargetName: String? = null,
)

/** `RejectOptionDto` from `GET api/workflow/task/{id}/reject-options`: a step the task can go back to. */
@Serializable
data class RejectOptionResponse(
    val taskApprovalId: Int = 0,
    val displayName: String? = null,
    val roleName: String? = null,
)

/** `FeedbackRequest` for `POST api/workflow/reject/{id}`. */
@Serializable
data class RejectTaskBody(
    val feedback: String,
    val taskApprovalId: Int,
    val durationInMinutes: Int,
    val lateJustificationAdjustmentId: Long? = null,
    val lateJustificationDecision: String? = null,
)

/** `GET api/form/v2/task/{id}/drafts`: forms with saved progress that isn't finalized. */
@Serializable
data class TaskFormDraftResponse(val id: Int = 0, val formName: String? = null)

/** `EmployeeTaskClosureOptionsResponse`. */
@Serializable
data class EmployeeClosureOptionsResponse(
    val requiresNextExpirationDate: Boolean = false,
    /** A literal date (`DateOnly`). */
    val nextExpirationDate: String? = null,
    val canMerge: Boolean = false,
    val eligiblePdfs: List<EmployeeClosureFileResponse> = emptyList(),
)

@Serializable
data class EmployeeClosureFileResponse(
    val employeeFileId: String = "",
    val fileName: String? = null,
    val size: Long = 0,
)

/** `EmployeeTaskClosureRequest` for `PUT api/tasks/{id}/employee-closure-choice`. */
@Serializable
data class EmployeeClosureBody(
    val mode: String,
    val idempotencyKey: String,
    val orderedEmployeeFileIds: List<String>,
    val nextExpirationDate: String? = null,
)

/** `LateJustificationContextResponse` from `GET api/tasks/{id}/justifications/context`. */
@Serializable
data class LateContextResponse(
    val taskId: Int = 0,
    val isLate: Boolean = false,
    val canSubmit: Boolean = false,
    val canReview: Boolean = false,
    val hasCurrentUserReviewed: Boolean = false,
    val satisfiesSubmission: Boolean = false,
    val justification: LateJustificationResponse? = null,
    val suggestedDelay: LateDelayResponse? = null,
    val reviews: List<LateReviewResponse> = emptyList(),
)

@Serializable
data class LateJustificationResponse(
    val id: Long = 0,
    val reasonCodeId: Long = 0,
    val reasonName: String? = null,
    val explanation: String? = null,
    val evidenceAttachmentId: Int? = null,
    val sourceTaskDelayId: Long? = null,
    val submittedByName: String? = null,
    /** `Pending`, `Approved`, `Rejected`, `NotRequired` or `Superseded`. */
    val status: String? = null,
)

@Serializable
data class LateDelayResponse(
    val id: Long = 0,
    val reasonCodeId: Long = 0,
    val reasonName: String? = null,
    val explanation: String? = null,
    val evidenceAttachmentId: Int? = null,
)

@Serializable
data class LateReviewResponse(
    val id: Long = 0,
    val reviewerName: String? = null,
    val reviewerRole: String? = null,
    /** `Agree` or `ReturnForClarification`. */
    val decision: String? = null,
    val note: String? = null,
)

/** `TaskJustificationRequestDto` for `POST api/tasks/{id}/justifications`. */
@Serializable
data class LateJustificationBody(
    val reasonCodeId: Long,
    val explanation: String,
    val evidenceAttachmentId: Int? = null,
    val sourceTaskDelayId: Long? = null,
)

/** `LateJustificationReviewRequestDto` for `POST api/tasks/{id}/justifications/{adjustmentId}/reviews`. */
@Serializable
data class LateReviewBody(
    val decision: String,
    val note: String? = null,
    val reasonCodeId: Long? = null,
    val explanation: String? = null,
    val evidenceAttachmentId: Int? = null,
)

/** `POST api/tasks/{id}/justifications` answers `{ message, adjustmentId, status }`; only [status] is read. */
@Serializable
data class LateJustificationResult(val adjustmentId: Long = 0, val status: String? = null)

/** `POST api/file/upload-compliance-evidence` answers `{ attachmentId, fileId }`. */
@Serializable
data class EvidenceUploadResponse(val attachmentId: Int = 0)
