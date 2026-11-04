package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ChangeOptions
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.LeaveBalance
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestOutcome
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.data.model.TrainingDecision
import com.waivera.waivera.core.data.repository.RequestRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A scripted requests server. [decide] behaves like the live one: optimistic, restored on failure. */
internal class FakeRequests(
    access: RequestAccess = RequestAccess(
        canAskMoreTime = true,
        canAskClosure = true,
        showsRequests = true,
        loaded = true,
    ),
) : RequestRepository {
    val accessFlow = MutableStateFlow(access)
    override val access: StateFlow<RequestAccess> = accessFlow

    private val waitingFlow = MutableStateFlow<List<ChangeRequest>?>(null)
    override val waiting: StateFlow<List<ChangeRequest>?> = waitingFlow

    var serverWaiting: List<ChangeRequest> = emptyList()
    var waitingResult: Result<Unit> = Result.success(Unit)
    var waitingGate: CompletableDeferred<Unit>? = null
    var mineResult: Result<List<ChangeRequest>> = Result.success(emptyList())
    var decideResult: Result<Unit> = Result.success(Unit)
    var decideGate: CompletableDeferred<Unit>? = null
    val decisions = mutableListOf<Pair<Int, Boolean>>()
    val trainingDecisions = mutableListOf<TrainingDecision?>()
    var balance: Result<LeaveBalance?> = Result.success(null)
    var leaveZone: ZoneId? = null
    var training: Result<TrainingCredit> = Result.failure(IllegalStateException("no training credit"))

    var optionsResult: Result<ChangeOptions> = Result.success(options())
    var reasons: Result<List<ChangeReason>> =
        Result.success(listOf(ChangeReason(1, "Participant unavailable", null, false)))
    var closure: Result<List<ChangeReason>> = Result.success(
        listOf(
            ChangeReason(5, "Moved out of state", null, false),
            ChangeReason(6, "Other", null, false, needsComment = true),
        ),
    )
    var filesResult: Result<List<TaskFile>> = Result.success(emptyList())
    var askResult: Result<RequestOutcome> = Result.success(RequestOutcome.Pending)
    var askGate: CompletableDeferred<Unit>? = null
    val asked = mutableListOf<String>()

    override suspend fun refreshAccess(): Result<Unit> = Result.success(Unit)

    override suspend fun refreshWaiting(): Result<Unit> {
        waitingGate?.await()
        waitingResult.onSuccess { waitingFlow.value = serverWaiting }
        return waitingResult
    }

    override suspend fun mine(): Result<List<ChangeRequest>> = mineResult

    override suspend fun decide(request: ChangeRequest, approve: Boolean, training: TrainingDecision?): Result<Unit> {
        val before = waitingFlow.value
        decisions += request.id to approve
        trainingDecisions += training
        waitingFlow.value = before?.filterNot { it.id == request.id }
        decideGate?.await()
        decideResult.onFailure { waitingFlow.value = before }
        return decideResult
    }

    override suspend fun leaveBalance(employeeId: String, leaveTypeId: Int): Result<LeaveBalance?> = balance

    override suspend fun leaveZone(): ZoneId? = leaveZone

    override suspend fun trainingCredit(requestId: Int): Result<TrainingCredit> = training

    override suspend fun options(taskId: Int): Result<ChangeOptions> = optionsResult

    override suspend fun moreTimeReasons(): Result<List<ChangeReason>> = reasons

    override suspend fun delayReasons(): Result<List<ChangeReason>> = reasons

    override suspend fun closureReasons(): Result<List<ChangeReason>> = closure

    override suspend fun files(taskId: Int): Result<List<TaskFile>> = filesResult

    override suspend fun askMoreTime(
        taskId: Int,
        newDue: LocalDate,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> {
        asked += "more:$newDue:$reasonId:$evidenceId"
        askGate?.await()
        return askResult
    }

    override suspend fun reportDelay(
        taskId: Int,
        period: DelayPeriod,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> {
        asked += "delay:${period.start}:${period.end}:$reasonId"
        askGate?.await()
        return askResult
    }

    override suspend fun askClosure(taskId: Int, reasonId: Long, comment: String): Result<Unit> {
        asked += "closure:$reasonId:$comment"
        askGate?.await()
        return askResult.map { }
    }

    companion object {
        val Due: LocalDate = LocalDate.of(2026, 11, 6)

        fun options(
            canAskMoreTime: Boolean = true,
            delay: DelayPeriod? = DelayPeriod(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 12), renewal = false),
            pending: Boolean = false,
        ) = ChangeOptions(
            taskId = 7,
            currentDue = Due,
            canAskMoreTime = canAskMoreTime,
            delay = delay,
            hasPendingRequest = pending,
        )

        fun request(
            id: Int,
            kind: RequestKind = RequestKind.MoreTime,
            status: RequestStatus = RequestStatus.Pending,
            requester: String = "Priya Raman",
        ) = ChangeRequest(
            id = id,
            kind = kind,
            typeName = when (kind) {
                RequestKind.MoreTime -> "Task Extension Request"
                RequestKind.Delay -> "Task Delay Request"
                RequestKind.Closure -> "Task Deletion Request"
                RequestKind.Other -> "Exception Period Request"
                else -> kind.name
            },
            status = status,
            requesterId = "usr_priya",
            requesterName = requester,
            approverName = null,
            requestedAt = Instant.parse("2026-11-04T15:12:00Z"),
            decidedAt = null,
            taskId = 1051,
            taskTitle = "Service authorization follow-up",
            reason = "Provider paperwork pending",
            explanation = "Waiting on the provider.",
            currentDue = LocalDate.of(2026, 11, 5),
            requestedDue = LocalDate.of(2026, 11, 20),
        )
    }
}
