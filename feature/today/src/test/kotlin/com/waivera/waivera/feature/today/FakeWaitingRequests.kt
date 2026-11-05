package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.model.ChangeOptions
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.LeaveBalance
import com.waivera.waivera.core.data.model.LeaveDraft
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestOutcome
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.data.model.TrainingDecision
import com.waivera.waivera.core.data.repository.RequestRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The server's pending list as Today reads it: [server] is what the next refresh returns, and
 * [accessFlow] whether the person may review requests. Everything else is out of Today's reach.
 */
internal class FakeWaitingRequests(showsRequests: Boolean = true) : RequestRepository {
    val accessFlow = MutableStateFlow(RequestAccess(showsRequests = showsRequests, loaded = true))
    override val access: StateFlow<RequestAccess> = accessFlow

    private val waitingFlow = MutableStateFlow<List<ChangeRequest>?>(null)
    override val waiting: StateFlow<List<ChangeRequest>?> = waitingFlow

    var server: List<ChangeRequest> = emptyList()
    var refreshes = 0
        private set

    override suspend fun refreshAccess(): Result<Unit> = Result.success(Unit)

    override suspend fun refreshWaiting(): Result<Unit> {
        refreshes++
        waitingFlow.value = server
        return Result.success(Unit)
    }

    override suspend fun mine(): Result<List<ChangeRequest>> = unused()

    override suspend fun decide(request: ChangeRequest, approve: Boolean, training: TrainingDecision?): Result<Unit> =
        unused()

    override suspend fun leaveBalance(employeeId: String, leaveTypeId: Int): Result<LeaveBalance?> = unused()

    override suspend fun leaveZone(): ZoneId? = null

    override suspend fun leaveTypes(): Result<List<LeaveType>> = unused()

    override suspend fun previewLeave(draft: LeaveDraft): Result<LeavePreview> = unused()

    override suspend fun requestTimeOff(draft: LeaveDraft): Result<Unit> = unused()

    override suspend fun trainingCredit(requestId: Int): Result<TrainingCredit> = unused()

    override suspend fun options(taskId: Int): Result<ChangeOptions> = unused()

    override suspend fun moreTimeReasons(): Result<List<ChangeReason>> = unused()

    override suspend fun delayReasons(): Result<List<ChangeReason>> = unused()

    override suspend fun closureReasons(): Result<List<ChangeReason>> = unused()

    override suspend fun files(taskId: Int): Result<List<TaskFile>> = unused()

    override suspend fun askMoreTime(
        taskId: Int,
        newDue: LocalDate,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> = unused()

    override suspend fun reportDelay(
        taskId: Int,
        period: DelayPeriod,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> = unused()

    override suspend fun askClosure(taskId: Int, reasonId: Long, comment: String): Result<Unit> = unused()

    private fun <T> unused(): Result<T> = Result.failure(UnsupportedOperationException("Today never calls this"))
}
