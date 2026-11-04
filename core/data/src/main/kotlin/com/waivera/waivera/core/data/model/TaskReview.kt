package com.waivera.waivera.core.data.model

import java.time.LocalDate

/**
 * What the viewer may do on a task's approval workflow now, straight from the server's
 * capabilities (sprout_api `TaskCapabilityPolicy`). [ready] is `isTaskReady`: every required
 * form, file and signature is done, which Submit and Approve need and Reject does not.
 */
data class TaskReviewAccess(
    val canSubmit: Boolean = false,
    val canApprove: Boolean = false,
    val canApproveAll: Boolean = false,
    val remainingStages: Int = 0,
    val canReject: Boolean = false,
    val ready: Boolean = false,
) {
    val any: Boolean get() = canSubmit || canApprove || canReject

    companion object {
        val None = TaskReviewAccess()
    }
}

enum class StepStatus { Completed, Active, Future, Unknown }

/** One approval stage, in order. [approverName] is who approved a completed stage. */
data class WorkflowStep(val order: Int, val role: String?, val status: StepStatus, val approverName: String?)

/** The participant's plan (CPOC) dates, which a stage that updates them checks new dates against. */
data class PlanDates(val start: LocalDate?, val end: LocalDate?)

enum class ApprovalMode { CurrentStage, AllRemaining }

enum class FieldType { Date, Text, LongText, Number, Unsupported }

data class RequirementField(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean,
    val defaultValue: String?,
)

/** Information a stage asks for before it can be approved, such as new plan dates. */
data class StageRequirement(
    val actionId: Int,
    val key: String,
    val actionType: String,
    val title: String,
    val description: String?,
    val fields: List<RequirementField>,
) {
    /** The web turns these away too (file fields are JSON-only on the server). */
    val supported: Boolean get() = fields.none { it.type == FieldType.Unsupported }

    /** `participant_profile_change`: new CPOC dates, checked against the current ones. */
    val isPlanDates: Boolean get() = actionType.equals(PLAN_DATES, ignoreCase = true)

    companion object {
        const val PLAN_DATES = "participant_profile_change"
        const val PLAN_START = "cpocDate"
        const val PLAN_END = "cpocEndDate"
    }
}

/** The server's plan for an approval: what it will do and what it needs first. */
data class ApprovalPlan(
    val mode: ApprovalMode,
    val willComplete: Boolean,
    /** A reopened task was filed in this month's Drive section before. */
    val previousFiling: LocalDate?,
    val requirementsVersion: String?,
    val workflowVersion: String?,
    val canApproveAll: Boolean,
    val remainingStages: Int,
    val requirements: List<StageRequirement>,
)

/** One approval send. [answers] maps a requirement's action id to its field values (ISO dates). */
data class ApprovalSend(
    val plan: ApprovalPlan,
    val idempotencyKey: String,
    val answers: Map<Int, Map<String, String>> = emptyMap(),
    val acknowledgeWarnings: Boolean = false,
    val discardDrafts: Boolean = false,
    val useOriginalSection: Boolean = false,
)

/** A related task that holds this one up. [statusName] is the server's status name. */
data class RelatedTask(
    val id: Int,
    val key: String,
    val title: String,
    val statusName: String?,
    val relationship: String?,
)

sealed interface ApprovalOutcome {
    data class Approved(val completed: Boolean) : ApprovalOutcome

    /** A related task must be finished first. */
    data class Blocked(val related: List<RelatedTask>) : ApprovalOutcome

    /** A related task isn't finished; sending again with acknowledgement completes anyway. */
    data class NeedsAcknowledgement(val related: List<RelatedTask>) : ApprovalOutcome

    /** The stage's requirements changed or weren't met; [requirements] are the current ones. */
    data class RequirementsChanged(val requirements: List<StageRequirement>, val requirementsVersion: String?) :
        ApprovalOutcome

    /** The workflow moved on (someone else acted, or the Drive section is gone): reload. */
    data object Stale : ApprovalOutcome
}

/** An earlier step a rejected task can go back to. */
data class SendBackOption(val approvalId: Int, val name: String, val role: String?)

/** A form with saved progress that completing the task discards. */
data class FormDraft(val id: Int, val name: String)

/** sprout_web `getEmployeeClosureOptions`: how an employee task's files are finalized. */
data class EmployeeClosure(
    val needsExpiration: Boolean,
    val nextExpiration: LocalDate?,
    val canMerge: Boolean,
    val pdfs: List<ClosurePdf>,
) {
    val asks: Boolean get() = canMerge || needsExpiration
}

data class ClosurePdf(val id: String, val name: String, val sizeBytes: Long)

/** sprout_api `LateJustificationContextResponse`: where a late task's reason stands for the viewer. */
data class LateContext(
    /** The server's own lateness (UTC days); the phone asks for a reason only when this is true. */
    val late: Boolean = true,
    val canSubmit: Boolean,
    val canReview: Boolean,
    val reviewedByMe: Boolean,
    val satisfied: Boolean,
    val justification: LateJustification?,
    val suggestedDelay: SuggestedDelay?,
    val reviews: List<LateReview>,
) {
    /** Returned for correction: its values come back so they can be fixed, not retyped. */
    val returned: Boolean get() = justification?.returned == true

    /** The latest reviewer's correction note, for a returned reason. */
    val correction: String?
        get() = if (!returned) null else reviews.lastOrNull { !it.agreed && !it.note.isNullOrBlank() }?.note
}

data class LateJustification(
    val id: Long,
    val reasonId: Long,
    val reasonName: String?,
    val explanation: String,
    val evidenceId: Int?,
    val delayId: Long?,
    val submittedBy: String?,
    val returned: Boolean,
)

/** A reported delay whose reason can stand in for the late reason. */
data class SuggestedDelay(
    val id: Long,
    val reasonId: Long,
    val reasonName: String?,
    val explanation: String,
    val evidenceId: Int?,
)

data class LateReview(val name: String?, val role: String?, val agreed: Boolean, val note: String?)

/** A reviewer's own correction of the late reason, saved as they agree with it. */
data class LateEdit(val reasonId: Long, val explanation: String, val evidenceId: Int?)
