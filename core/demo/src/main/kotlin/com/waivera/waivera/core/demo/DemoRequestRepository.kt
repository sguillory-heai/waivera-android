@file:Suppress("MagicNumber")

package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.model.ChangeOptions
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestOutcome
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.data.repository.RequestRepository
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Sample change requests for demo mode and screenshots; asks and decisions stay in memory. */
@Singleton
@Suppress("TooManyFunctions")
class DemoRequestRepository @Inject constructor(private val backend: DemoBackend) : RequestRepository {
    private val today: LocalDate get() = backend.now().atZone(DemoData.ZONE).toLocalDate()

    private val _access = MutableStateFlow(
        RequestAccess(canAskMoreTime = true, canAskClosure = true, showsRequests = true, loaded = true),
    )
    override val access: StateFlow<RequestAccess> = _access.asStateFlow()

    private val _waiting = MutableStateFlow<List<ChangeRequest>?>(null)
    override val waiting: StateFlow<List<ChangeRequest>?> = _waiting.asStateFlow()

    private val asked = MutableStateFlow<List<ChangeRequest>>(emptyList())
    private var nextId = 7_000

    override suspend fun refreshAccess(): Result<Unit> = backend.refresh()

    override suspend fun refreshWaiting(): Result<Unit> {
        backend.failRefreshes?.let { return Result.failure(it) }
        if (_waiting.value == null) _waiting.value = sampleWaiting()
        return Result.success(Unit)
    }

    override suspend fun mine(): Result<List<ChangeRequest>> {
        backend.failRefreshes?.let { return Result.failure(it) }
        return Result.success(asked.value + sampleMine())
    }

    override suspend fun decide(request: ChangeRequest, approve: Boolean): Result<Unit> {
        backend.failRefreshes?.let { return Result.failure(it) }
        _waiting.update { list -> list?.filterNot { it.id == request.id } }
        return Result.success(Unit)
    }

    override suspend fun options(taskId: Int): Result<ChangeOptions> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val task = DemoTasks.casework(today).firstOrNull { it.id == taskId }
        val overdue = task?.due?.isBefore(today) == true
        val pending = asked.value.any { it.taskId == taskId && it.status == RequestStatus.Pending }
        return Result.success(
            ChangeOptions(
                taskId = taskId,
                currentDue = task?.due,
                canAskMoreTime = !overdue && !pending,
                delay = if (overdue && !pending) DelayPeriod(today, today.plusDays(7), renewal = false) else null,
                hasPendingRequest = pending,
            ),
        )
    }

    override suspend fun moreTimeReasons(): Result<List<ChangeReason>> = Result.success(Reasons)

    override suspend fun delayReasons(): Result<List<ChangeReason>> = Result.success(Reasons)

    override suspend fun closureReasons(): Result<List<ChangeReason>> = Result.success(ClosureReasons)

    override suspend fun files(taskId: Int): Result<List<TaskFile>> =
        Result.success(listOf(TaskFile(501, "Travel note.pdf"), TaskFile(502, "Visit summary.pdf")))

    override suspend fun askMoreTime(
        taskId: Int,
        newDue: LocalDate,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> = ask(taskId, RequestKind.MoreTime, reasonId, explanation) {
        it.copy(requestedDue = newDue, currentDue = DemoTasks.casework(today).firstOrNull { t -> t.id == taskId }?.due)
    }

    override suspend fun reportDelay(
        taskId: Int,
        period: DelayPeriod,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> = ask(taskId, RequestKind.Delay, reasonId, explanation) {
        it.copy(delayStart = period.start, delayEnd = period.end)
    }

    override suspend fun askClosure(taskId: Int, reasonId: Long, comment: String): Result<Unit> =
        ask(taskId, RequestKind.Closure, reasonId, comment) { it }.map { }

    private fun ask(
        taskId: Int,
        kind: RequestKind,
        reasonId: Long,
        explanation: String,
        details: (ChangeRequest) -> ChangeRequest,
    ): Result<RequestOutcome> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val task = DemoTasks.casework(today).firstOrNull { it.id == taskId }
        val request = details(
            ChangeRequest(
                id = nextId++,
                kind = kind,
                typeName = kind.name,
                status = RequestStatus.Pending,
                requesterId = backend.myUserId,
                requesterName = "You",
                approverName = null,
                requestedAt = backend.now(),
                decidedAt = null,
                taskId = taskId,
                taskTitle = task?.title,
                reason = (Reasons + ClosureReasons).firstOrNull { it.id == reasonId }?.name,
                explanation = explanation.trim().ifEmpty { null },
            ),
        )
        asked.update { listOf(request) + it }
        return Result.success(RequestOutcome.Pending)
    }

    private fun sampleWaiting(): List<ChangeRequest> {
        val now = backend.now()
        return listOf(
            ChangeRequest(
                id = 6_101, kind = RequestKind.MoreTime, typeName = "Task Extension Request",
                status = RequestStatus.Pending, requesterId = "demo-priya", requesterName = "Priya Raman",
                approverName = null, requestedAt = now.minus(Duration.ofMinutes(48)), decidedAt = null,
                taskId = 1063, taskTitle = "Monthly contact note", reason = "Participant unavailable",
                explanation = "Ana is visiting family until the 9th; the visit is booked for the 11th.",
                currentDue = today.plusDays(3), requestedDue = today.plusDays(8),
            ),
            ChangeRequest(
                id = 6_102, kind = RequestKind.Closure, typeName = "Task Deletion Request",
                status = RequestStatus.Pending, requesterId = "demo-luis", requesterName = "Luis Ortega",
                approverName = null, requestedAt = now.minus(Duration.ofHours(20)), decidedAt = null,
                taskId = 1078, taskTitle = "Annual plan signatures", reason = "Duplicate task",
                explanation = "The signatures were collected on the renewal task.",
            ),
        )
    }

    private fun sampleMine(): List<ChangeRequest> {
        val now = backend.now()
        return listOf(
            ChangeRequest(
                id = 6_001, kind = RequestKind.Delay, typeName = "Task Delay Request",
                status = RequestStatus.Approved, requesterId = backend.myUserId, requesterName = "You",
                approverName = "Priya Raman", requestedAt = now.minus(Duration.ofDays(3)),
                decidedAt = now.minus(Duration.ofDays(2)), taskId = 1017, taskTitle = "Update emergency contacts",
                reason = "Waiting on records", explanation = "The county office is resending the form.",
                delayStart = today.minusDays(3), delayEnd = today.plusDays(4),
            ),
        )
    }

    private companion object {
        val ClosureReasons = listOf(
            ChangeReason(31, "Participant moved out of the area", null, false),
            ChangeReason(32, "Duplicate task", null, false),
            ChangeReason(33, "Other", null, false, needsComment = true),
        )
        val Reasons = listOf(
            ChangeReason(
                21,
                "Participant unavailable",
                "The participant could not meet in time.",
                requiresEvidence = false,
            ),
            ChangeReason(
                22,
                "Waiting on records",
                "Records from another office are needed first.",
                requiresEvidence = true,
            ),
            ChangeReason(23, "Staff illness", null, requiresEvidence = false),
        )
    }
}
