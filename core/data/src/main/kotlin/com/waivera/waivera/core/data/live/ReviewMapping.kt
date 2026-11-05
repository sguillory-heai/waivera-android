package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ApprovalMode
import com.waivera.waivera.core.data.model.ApprovalOutcome
import com.waivera.waivera.core.data.model.ApprovalPlan
import com.waivera.waivera.core.data.model.ApprovalSend
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ClosurePdf
import com.waivera.waivera.core.data.model.EmployeeClosure
import com.waivera.waivera.core.data.model.FieldType
import com.waivera.waivera.core.data.model.FormDraft
import com.waivera.waivera.core.data.model.LateContext
import com.waivera.waivera.core.data.model.LateJustification
import com.waivera.waivera.core.data.model.LateReview
import com.waivera.waivera.core.data.model.PlanDates
import com.waivera.waivera.core.data.model.RelatedTask
import com.waivera.waivera.core.data.model.RequirementField
import com.waivera.waivera.core.data.model.SendBackOption
import com.waivera.waivera.core.data.model.StageRequirement
import com.waivera.waivera.core.data.model.StepStatus
import com.waivera.waivera.core.data.model.SuggestedDelay
import com.waivera.waivera.core.data.model.TaskReviewAccess
import com.waivera.waivera.core.data.model.WorkflowStep
import com.waivera.waivera.core.network.dto.ApprovalErrorResponse
import com.waivera.waivera.core.network.dto.ApprovalRequirementsResponse
import com.waivera.waivera.core.network.dto.ApproveTaskStageBody
import com.waivera.waivera.core.network.dto.CompletionDependencyResponse
import com.waivera.waivera.core.network.dto.ComplianceReasonResponse
import com.waivera.waivera.core.network.dto.EmployeeClosureOptionsResponse
import com.waivera.waivera.core.network.dto.LateContextResponse
import com.waivera.waivera.core.network.dto.RejectOptionResponse
import com.waivera.waivera.core.network.dto.RequirementAnswerBody
import com.waivera.waivera.core.network.dto.StepTargetResponse
import com.waivera.waivera.core.network.dto.TaskCapabilitiesResponse
import com.waivera.waivera.core.network.dto.TaskFormDraftResponse
import com.waivera.waivera.core.network.dto.TaskParticipantResponse
import com.waivera.waivera.core.network.dto.WorkflowRequirementResponse
import com.waivera.waivera.core.network.dto.WorkflowStepResponse
import com.waivera.waivera.core.network.time.WaiveraTime
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** The task review contract (sprout_api workflow, late justification and employee closure) in the phone's terms. */
@Suppress("TooManyFunctions") // One mapper per DTO of the review chain.
object ReviewMapping {
    private const val BLOCKED = "TASK_COMPLETION_BLOCKED"
    private const val WARNING = "TASK_COMPLETION_WARNING_REQUIRED"
    private const val CHANGED = "WORKFLOW_REQUIREMENTS_CHANGED"
    private const val NOT_SATISFIED = "WORKFLOW_REQUIREMENTS_NOT_SATISFIED"
    private const val REJECTED = "Rejected"
    private const val AGREE = "Agree"
    private const val MERGE = "CloseAndMergePdfs"
    private const val CLOSE = "CloseTask"

    fun access(dto: TaskCapabilitiesResponse?, ready: Boolean): TaskReviewAccess = dto?.let {
        TaskReviewAccess(
            canSubmit = it.canSubmit,
            canApprove = it.canApprove,
            canApproveAll = it.canApproveAllRemaining,
            remainingStages = it.remainingApprovalStageCount,
            canReject = it.canReject,
            ready = ready,
        )
    } ?: TaskReviewAccess.None

    /**
     * [assignee] is the task's main assignee: a stage with no role goes to them (sprout_api
     * `TaskService` waterfall), so the note can name them even before the server assigns it.
     */
    fun steps(items: List<WorkflowStepResponse>?, assignee: String? = null): List<WorkflowStep> = items.orEmpty()
        .sortedBy { it.stepOrder }
        .map {
            val role = it.stepRole?.trim()?.takeIf(String::isNotEmpty)
            val status = when (it.status?.uppercase()) {
                "COMPLETED" -> StepStatus.Completed
                "ACTIVE" -> StepStatus.Active
                "FUTURE" -> StepStatus.Future
                else -> StepStatus.Unknown
            }
            WorkflowStep(
                order = it.stepOrder,
                role = humanRole(role),
                status = status,
                approverName = TaskMapping.name(it.actualApprover),
                waitingOn = if (status == StepStatus.Completed) {
                    null
                } else {
                    // A pool's person is only the server's first pick among many, so the role reads truer.
                    val projected = it.projectedApprover?.takeUnless(StepTargetResponse::isPool)?.exactUser
                    TaskMapping.name(projected) ?: assignee.takeIf { role == CASE_WORKER_ROLE }
                },
            )
        }

