package com.waivera.waivera.core.demo

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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/** A review chain with no extra steps: every approval goes straight through after a short wait. */
@Singleton
@Suppress("TooManyFunctions") // Mirrors the repository's one function per step.
class DemoTaskReviewRepository @Inject constructor(private val backend: DemoBackend) : TaskReviewRepository {
    override suspend fun plan(taskId: Int, mode: ApprovalMode): Result<ApprovalPlan> {
        backend.failRefreshes?.let { return Result.failure(it) }
        return Result.success(
            ApprovalPlan(
                mode = mode,
                willComplete = mode == ApprovalMode.AllRemaining,
                previousFiling = null,
                requirementsVersion = "demo",
                workflowVersion = "demo",
                canApproveAll = true,
                remainingStages = REMAINING,
                requirements = emptyList(),
            ),
        )
    }

    override suspend fun approve(taskId: Int, send: ApprovalSend): Result<ApprovalOutcome> {
        delay(WAIT_MILLIS)
        return Result.success(ApprovalOutcome.Approved(completed = send.plan.willComplete))
    }

    override suspend fun drafts(taskId: Int): Result<List<FormDraft>> = Result.success(emptyList())

    override suspend fun employeeClosure(taskId: Int): Result<EmployeeClosure> = Result.success(
        EmployeeClosure(needsExpiration = false, nextExpiration = null, canMerge = false, pdfs = emptyList()),
    )

    override suspend fun chooseClosure(taskId: Int, merge: List<String>?, nextExpiration: LocalDate?): Result<Unit> =
        Result.success(Unit)

    override suspend fun sendBackOptions(taskId: Int): Result<List<SendBackOption>> =
        Result.success(listOf(SendBackOption(1, "Priya Raman", "Assignee")))

    override suspend fun sendBack(
        taskId: Int,
        option: SendBackOption,
        feedback: String,
        minutes: Int,
        lateReasonId: Long?,
    ): Result<Unit> {
        delay(WAIT_MILLIS)
        return Result.success(Unit)
    }

    override suspend fun lateContext(taskId: Int): Result<LateContext> = Result.success(
        LateContext(
            canSubmit = true,
            canReview = false,
            reviewedByMe = false,
            satisfied = true,
            justification = null,
            suggestedDelay = null,
            reviews = emptyList(),
        ),
    )

    override suspend fun lateReasons(): Result<List<ChangeReason>> = Result.success(
        listOf(
            ChangeReason(1, "Participant unavailable", null, requiresEvidence = true),
            ChangeReason(2, "Waiting on outside agency", null, requiresEvidence = false),
        ),
    )

    override suspend fun justify(
        taskId: Int,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
        delayId: Long?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun agreeLate(taskId: Int, justificationId: Long, note: String?, edit: LateEdit?): Result<Unit> =
        Result.success(Unit)

    private companion object {
        const val REMAINING = 2
        const val WAIT_MILLIS = 600L
    }
}
