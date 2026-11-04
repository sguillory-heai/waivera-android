package com.waivera.waivera.feature.tasks

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.ProfileField
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.TimeOffReview
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.data.model.TrainingDecision
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.feature.tasks.files.TaskFileExport
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a request changes, already worded for the person's day. */
@Immutable
sealed interface RequestChange {
    /** More time: the due date moves from [from] to [to], [daysLater] days later. */
    data class DueMove(val from: String?, val to: String, val daysLater: Int?) : RequestChange

    /** A delay recorded for a server-owned period; the due date stays. */
    data class DelayWindow(val start: String, val end: String) : RequestChange

    /** Closure: the task closes without being completed. */
    data object Closure : RequestChange

    /** Time off asked for, or handed back by a cancellation. */
    data class TimeOff(
        val leaveType: String,
        /** First and last day; [TaskFormat.Span.end] is null for one day. */
        val dates: TaskFormat.Span,
        /** From and to clock times for a partial day, in the agency's leave zone. */
        val times: TaskFormat.Span?,
        val minutes: Int,
        val cancellation: Boolean,
    ) : RequestChange

    /** A participant's new details, changed fields first. */
    data class Profile(val changed: List<ProfileRow>, val unchanged: List<ProfileRow>) : RequestChange

    /** A closed task opens again. */
    data object Reopen : RequestChange

    /** A required file stops being required. */
    data object RemoveFile : RequestChange

    /** External training credit; its details load when the review opens. */
    data object Training : RequestChange

    /** A kind newer than this app, reviewed on the web. */
    data object Other : RequestChange
}

/** One compared profile field, with dates already worded. */
@Immutable
data class ProfileRow(val field: ProfileField.Field, val previous: String?, val requested: String?)

/** The requester's balance around this time off, in minutes; [after] may go below zero. */
@Immutable
data class BalanceChange(val before: Int, val after: Int)

@Immutable
data class RequestItem(
    val request: ChangeRequest,
    /** When it was asked, in the employee's saved zone. */
    val asked: TaskFormat.Moment?,
    val change: RequestChange,
    /** Today's due text for the task in question, when the request carries its current due date. */
    val due: DueText,
) {
    val id: Int get() = request.id
}

/** A pending approve or reject, confirmed on its own sheet before it is sent. */
@Immutable
data class Decision(val item: RequestItem, val approve: Boolean)

@Immutable
data class RequestsUiState(
    val waiting: Loadable<List<RequestItem>> = Loadable.Loading,
    val mine: Loadable<List<RequestItem>> = Loadable.Loading,
    val reviewing: RequestItem? = null,
    val confirming: Decision? = null,
    val isRefreshing: Boolean = false,
    /** Time off under review: the balance, or null when this reviewer may not see balances. */
    val balance: Loadable<BalanceChange?> = Loadable.Loading,
    /** Training credit under review. */
    val training: Loadable<TrainingCredit> = Loadable.Loading,
    /** What the reviewer writes on a training decision. */
    val reason: String = "",
    /** The training certificate is downloading; another tap waits for it. */
    val openingCertificate: Boolean = false,
)

const val REASON_MAX_LENGTH = 2_000

sealed interface RequestsEffect {
    data class Decided(val approve: Boolean, val requester: String) : RequestsEffect

    /** The decision did not go through; the request is back in the list. */
    data class DecideFailed(val approve: Boolean) : RequestsEffect

    /** Someone else decided it first; it stays gone. */
    data object AlreadyDecided : RequestsEffect

    data class OpenWeb(val url: String) : RequestsEffect

    data object WebUnavailable : RequestsEffect

    /** The certificate is on the phone; open it in the person's PDF app. */
    data class OpenCertificate(val uri: String) : RequestsEffect

    data object CertificateFailed : RequestsEffect
}