    /** `[CASE_WORKER]` reads "Case worker"; agency role names ("Supervisor") stay as they are. */
    fun humanRole(role: String?): String? {
        if (role == null || !role.startsWith("[") || !role.endsWith("]")) return role
        val words = role.substring(1, role.length - 1).replace('_', ' ').lowercase(Locale.ROOT)
        return words.replaceFirstChar { it.titlecase(Locale.ROOT) }.takeIf(String::isNotBlank)
    }

    /** The server's role for a stage with no role on a client task: the task's own case worker. */
    private const val CASE_WORKER_ROLE = "[CASE_WORKER]"

    fun planDates(participant: TaskParticipantResponse?): PlanDates? = participant?.let {
        PlanDates(WaiveraTime.parseLocalDate(it.cpocDate), WaiveraTime.parseLocalDate(it.cpocEndDate))
    }

    fun plan(dto: ApprovalRequirementsResponse, asked: ApprovalMode): ApprovalPlan = ApprovalPlan(
        mode = ApprovalMode.entries.firstOrNull { it.name.equals(dto.approvalMode, ignoreCase = true) } ?: asked,
        willComplete = dto.willCompleteTask,
        previousFiling = WaiveraTime.parseLocalDate(dto.previousDriveFilingDate),
        requirementsVersion = dto.requirementsVersion,
        workflowVersion = dto.workflowVersion,
        canApproveAll = dto.canApproveAllRemaining,
        remainingStages = dto.remainingStageCount,
        requirements = requirements(dto.requirements),
    )

    fun requirements(items: List<WorkflowRequirementResponse>): List<StageRequirement> = items.map { item ->
        StageRequirement(
            actionId = item.actionId,
            key = item.requirementKey,
            actionType = item.actionType.orEmpty(),
            title = item.title?.trim().orEmpty(),
            description = item.description?.trim()?.takeIf(String::isNotEmpty),
            fields = item.fields.map { field ->
                RequirementField(
                    key = field.key,
                    label = field.label?.trim()?.takeIf(String::isNotEmpty) ?: field.key,
                    type = when (field.fieldType?.lowercase()) {
                        "date" -> FieldType.Date
                        "text" -> FieldType.Text
                        "textarea" -> FieldType.LongText
                        "number" -> FieldType.Number
                        else -> FieldType.Unsupported
                    },
                    required = field.required,
                    defaultValue = field.defaultValue,
                )
            },
        )
    }

    /** sprout_web `continueWorkflowApproval`'s request; the server checks both versions. */
    fun body(send: ApprovalSend): ApproveTaskStageBody = ApproveTaskStageBody(
        mode = send.plan.mode.name,
        expectedWorkflowVersion = send.plan.workflowVersion,
        idempotencyKey = send.idempotencyKey,
        requirementsVersion = send.plan.requirementsVersion,
        requirementResponses = send.plan.requirements.mapNotNull { requirement ->
            val values = send.answers[requirement.actionId] ?: return@mapNotNull null
            RequirementAnswerBody(
                actionId = requirement.actionId,
                requirementKey = requirement.key,
                values = buildJsonObject {
                    values.filterValues(String::isNotBlank).forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                },
            )
        },
        acknowledgeRelationshipWarnings = send.acknowledgeWarnings,
        closeUnfinalizedDrafts = send.discardDrafts,
        useOriginalDriveSection = send.useOriginalSection,
        expectedPreviousDriveFilingDate = send.plan.previousFiling?.toString()?.takeIf { send.useOriginalSection },
    )

