package com.waivera.waivera.feature.tasks.review

import com.waivera.waivera.core.data.model.ApprovalMode
import com.waivera.waivera.core.data.model.ApprovalOutcome
import com.waivera.waivera.core.data.model.ApprovalPlan
import com.waivera.waivera.core.data.model.ApprovalSend
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.EmployeeClosure
import com.waivera.waivera.core.data.model.FormDraft
import com.waivera.waivera.core.data.model.LateContext
import com.waivera.waivera.core.data.model.LateEdit
import com.waivera.waivera.core.data.model.SendBackOption
import com.waivera.waivera.core.data.repository.TaskReviewRepository
import java.time.LocalDate

/** The review chain in memory: tests set each answer and read what was sent. */
@Suppress("TooManyFunctions")
class FakeTaskReview : TaskReviewRepository {
    var plan: (ApprovalMode) -> Result<ApprovalPlan> = { Result.success(Companion.plan(it)) }
    val outcomes = ArrayDeque<Result<ApprovalOutcome>>()
    val sent = mutableListOf<ApprovalSend>()
    var drafts: Result<List<FormDraft>> = Result.success(emptyList())
    var closure: Result<EmployeeClosure> =
        Result.success(
            EmployeeClosure(needsExpiration = false, nextExpiration = null, canMerge = false, pdfs = emptyList()),
        )
    val closureChoices = mutableListOf<Pair<List<String>?, LocalDate?>>()
    var options: Result<List<SendBackOption>> = Result.success(
        listOf(SendBackOption(11, "Chris Patel", "Case manager")),
    )
    var sendBackResult: Result<Unit> = Result.success(Unit)
    val sentBack = mutableListOf<SentBack>()
    var late: Result<LateContext> = Result.success(satisfied())
    var reasons: Result<List<ChangeReason>> = Result.success(
        listOf(
            ChangeReason(
                1,
                "Participant unavailable",
                null,
                requiresEvidence = true,
                preset = "Participant was unavailable.",
            ),
            ChangeReason(2, "Waiting on outside agency", null, requiresEvidence = false),
        ),
    )
    var justifyResult: Result<Unit> = Result.success(Unit)
    val justified = mutableListOf<Justified>()
    val agreed = mutableListOf<Triple<Long, String?, LateEdit?>>()

    data class SentBack(val approvalId: Int, val feedback: String, val minutes: Int, val lateReasonId: Long?)

    data class Justified(val reasonId: Long, val explanation: String, val evidenceId: Int?, val delayId: Long?)

    override suspend fun plan(taskId: Int, mode: ApprovalMode): Result<ApprovalPlan> = plan.invoke(mode)

    override suspend fun approve(taskId: Int, send: ApprovalSend): Result<ApprovalOutcome> {
        sent += send
        return outcomes.removeFirstOrNull() ?: Result.success(ApprovalOutcome.Approved(completed = false))
    }

    override suspend fun drafts(taskId: Int): Result<List<FormDraft>> = drafts

    override suspend fun employeeClosure(taskId: Int): Result<EmployeeClosure> = closure

    override suspend fun chooseClosure(taskId: Int, merge: List<String>?, nextExpiration: LocalDate?): Result<Unit> {
        closureChoices += merge to nextExpiration
        return Result.success(Unit)
    }

    override suspend fun sendBackOptions(taskId: Int): Result<List<SendBackOption>> = options

    override suspend fun sendBack(
        taskId: Int,
        option: SendBackOption,
        feedback: String,
        minutes: Int,
        lateReasonId: Long?,
    ): Result<Unit> {
        sentBack += SentBack(option.approvalId, feedback, minutes, lateReasonId)
        return sendBackResult
    }

    override suspend fun lateContext(taskId: Int): Result<LateContext> = late

    override suspend fun lateReasons(): Result<List<ChangeReason>> = reasons

    override suspend fun justify(
        taskId: Int,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
        delayId: Long?,
    ): Result<Unit> {
        justified += Justified(reasonId, explanation, evidenceId, delayId)
        if (justifyResult.isSuccess) late = Result.success(satisfied())
        return justifyResult
    }

    override suspend fun agreeLate(taskId: Int, justificationId: Long, note: String?, edit: LateEdit?): Result<Unit> {
        agreed += Triple(justificationId, note, edit)
        return Result.success(Unit)
    }

    companion object {
        fun plan(mode: ApprovalMode, willComplete: Boolean = false) = ApprovalPlan(
            mode = mode,
            willComplete = willComplete || mode == ApprovalMode.AllRemaining,
            previousFiling = null,
            requirementsVersion = "r1",
            workflowVersion = "w1",
            canApproveAll = true,
            remainingStages = 3,
            requirements = emptyList(),
        )

        fun satisfied() = LateContext(
            canSubmit = true,
            canReview = false,
            reviewedByMe = false,
            satisfied = true,
            justification = null,
            suggestedDelay = null,
            reviews = emptyList(),
        )
    }
}
