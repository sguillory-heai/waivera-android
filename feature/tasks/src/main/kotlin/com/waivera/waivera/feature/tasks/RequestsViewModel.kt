package com.waivera.waivera.feature.tasks

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
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

    /** Time off, profile changes and the rest, which the web reviews. */
    data object Other : RequestChange
}

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
)

sealed interface RequestsEffect {
    data class Decided(val approve: Boolean, val requester: String) : RequestsEffect

    /** The decision did not go through; the request is back in the list. */
    data class DecideFailed(val approve: Boolean) : RequestsEffect

    /** Someone else decided it first; it stays gone. */
    data object AlreadyDecided : RequestsEffect

    data class OpenWeb(val url: String) : RequestsEffect

    data object WebUnavailable : RequestsEffect
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
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val effectChannel = Channel<RequestsEffect>(Channel.BUFFERED)
    val effects: Flow<RequestsEffect> = effectChannel.receiveAsFlow()
    private val zone = calendar.zone.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WaiveraTime.FallbackZone)

    val uiState: StateFlow<RequestsUiState> = combine(local, requests.waiting, zone) { l, waiting, zone ->
        val today = clock.instant().atZone(zone).toLocalDate()
        val items = waiting?.map { item(it, zone, today) }
        RequestsUiState(
            waiting = when {
                items != null -> Loadable.Ready(items)
                l.waitingFailed -> Loadable.Failed
                else -> Loadable.Loading
            },
            mine = when (val mine = l.mine) {
                Loadable.Loading -> Loadable.Loading
                Loadable.Failed -> Loadable.Failed
                is Loadable.Ready -> Loadable.Ready(mine.value.map { item(it, zone, today) })
            },
            // A request decided elsewhere closes its sheet instead of offering a stale review.
            reviewing = l.reviewingId?.let { id -> items?.firstOrNull { it.id == id } },
            confirming = l.confirming?.let { (id, approve) ->
                items?.firstOrNull { it.id == id }?.let { Decision(it, approve) }
            },
            isRefreshing = l.isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestsUiState())

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
        local.update { it.copy(reviewingId = item.id) }
    }

    fun closeReview() = local.update { it.copy(reviewingId = null, confirming = null) }

    fun ask(approve: Boolean) {
        val id = local.value.reviewingId ?: return
        local.update { it.copy(confirming = id to approve) }
    }

    fun cancelConfirm() = local.update { it.copy(confirming = null) }

    /** Sends the confirmed decision. Both sheets close at once; the list already shows the result. */
    fun confirm() {
        val decision = uiState.value.confirming ?: return
        local.update { it.copy(reviewingId = null, confirming = null) }
        viewModelScope.launch {
            val result = requests.decide(decision.item.request, decision.approve)
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

    private fun load(refreshing: Boolean) {
        local.update { it.copy(isRefreshing = refreshing, waitingFailed = false) }
        viewModelScope.launch {
            val waiting = requests.refreshWaiting()
            local.update { it.copy(waitingFailed = waiting.isFailure) }
            loadMine()
            local.update { it.copy(isRefreshing = false) }
        }
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

    private fun item(request: ChangeRequest, zone: ZoneId, today: LocalDate) = RequestItem(
        request = request,
        asked = request.requestedAt?.let { TaskFormat.moment(it, zone, today) },
        change = change(request, today),
        due = TaskFormat.due(request.currentDue, today),
    )

    private fun change(request: ChangeRequest, today: LocalDate): RequestChange = when (request.kind) {
        RequestKind.MoreTime -> request.requestedDue?.let { to ->
            RequestChange.DueMove(
                from = request.currentDue?.let { TaskFormat.date(it, today) },
                to = TaskFormat.date(to, today),
                daysLater = request.currentDue?.let { ChronoUnit.DAYS.between(it, to).toInt() }?.takeIf { it > 0 },
            )
        } ?: RequestChange.Other
        RequestKind.Delay -> {
            val start = request.delayStart
            val end = request.delayEnd
            if (start != null && end != null) {
                RequestChange.DelayWindow(TaskFormat.date(start, today), TaskFormat.date(end, today))
            } else {
                RequestChange.Other
            }
        }
        RequestKind.Closure -> RequestChange.Closure
        RequestKind.Other -> RequestChange.Other
    }

    private data class Local(
        val waitingFailed: Boolean = false,
        val mine: Loadable<List<ChangeRequest>> = Loadable.Loading,
        val reviewingId: Int? = null,
        val confirming: Pair<Int, Boolean>? = null,
        val isRefreshing: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val WEB_REQUESTS_PATH = "/admin-requests"
    }
}
