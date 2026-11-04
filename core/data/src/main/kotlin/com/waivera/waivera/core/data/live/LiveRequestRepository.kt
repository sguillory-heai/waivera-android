package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ChangeOptions
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.LeaveBalance
import com.waivera.waivera.core.data.model.LeaveDraft
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveSendException
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestOutcome
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.data.model.TrainingDecision
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.realtime.AppForegroundMonitor
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.api.RequestApi
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.LeavePreviewBody
import com.waivera.waivera.core.network.dto.RequestActionBody
import com.waivera.waivera.core.network.dto.TaskClosureRequest
import com.waivera.waivera.core.network.dto.TaskDelayRequest
import com.waivera.waivera.core.network.dto.TaskExtensionRequest
import com.waivera.waivera.core.network.dto.TimeOffBody
import com.waivera.waivera.core.network.dto.TrainingDecisionBody
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.error.requireData
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Change requests straight from sprout_api; nothing is stored on the device. What to offer is
 * loaded from `Authentication/bootstrap` when someone signs in and each time the app comes back
 * to the foreground, so a role change on the web reaches the phone without signing out.
 */
@Singleton
@Suppress("TooManyFunctions", "LongParameterList")
class LiveRequestRepository @Inject constructor(
    private val api: RequestApi,
    private val authApi: AuthenticationApi,
    private val session: SessionRepository,
    private val json: Json,
    foreground: AppForegroundMonitor,
    @ApplicationScope scope: CoroutineScope,
) : RequestRepository {
    private val _access = MutableStateFlow(RequestAccess())
    override val access: StateFlow<RequestAccess> = _access.asStateFlow()

    private val _waiting = MutableStateFlow<List<ChangeRequest>?>(null)
    override val waiting: StateFlow<List<ChangeRequest>?> = _waiting.asStateFlow()

    /** Requests decided on this device whose answer is still out, so a refresh cannot bring them back. */
    private val deciding = MutableStateFlow<Set<Int>>(emptySet())
    private val accessLock = Mutex()

    init {
        scope.launch {
            var lastUser: String? = null
            combine(
                session.state.map { (it as? SessionState.SignedIn)?.user?.userId }.distinctUntilChanged(),
                foreground.isForeground,
            ) { user, visible -> user to visible }
                .distinctUntilChanged()
                .collectLatest { (user, visible) ->
                    // Nothing from one account outlives its session.
                    if (user != lastUser) clear()
                    lastUser = user
                    if (user != null && visible) {
                        refreshAccess()
                        refreshWaiting()
                    }
                }
        }
    }

    override suspend fun refreshAccess(): Result<Unit> = loadAccess(onlyIfMissing = false)

    // Callers that only need access loaded share one bootstrap call instead of queueing several.
    override suspend fun ensureAccess(): Result<Unit> = loadAccess(onlyIfMissing = true)

    private suspend fun loadAccess(onlyIfMissing: Boolean): Result<Unit> = catchingWaivera {
        accessLock.withLock {
            if (onlyIfMissing && _access.value.loaded) return@withLock
            val user = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
            val bootstrap = apiCall { authApi.bootstrap() }.requireData()
            // An answer that lands after sign-out or a switch of account belongs to nobody now.
            if (session.state.value.currentUserId == user) _access.value = RequestMapping.access(bootstrap)
        }
    }

    override suspend fun refreshWaiting(): Result<Unit> = catchingWaivera {
        val user = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        val page = apiCall { api.waiting() }.requireData()
        val open = deciding.value
        if (session.state.value.currentUserId == user) {
            _waiting.value = page.items.map(RequestMapping::request).filterNot { it.id in open }
        }
    }

    override suspend fun mine(): Result<List<ChangeRequest>> = catchingWaivera {
        apiCall { api.mine() }.requireData().items.map(RequestMapping::request)
    }

    override suspend fun decide(request: ChangeRequest, approve: Boolean, training: TrainingDecision?): Result<Unit> {
        val before = _waiting.value
        deciding.update { it + request.id }
        _waiting.update { list -> list?.filterNot { it.id == request.id } }
        val result = try {
            catchingWaivera { send(request, approve, training) }
        } finally {
            // Also when the screen leaves mid-call: the next refresh shows the server's answer.
            deciding.update { it - request.id }
        }
        result.onFailure { failure ->
            // Someone else already decided it: it stays gone. Anything else puts it back where it was.
            if (WaiveraError.from(failure) !is WaiveraError.NotFound) {
                _waiting.update { current ->
                    val list = current.orEmpty()
                    if (list.any { it.id == request.id }) {
                        list
                    } else {
                        val order = before.orEmpty().map { it.id }
                        (list + request).sortedBy { order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    }
                }
            }
        }
        return result
    }

    /** sprout_web `AdminRequestsPanel.handleApproveReject` and `ExternalTrainingReview.decide`. */
    private suspend fun send(request: ChangeRequest, approve: Boolean, training: TrainingDecision?) {
        val action = RequestActionBody(
            action = if (approve) "approve" else "reject",
            // The web's fixed wording; it asks the reviewer for no reason on these.
            reason = if (approve) "Approved via Admin Panel" else "Rejected via Admin Panel",
        )
        val body = when (request.kind) {
            RequestKind.TimeOff, RequestKind.TimeOffCancellation -> apiCall { api.decideTimeOff(request.id, action) }
            RequestKind.ProfileChange -> apiCall { api.decideProfileChange(request.id, action) }
            // A training decision needs the credit's revision; the web's Other requests stay on the web.
            RequestKind.TrainingCredit -> training?.let { decision ->
                apiCall {
                    api.decideTrainingCredit(
                        request.id,
                        TrainingDecisionBody(decision.revision, approve, decision.reason.trim()),
                    )
                }
            }
            RequestKind.Other -> null
            else -> apiCall { if (approve) api.approve(request.id) else api.reject(request.id) }
        }
        if (body == null || !succeeded(body)) throw WaiveraError.Unexpected()
    }

    override suspend fun leaveBalance(employeeId: String, leaveTypeId: Int): Result<LeaveBalance?> = catchingWaivera {
        // Balances are for leave administrators; other reviewers decide without them, as on the web.
        val summary = runCatching { apiCall { api.leaveBalances(employeeId) }.requireData() }
            .getOrElse { error -> if (error is WaiveraError.Forbidden) return@catchingWaivera null else throw error }
        summary.balances.firstOrNull { it.id == leaveTypeId }?.let(ApprovalMapping::balance)
    }

    override suspend fun leaveZone(): ZoneId? = catchingWaivera {
        WaiveraTime.zoneOrNull(apiCall { api.leaveSettings() }.requireData().timeZone)
    }.getOrNull()

    override suspend fun leaveTypes(): Result<List<LeaveType>> = catchingWaivera {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        coroutineScope {
            val types = async { apiCall { api.leaveTypes() } }
            // A person may always read their own balances; without them the types still list.
            val balances =
                async { catchingWaivera { apiCall { api.leaveBalances(me) }.requireData().balances }.getOrNull() }
            LeaveMapping.leaveTypes(types.await(), balances.await())
        }
    }

    override suspend fun previewLeave(draft: LeaveDraft): Result<LeavePreview> = catchingWaivera {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        val body = LeavePreviewBody(
            employeeId = me,
            leaveTypeId = draft.leaveTypeId,
            startTime = draft.start.toString(),
            endTime = draft.end.toString(),
            requestType = draft.requestType(),
        )
        LeaveMapping.preview(apiCall { api.previewLeave(body) }.requireData())
    }

    override suspend fun requestTimeOff(draft: LeaveDraft): Result<Unit> = catchingWaivera {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        val body = TimeOffBody(
            userId = me,
            typeOfLeave = draft.leaveTypeId,
            comment = draft.note?.trim()?.takeIf(String::isNotEmpty),
            timeOffStartTime = draft.start.toString(),
            timeOffEndTime = draft.end.toString(),
            requestType = draft.requestType(),
        )
        if (!succeeded(apiCall { api.requestTimeOff(body) })) throw WaiveraError.Unexpected()
    }.fold(
        onSuccess = { Result.success(Unit) },
        // Refusals the person can fix get the app's own words; everything else stays a WaiveraError.
        onFailure = { error ->
            val waivera = WaiveraError.from(error)
            Result.failure(LeaveMapping.sendProblem(waivera)?.let(::LeaveSendException) ?: waivera)
        },
    )

    private fun LeaveDraft.requestType() = if (partialDay) PARTIAL_DAY else FULL_DAY

    override suspend fun trainingCredit(requestId: Int): Result<TrainingCredit> = catchingWaivera {
        ApprovalMapping.training(requestId, apiCall { api.trainingCredit(requestId) })
    }

    override suspend fun options(taskId: Int): Result<ChangeOptions> = catchingWaivera {
        RequestMapping.options(apiCall { api.eligibility(taskId) })
    }

    override suspend fun moreTimeReasons(): Result<List<ChangeReason>> = catchingWaivera {
        RequestMapping.reasons(apiCall { api.complianceReasons() }, forDelay = false)
    }

    override suspend fun delayReasons(): Result<List<ChangeReason>> = catchingWaivera {
        RequestMapping.reasons(apiCall { api.complianceReasons() }, forDelay = true)
    }

    override suspend fun closureReasons(): Result<List<ChangeReason>> = catchingWaivera {
        RequestMapping.closureReasons(apiCall { api.closureReasons() })
    }

    override suspend fun files(taskId: Int): Result<List<TaskFile>> = catchingWaivera {
        RequestMapping.files(apiCall { api.attachments(taskId) })
    }

    override suspend fun askMoreTime(
        taskId: Int,
        newDue: LocalDate,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> = catchingWaivera {
        val result = apiCall {
            api.askMoreTime(
                taskId,
                TaskExtensionRequest(
                    // The web sends the picked day at UTC midnight; the server keeps it as a UTC date.
                    requestedDueDate = newDue.atStartOfDay(ZoneOffset.UTC).toInstant().toString(),
                    reasonCodeId = reasonId,
                    explanation = explanation.trim(),
                    evidenceAttachmentId = evidenceId,
                ),
            )
        }
        outcome(result.status)
    }

    override suspend fun reportDelay(
        taskId: Int,
        period: DelayPeriod,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome> = catchingWaivera {
        val result = apiCall {
            api.reportDelay(
                taskId,
                TaskDelayRequest(
                    reasonCodeId = reasonId,
                    explanation = explanation.trim(),
                    delayStartDate = period.start.toString(),
                    delayEndDate = period.end.toString(),
                    evidenceAttachmentId = evidenceId,
                ),
            )
        }
        if (!result.success) throw WaiveraError.Unexpected(result.statusCode)
        outcome(result.data?.status)
    }

    override suspend fun askClosure(taskId: Int, reasonId: Long, comment: String): Result<Unit> = catchingWaivera {
        val me = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
        val body = apiCall {
            api.askClosure(
                taskId,
                TaskClosureRequest(
                    requesterId = me,
                    taskId = taskId,
                    transactionReasonId = reasonId.toInt(),
                    // As the web's closure modal: the reason goes by id, the text is the comment.
                    transactionReasonText = comment.trim(),
                ),
            )
        }
        if (!succeeded(body)) throw WaiveraError.Unexpected()
    }

    private fun clear() {
        _access.value = RequestAccess()
        _waiting.value = null
        deciding.value = emptySet()
    }

    private fun outcome(status: String?): RequestOutcome =
        if (status.equals("Approved", ignoreCase = true)) RequestOutcome.Approved else RequestOutcome.Pending

    /** The envelope can say no inside a 200; its own message is never shown. */
    private companion object {
        const val FULL_DAY = "FULL_DAY"
        const val PARTIAL_DAY = "PARTIAL_DAY"
    }

    private fun succeeded(body: JsonElement): Boolean {
        if (body !is JsonObject || !body.containsKey("success")) return true
        return runCatching { json.decodeFromJsonElement(ApiResponse.serializer(JsonElement.serializer()), body) }
            .getOrNull()?.success != false
    }
}