/**
 * The Requests segment: requests waiting on this person, then their own. Approve and reject are
 * optimistic: the card leaves at once and comes back, with a note, if the server says no.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // One small handler per control on the Requests segment.
class RequestsViewModel @Inject constructor(
    private val requests: RequestRepository,
    private val config: NetworkConfig,
    calendar: CalendarRepository,
    private val clock: Clock,
    private val files: TaskFileRepository,
    private val export: TaskFileExport,
) : ViewModel() {
    private val local = MutableStateFlow(Local())

    /** Typed apart from [local], so a keystroke doesn't reword both request lists. */
    private val reason = MutableStateFlow("")
    private var certificate: Job? = null
    private var leaveZoneAsked = false
    private val effectChannel = Channel<RequestsEffect>(Channel.BUFFERED)
    val effects: Flow<RequestsEffect> = effectChannel.receiveAsFlow()
    private var details: Job? = null
    private val zone = calendar.zone.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WaiveraTime.FallbackZone)

    private val lists = combine(local, requests.waiting, zone) { l, waiting, zone ->
        val today = clock.instant().atZone(zone).toLocalDate()
        // Partial days are told in the agency's leave zone, as on the web; the person's own zone
        // stands in when the leave settings can't be read.
        val leaveZone = l.leaveZone ?: zone
        val items = waiting?.map { item(it, zone, leaveZone, today) }
        RequestsUiState(
            waiting = when {
                items != null -> Loadable.Ready(items)
                l.waitingFailed -> Loadable.Failed
                else -> Loadable.Loading
            },
            mine = when (val mine = l.mine) {
                Loadable.Loading -> Loadable.Loading
                Loadable.Failed -> Loadable.Failed
                is Loadable.Ready -> Loadable.Ready(mine.value.map { item(it, zone, leaveZone, today) })
            },
            // A request decided elsewhere closes its sheet instead of offering a stale review.
            reviewing = l.reviewingId?.let { id -> items?.firstOrNull { it.id == id } },
            confirming = l.confirming?.let { (id, approve) ->
                items?.firstOrNull { it.id == id }?.let { Decision(it, approve) }
            },
            isRefreshing = l.isRefreshing,
            balance = l.balance,
            training = l.training,
            openingCertificate = l.openingCertificate,
        )
    }

    val uiState: StateFlow<RequestsUiState> = combine(lists, reason) { state, reason -> state.copy(reason = reason) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestsUiState())

    init {
        load(refreshing = false)
    }

    fun refresh() = load(refreshing = true)

    fun retryWaiting() = load(refreshing = false)

    fun retryMine() {
        local.update { it.copy(mine = Loadable.Loading) }
        viewModelScope.launch { loadMine() }
    }

    fun review(item: RequestItem) {
        if (!item.request.kind.reviewableHere) {
            openWeb()
            return
        }
        reason.value = ""
        local.update { it.copy(reviewingId = item.id, balance = Loadable.Loading, training = Loadable.Loading) }
        details?.cancel()
        details = viewModelScope.launch { loadDetails(item.request) }
    }

    fun retryDetails() {
        val request = uiState.value.reviewing?.request ?: return
        local.update { it.copy(balance = Loadable.Loading, training = Loadable.Loading) }
        details?.cancel()
        details = viewModelScope.launch { loadDetails(request) }
    }

    fun closeReview() {
        details?.cancel()
        certificate?.cancel()
        reason.value = ""
        local.update { it.copy(reviewingId = null, confirming = null, openingCertificate = false) }
    }

    fun ask(approve: Boolean) {
        val id = local.value.reviewingId ?: return
        reason.value = ""
        local.update { it.copy(confirming = id to approve) }
    }

    fun cancelConfirm() = local.update { it.copy(confirming = null) }

    fun setReason(text: String) {
        reason.value = text.take(REASON_MAX_LENGTH)
    }

    /** Whether the confirm button can send: a training decision may need a written reason first. */
    fun canConfirm(state: RequestsUiState = uiState.value): Boolean {
        val decision = state.confirming ?: return false
        if (decision.item.request.kind != RequestKind.TrainingCredit) return true
        val credit = (state.training as? Loadable.Ready)?.value ?: return false
        val reasonNeeded = !decision.approve || credit.needsApproveReason
        return credit.decidable && (!reasonNeeded || state.reason.isNotBlank())
    }

    /** Downloads the certificate once and hands it to a PDF app under [fileName]; repeat taps wait for it. */
    fun openCertificate(fileName: String) {
        val request = uiState.value.reviewing?.request ?: return
        if (certificate?.isActive == true) return
        local.update { it.copy(openingCertificate = true) }
        certificate = viewModelScope.launch {
            try {
                val downloaded = files.certificate(request.id).getOrNull()
                val uri = downloaded?.let { file ->
                    export.shareableUri(file, fileName).also { files.close(file) }.getOrNull()
                }
                effectChannel.send(uri?.let(RequestsEffect::OpenCertificate) ?: RequestsEffect.CertificateFailed)
            } finally {
                local.update { it.copy(openingCertificate = false) }
            }
        }
    }

    /** Sends the confirmed decision. Both sheets close at once; the list already shows the result. */
    fun confirm() {
        val state = uiState.value
        val decision = state.confirming ?: return
        if (!canConfirm(state)) return
        val training = (state.training as? Loadable.Ready)?.value
            ?.takeIf { decision.item.request.kind == RequestKind.TrainingCredit }
            ?.let { credit -> credit.revision?.let { TrainingDecision(it, state.reason.trim()) } }
        reason.value = ""
        local.update { it.copy(reviewingId = null, confirming = null) }
        viewModelScope.launch {
            val result = requests.decide(decision.item.request, decision.approve, training)
            val effect = result.fold(
                onSuccess = { RequestsEffect.Decided(decision.approve, decision.item.request.requesterName) },
                onFailure = {
                    if (WaiveraError.from(it) is WaiveraError.NotFound) {
                        RequestsEffect.AlreadyDecided
                    } else {
                        RequestsEffect.DecideFailed(decision.approve)
                    }
                },
            )
            effectChannel.send(effect)
            if (result.isSuccess) loadMine()
        }
    }

    fun openWeb() {
        val base = config.webBase
        val url = base?.resolve(WEB_REQUESTS_PATH)?.takeIf { it.host == base.host && it.scheme == base.scheme }
        viewModelScope.launch {
            effectChannel.send(url?.let { RequestsEffect.OpenWeb(it.toString()) } ?: RequestsEffect.WebUnavailable)
        }
    }

    private suspend fun loadDetails(request: ChangeRequest) {
        when {
            request.kind == RequestKind.TrainingCredit -> {
                val credit = requests.trainingCredit(request.id)
                local.update { it.copy(training = credit.fold({ Loadable.Ready(it) }, { Loadable.Failed })) }
            }
            request.kind.isTimeOff -> {
                val timeOff = request.timeOff
                val employee = request.requesterId
                loadLeaveZone()
                if (timeOff == null || employee == null) {
                    local.update { it.copy(balance = Loadable.Ready(null)) }
                    return
                }
                val result = requests.leaveBalance(employee, timeOff.leaveTypeId)
                val change = result.map { balance ->
                    balance?.takeIf { it.tracksBalance }?.let {
                        val cancelling = request.kind == RequestKind.TimeOffCancellation
                        // sprout_web renderTimeOffDetails: a pending request already holds its minutes.
                        BalanceChange(
                            before = if (cancelling) it.availableMinutes else it.availableMinutes + timeOff.minutes,
                            after = if (cancelling) it.availableMinutes + timeOff.minutes else it.availableMinutes,
                        )
                    }
                }
                // A reviewer who may not see balances gets Ready(null); a failed load offers a retry.
                local.update { it.copy(balance = change.fold({ Loadable.Ready(it) }, { Loadable.Failed })) }
            }
            else -> Unit
        }
    }

    private fun load(refreshing: Boolean) {
        local.update { it.copy(isRefreshing = refreshing, waitingFailed = false) }
        viewModelScope.launch {
            val waiting = requests.refreshWaiting()
            local.update { it.copy(waitingFailed = waiting.isFailure) }
            loadMine()
            // Cards word partial days in the leave zone too, so it loads with the lists.
            val mine = (local.value.mine as? Loadable.Ready)?.value.orEmpty()
            if ((requests.waiting.value.orEmpty() + mine).any { it.timeOff?.partialDay == true }) loadLeaveZone()
            local.update { it.copy(isRefreshing = false) }
        }
    }

    /** Asked once: reviewers who aren't leave administrators can't read it, and the answer won't change. */
    private suspend fun loadLeaveZone() {
        if (leaveZoneAsked) return
        leaveZoneAsked = true
        requests.leaveZone()?.let { zone -> local.update { it.copy(leaveZone = zone) } }
    }

    private suspend fun loadMine() {
        val result = requests.mine()
        local.update { current ->
            result.fold(
                onSuccess = { current.copy(mine = Loadable.Ready(it)) },
                // A failed refresh keeps the last good list.
                onFailure = { if (current.mine is Loadable.Ready) current else current.copy(mine = Loadable.Failed) },
            )
        }
    }

    private fun item(request: ChangeRequest, zone: ZoneId, leaveZone: ZoneId, today: LocalDate) = RequestItem(
        request = request,
        asked = request.requestedAt?.let { TaskFormat.moment(it, zone, today) },
        change = change(request, leaveZone, today),
        due = TaskFormat.due(request.currentDue, today),
    )

    private fun change(request: ChangeRequest, leaveZone: ZoneId, today: LocalDate): RequestChange =
        when (request.kind) {
            RequestKind.MoreTime -> dueMove(request, today)
            RequestKind.Delay -> delayWindow(request, today)
            RequestKind.Closure -> RequestChange.Closure
            RequestKind.TimeOff, RequestKind.TimeOffCancellation ->
                request.timeOff?.let { timeOff(it, request.kind, leaveZone, today) } ?: RequestChange.Other
            RequestKind.ProfileChange -> profile(request)
            RequestKind.Reactivation -> RequestChange.Reopen
            RequestKind.AttachmentRemoval -> RequestChange.RemoveFile
            RequestKind.TrainingCredit -> RequestChange.Training
            RequestKind.Other -> RequestChange.Other
        }

    private fun dueMove(request: ChangeRequest, today: LocalDate): RequestChange {
        val to = request.requestedDue ?: return RequestChange.Other
        return RequestChange.DueMove(
            from = request.currentDue?.let { TaskFormat.date(it, today) },
            to = TaskFormat.date(to, today),
            daysLater = request.currentDue?.let { ChronoUnit.DAYS.between(it, to).toInt() }?.takeIf { it > 0 },
        )
    }

    private fun delayWindow(request: ChangeRequest, today: LocalDate): RequestChange {
        val start = request.delayStart ?: return RequestChange.Other
        val end = request.delayEnd ?: return RequestChange.Other
        return RequestChange.DelayWindow(TaskFormat.date(start, today), TaskFormat.date(end, today))
    }

    private fun profile(request: ChangeRequest): RequestChange {
        val rows = request.profile.map { field ->
            val format = { value: String? ->
                if (field.field in DateFields) {
                    value?.let { runCatching { TaskFormat.fullDate(LocalDate.parse(it)) }.getOrDefault(it) }
                } else {
                    value
                }
            }
            field.changed to ProfileRow(field.field, format(field.previous), format(field.requested))
        }
        return RequestChange.Profile(
            changed = rows.filter { it.first }.map { it.second },
            unchanged = rows.filterNot { it.first }.map { it.second },
        )
    }

    private fun timeOff(time: TimeOffReview, kind: RequestKind, zone: ZoneId, today: LocalDate): RequestChange {
        val start = time.start
        val end = time.end
        val partial = time.partialDay && start != null && end != null
        val startDay = if (partial) start.atZone(zone).toLocalDate() else time.startDate
        val endDay = if (partial) end.atZone(zone).toLocalDate() else time.endDate
        val dates = when {
            startDay == null -> null
            endDay == null || endDay == startDay -> TaskFormat.Span(TaskFormat.date(startDay, today), null)
            else -> TaskFormat.Span(TaskFormat.date(startDay, today), TaskFormat.date(endDay, today))
        } ?: return RequestChange.Other
        return RequestChange.TimeOff(
            leaveType = time.leaveType,
            dates = dates,
            times = if (partial) TaskFormat.Span(TaskFormat.clock(start, zone), TaskFormat.clock(end, zone)) else null,
            minutes = time.minutes,
            cancellation = kind == RequestKind.TimeOffCancellation,
        )
    }

    private data class Local(
        val waitingFailed: Boolean = false,
        val mine: Loadable<List<ChangeRequest>> = Loadable.Loading,
        val reviewingId: Int? = null,
        val confirming: Pair<Int, Boolean>? = null,
        val isRefreshing: Boolean = false,
        val balance: Loadable<BalanceChange?> = Loadable.Loading,
        val training: Loadable<TrainingCredit> = Loadable.Loading,
        val leaveZone: ZoneId? = null,
        val openingCertificate: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val WEB_REQUESTS_PATH = "/admin-requests"
        val DateFields = setOf(
            ProfileField.Field.DateOfBirth,
            ProfileField.Field.Certification,
            ProfileField.Field.CpocStart,
            ProfileField.Field.CpocEnd,
        )
    }
}
