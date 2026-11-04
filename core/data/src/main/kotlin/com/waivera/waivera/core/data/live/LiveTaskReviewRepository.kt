package com.waivera.waivera.core.data.live

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
import com.waivera.waivera.core.network.api.RequestApi
import com.waivera.waivera.core.network.api.WorkflowApi
import com.waivera.waivera.core.network.dto.ApprovalErrorResponse
import com.waivera.waivera.core.network.dto.EmployeeClosureBody
import com.waivera.waivera.core.network.dto.LateJustificationBody
import com.waivera.waivera.core.network.dto.LateReviewBody
import com.waivera.waivera.core.network.dto.RejectTaskBody
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.toWaiveraError
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** Submit, Approve and Reject on a task through the same endpoints, in the same order, as sprout_web. */
@Singleton
@Suppress("TooManyFunctions") // One function per step of the review chain.
class LiveTaskReviewRepository @Inject constructor(
    private val api: WorkflowApi,
    private val requests: RequestApi,
    private val json: Json,
) : TaskReviewRepository {
    override suspend fun plan(taskId: Int, mode: ApprovalMode): Result<ApprovalPlan> = catchingWaivera {
        ReviewMapping.plan(apiCall { api.requirements(taskId, mode.name) }, mode)
    }

    override suspend fun approve(taskId: Int, send: ApprovalSend): Result<ApprovalOutcome> = catchingWaivera {
        val response = api.approve(taskId, ReviewMapping.body(send))
        when {
            response.isSuccessful -> ApprovalOutcome.Approved(completed = response.body()?.taskCompleted == true)
            response.code() == HTTP_CONFLICT || response.code() == HTTP_UNPROCESSABLE -> {
                val text = runCatching { response.errorBody()?.use { it.string() } }.getOrNull().orEmpty()
                val refusal = runCatching { json.decodeFromString<ApprovalErrorResponse>(text) }.getOrNull()
                refusal?.let(ReviewMapping::refusal) ?: ApprovalOutcome.Stale
            }
            else -> throw response.toWaiveraError()
        }
    }

    override suspend fun drafts(taskId: Int): Result<List<FormDraft>> = catchingWaivera {
        ReviewMapping.drafts(apiCall { api.drafts(taskId) })
    }

    override suspend fun employeeClosure(taskId: Int): Result<EmployeeClosure> = catchingWaivera {
        ReviewMapping.closure(apiCall { api.employeeClosureOptions(taskId) })
    }

    override suspend fun chooseClosure(taskId: Int, merge: List<String>?, nextExpiration: LocalDate?): Result<Unit> =
        catchingWaivera {
            apiCall {
                api.employeeClosureChoice(
                    taskId,
                    EmployeeClosureBody(
                        mode = ReviewMapping.closureMode(merge),
                        idempotencyKey = "employee-close-$taskId-${UUID.randomUUID()}",
                        orderedEmployeeFileIds = merge.orEmpty(),
                        nextExpirationDate = nextExpiration?.toString(),
                    ),
                )
            }
            Unit
        }

    override suspend fun sendBackOptions(taskId: Int): Result<List<SendBackOption>> = catchingWaivera {
        try {
            ReviewMapping.options(apiCall { api.rejectOptions(taskId) })
        } catch (_: WaiveraError.NotFound) {
            // The server answers 404 when there is no earlier step, as the web reads it.
            emptyList()
        }
    }

    override suspend fun sendBack(
        taskId: Int,
        option: SendBackOption,
        feedback: String,
        minutes: Int,
        lateReasonId: Long?,
    ): Result<Unit> = catchingWaivera {
        val result = apiCall {
            api.reject(
                taskId,
                RejectTaskBody(
                    feedback = feedback,
                    taskApprovalId = option.approvalId,
                    durationInMinutes = minutes,
                    lateJustificationAdjustmentId = lateReasonId,
                    lateJustificationDecision = RETURN_DECISION.takeIf { lateReasonId != null },
                ),
            )
        }
        // The envelope can say no inside a 200; the web misses this, the phone does not.
        if (!result.success) throw WaiveraError.Validation(result.errorCode)
    }

    override suspend fun lateContext(taskId: Int): Result<LateContext> = catchingWaivera {
        ReviewMapping.late(apiCall { api.lateContext(taskId) })
    }

    override suspend fun lateReasons(): Result<List<ChangeReason>> = catchingWaivera {
        ReviewMapping.lateReasons(apiCall { requests.complianceReasons() })
    }

    override suspend fun justify(
        taskId: Int,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
        delayId: Long?,
    ): Result<Unit> = catchingWaivera {
        apiCall { api.justify(taskId, LateJustificationBody(reasonId, explanation, evidenceId, delayId)) }
        Unit
    }

    override suspend fun agreeLate(taskId: Int, justificationId: Long, note: String?, edit: LateEdit?): Result<Unit> =
        catchingWaivera {
            apiCall {
                api.reviewJustification(
                    taskId,
                    justificationId,
                    LateReviewBody(
                        decision = AGREE_DECISION,
                        note = note?.trim()?.takeIf(String::isNotEmpty),
                        reasonCodeId = edit?.reasonId,
                        explanation = edit?.explanation,
                        evidenceAttachmentId = edit?.evidenceId,
                    ),
                )
            }
            Unit
        }

    private companion object {
        const val HTTP_CONFLICT = 409
        const val HTTP_UNPROCESSABLE = 422
        const val AGREE_DECISION = "Agree"
        const val RETURN_DECISION = "ReturnForClarification"
    }
}