    /** What a 409/422 refusal means for the person; codes the phone has no step for reload the task. */
    fun refusal(dto: ApprovalErrorResponse): ApprovalOutcome = when (dto.code) {
        BLOCKED -> ApprovalOutcome.Blocked(dto.completion?.blockers.orEmpty().map(::related))
        WARNING -> ApprovalOutcome.NeedsAcknowledgement(dto.completion?.warnings.orEmpty().map(::related))
        CHANGED, NOT_SATISFIED -> dto.requirements?.let {
            ApprovalOutcome.RequirementsChanged(requirements(it), dto.requirementsVersion)
        } ?: ApprovalOutcome.Stale
        else -> ApprovalOutcome.Stale
    }

    private fun related(dto: CompletionDependencyResponse) = RelatedTask(
        id = dto.sourceTaskId,
        key = listOfNotNull(dto.organizationAbbreviation?.trim()?.takeIf(String::isNotEmpty), dto.sourceTaskFriendlyId)
            .joinToString("-"),
        title = dto.sourceTaskTitle?.trim().orEmpty(),
        statusName = dto.sourceTaskStatus?.trim()?.takeIf(String::isNotEmpty),
        relationship = (dto.sourceToTargetName ?: dto.relationshipName)?.trim()?.takeIf(String::isNotEmpty),
    )

    fun options(items: List<RejectOptionResponse>): List<SendBackOption> = items.map {
        SendBackOption(
            approvalId = it.taskApprovalId,
            name = it.displayName?.trim()?.takeIf(String::isNotEmpty).orEmpty(),
            role = humanRole(it.roleName?.trim()?.takeIf(String::isNotEmpty)),
        )
    }

    fun drafts(items: List<TaskFormDraftResponse>): List<FormDraft> =
        items.map { FormDraft(it.id, it.formName?.trim().orEmpty()) }

    fun closure(dto: EmployeeClosureOptionsResponse) = EmployeeClosure(
        needsExpiration = dto.requiresNextExpirationDate,
        nextExpiration = WaiveraTime.parseLocalDate(dto.nextExpirationDate),
        canMerge = dto.canMerge,
        pdfs = dto.eligiblePdfs.map { ClosurePdf(it.employeeFileId, it.fileName?.trim().orEmpty(), it.size) },
    )

    fun closureMode(merge: List<String>?): String = if (merge != null) MERGE else CLOSE

    fun late(dto: LateContextResponse) = LateContext(
        late = dto.isLate,
        canSubmit = dto.canSubmit,
        canReview = dto.canReview,
        reviewedByMe = dto.hasCurrentUserReviewed,
        satisfied = dto.satisfiesSubmission,
        justification = dto.justification?.let {
            LateJustification(
                id = it.id,
                reasonId = it.reasonCodeId,
                reasonName = it.reasonName?.trim()?.takeIf(String::isNotEmpty),
                explanation = it.explanation.orEmpty(),
                evidenceId = it.evidenceAttachmentId,
                delayId = it.sourceTaskDelayId,
                submittedBy = it.submittedByName?.trim()?.takeIf(String::isNotEmpty),
                returned = it.status.equals(REJECTED, ignoreCase = true),
            )
        },
        suggestedDelay = dto.suggestedDelay?.let {
            SuggestedDelay(
                id = it.id,
                reasonId = it.reasonCodeId,
                reasonName = it.reasonName?.trim()?.takeIf(String::isNotEmpty),
                explanation = it.explanation.orEmpty(),
                evidenceId = it.evidenceAttachmentId,
            )
        },
        reviews = dto.reviews.map {
            LateReview(
                name = it.reviewerName?.trim()?.takeIf(String::isNotEmpty),
                role = humanRole(it.reviewerRole?.trim()?.takeIf(String::isNotEmpty)),
                agreed = it.decision.equals(AGREE, ignoreCase = true),
                note = it.note?.trim()?.takeIf(String::isNotEmpty),
            )
        },
    )

    /** The web's late-justification filter: system codes unless hidden, agency codes while active. */
    fun lateReasons(items: List<ComplianceReasonResponse>): List<ChangeReason> = items
        .filter { (if (it.isSystemCode) !it.isHiddenFromUserSelection else it.isActive) }
        .filter { it.canBeUsedForLateJustification && !it.name.isNullOrBlank() }
        .map {
            ChangeReason(
                id = it.id,
                name = it.name.orEmpty().trim(),
                description = it.userFacingDescription?.trim()?.takeIf(String::isNotEmpty),
                requiresEvidence = it.requiresEvidence,
                preset = it.presetExplanation?.takeIf(String::isNotBlank),
            )
        }
}
