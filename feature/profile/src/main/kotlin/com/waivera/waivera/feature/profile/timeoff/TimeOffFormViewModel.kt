package com.waivera.waivera.feature.profile.timeoff

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveSendException
import com.waivera.waivera.core.data.model.LeaveSendProblem
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.data.model.TimeOffRequest
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something the form itself can see is wrong, worded like sprout_web's `localSelectionIssue`. */
enum class LeaveIssue { EndBeforeStart, TooShort, TooLong }

/** The server's look at the dates: hours, balance and anything that blocks them. */
@Immutable
sealed interface LeaveCheck {
    /** Not enough filled in yet, or the form already shows an issue. */
    data object Idle : LeaveCheck

    data object Checking : LeaveCheck

    data class Ready(val preview: LeavePreview) : LeaveCheck

    data object Failed : LeaveCheck
}

@Immutable
sealed interface LeaveTypes {
    data object Loading : LeaveTypes

    data object Failed : LeaveTypes

    data class Ready(val types: List<LeaveType>) : LeaveTypes
}

/**
 * The Request time off form. It follows sprout_web `AdminRequestModal`: the same fields, the same
 * local checks, the server's preview 250 ms after a change, and the same request on send.
 */
@Immutable
data class TimeOffFormState(
    val types: LeaveTypes = LeaveTypes.Loading,
    val typeId: Int? = null,
    val partialDay: Boolean = false,
    val firstDay: LocalDate? = null,
    val lastDay: LocalDate? = null,
    val day: LocalDate? = null,
    val from: LocalTime? = null,
    val to: LocalTime? = null,
    val note: String = "",
    val issue: LeaveIssue? = null,
    val check: LeaveCheck = LeaveCheck.Idle,
    /** The first day is before today in the person's zone: allowed, with the web's note. */
    val past: Boolean = false,
    val sending: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
) {
    val type: LeaveType? get() = (types as? LeaveTypes.Ready)?.types?.firstOrNull { it.id == typeId }

    private val preview: LeavePreview? get() = (check as? LeaveCheck.Ready)?.preview

    /** A balance-keeping type that this request would take below zero; the server refuses it. */
    val overBalance: Boolean
        get() = preview?.let { it.tracksBalance && (it.afterMinutes ?: 0) < 0 } == true

    val canSend: Boolean
        get() = !sending && issue == null && preview?.block == null && preview != null && !overBalance
}

sealed interface TimeOffFormEffect {
    /** Sent: the profile shows [request] as pending at once. */
    data class Sent(val request: TimeOffRequest) : TimeOffFormEffect

    data class SendFailed(val problem: LeaveSendProblem?) : TimeOffFormEffect
}

@Suppress("TooManyFunctions") // One small handler per form field.
@HiltViewModel
class TimeOffFormViewModel @Inject constructor(
    private val requests: RequestRepository,
    calendar: CalendarRepository,
    private val clock: Clock,
) : ViewModel() {
    private val zone: StateFlow<ZoneId> = calendar.zone.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        WaiveraTime.FallbackZone,
    )
    private val form = MutableStateFlow(TimeOffFormState(today = today()))
    val state: StateFlow<TimeOffFormState> = form.asStateFlow()

    private val effectChannel = Channel<TimeOffFormEffect>(Channel.BUFFERED)
    val effects: Flow<TimeOffFormEffect> = effectChannel.receiveAsFlow()
    private var checking: Job? = null

    init {
        loadTypes()
    }

    fun loadTypes() {
        form.update { it.copy(types = LeaveTypes.Loading) }
        viewModelScope.launch {
            val types = requests.leaveTypes()
            form.update { current ->
                current.copy(types = types.fold({ LeaveTypes.Ready(it) }, { LeaveTypes.Failed }))
            }
        }
    }

    fun pickType(id: Int) = change { it.copy(typeId = id) }

    fun setPartialDay(partial: Boolean) = change { it.copy(partialDay = partial) }

    fun pickFirstDay(day: LocalDate) = change { current ->
        // A first day after the last moves the last day with it, so the range never reads backwards.
        val last = current.lastDay?.takeUnless { it.isBefore(day) } ?: day
        current.copy(firstDay = day, lastDay = last)
    }

    fun pickLastDay(day: LocalDate) = change { it.copy(lastDay = day) }

    fun pickDay(day: LocalDate) = change { it.copy(day = day) }

    fun pickFrom(time: LocalTime) = change { it.copy(from = time) }

    fun pickTo(time: LocalTime) = change { it.copy(to = time) }

    fun setNote(text: String) = form.update { it.copy(note = text.take(NOTE_MAX_LENGTH)) }

    fun retryCheck() = change { it }

    fun send() {
        val current = form.value
        if (!current.canSend) return
        val draft = TimeOffDrafts.draft(current, zone.value) ?: return
        form.update { it.copy(sending = true) }
        viewModelScope.launch {
            val result = requests.requestTimeOff(draft)
            form.update { it.copy(sending = false) }
            effectChannel.send(
                result.fold(
                    onSuccess = { TimeOffFormEffect.Sent(TimeOffDrafts.pending(current)) },
                    onFailure = { TimeOffFormEffect.SendFailed((it as? LeaveSendException)?.problem) },
                ),
            )
        }
    }

    /** Applies [edit], re-runs the local checks, then asks the server again after a short pause. */
    private fun change(edit: (TimeOffFormState) -> TimeOffFormState) {
        val today = today()
        form.update { current ->
            val next = edit(current)
            next.copy(
                issue = TimeOffDrafts.issue(next, zone.value),
                past = TimeOffDrafts.past(next, today),
                today = today,
                check = LeaveCheck.Idle,
            )
        }
        checking?.cancel()
        val current = form.value
        val draft = TimeOffDrafts.draft(current, zone.value)?.takeIf { current.issue == null } ?: return
        form.update { it.copy(check = LeaveCheck.Checking) }
        checking = viewModelScope.launch {
            delay(CHECK_DELAY_MILLIS)
            val preview = requests.previewLeave(draft)
            form.update { it.copy(check = preview.fold({ p -> LeaveCheck.Ready(p) }, { LeaveCheck.Failed })) }
        }
    }

    private fun today(): LocalDate = clock.instant().atZone(zone.value).toLocalDate()

    companion object {
        const val NOTE_MAX_LENGTH = 1_000
        const val MIN_PARTIAL_MINUTES = 30L
        const val MAX_PARTIAL_MINUTES = 480L
        private const val CHECK_DELAY_MILLIS = 250L

        /** Stands in until the profile reloads the real request. */
        const val PENDING_ID = -1L
    }
}
