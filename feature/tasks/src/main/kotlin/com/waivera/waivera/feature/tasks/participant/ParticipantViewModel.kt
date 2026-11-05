package com.waivera.waivera.feature.tasks.participant

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.ParticipantProfile
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ParticipantRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.waiveraErrorOrNull
import com.waivera.waivera.core.network.time.WaiveraTime
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.TaskRow
import com.waivera.waivera.feature.tasks.taskRow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

enum class ParticipantPhase { Loading, Content, Error, Forbidden }

/** How the plan (CPOC) year stands today, in the employee's saved zone. */
sealed interface PlanStatus {
    data class EndsIn(val days: Int) : PlanStatus

    data object EndsToday : PlanStatus

    data class EndedAgo(val days: Int) : PlanStatus
}

@Immutable
data class ParticipantUiState(
    val phase: ParticipantPhase = ParticipantPhase.Loading,
    val profile: ParticipantProfile? = null,
    val plan: PlanStatus? = null,
    val tasks: Loadable<List<TaskRow>> = Loadable.Loading,
    val overdue: Int = 0,
    val today: LocalDate = LocalDate.MIN,
)

sealed interface ParticipantEffect {
    data class OpenWeb(val url: String) : ParticipantEffect

    data object WebUnavailable : ParticipantEffect
}

/**
 * The read-only participant profile (web Participant Profile tab) and their open tasks. Nothing
 * here edits: changes and the rest of the record stay on the web.
 */
@HiltViewModel(assistedFactory = ParticipantViewModel.Factory::class)
class ParticipantViewModel @AssistedInject constructor(
    @Assisted private val participantId: Int,
    private val participants: ParticipantRepository,
    private val session: SessionRepository,
    private val config: NetworkConfig,
    calendar: CalendarRepository,
    private val clock: Clock,
) : ViewModel() {
    private data class Local(
        val phase: ParticipantPhase = ParticipantPhase.Loading,
        val profile: ParticipantProfile? = null,
        val tasks: Loadable<List<TaskSummary>> = Loadable.Loading,
    )

    private val local = MutableStateFlow(Local())
    private val effectChannel = Channel<ParticipantEffect>(Channel.BUFFERED)
    val effects: Flow<ParticipantEffect> = effectChannel.receiveAsFlow()
    private val zone = calendar.zone.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WaiveraTime.FallbackZone)

    val uiState: StateFlow<ParticipantUiState> = combine(local, zone) { l, zone ->
        val today = clock.instant().atZone(zone).toLocalDate()
        val me = session.state.value.currentUserId
        val rows = (l.tasks as? Loadable.Ready)?.value?.map { taskRow(it, today, me) }
        ParticipantUiState(
            phase = l.phase,
            profile = l.profile,
            plan = l.profile?.planEnd?.let { planStatus(it, today) },
            tasks = rows?.let { Loadable.Ready(it) } ?: (l.tasks as? Loadable.Failed ?: Loadable.Loading),
            overdue = rows.orEmpty().count { it.daysLate != null },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ParticipantUiState())

    private var profileJob: Job? = null
    private var tasksJob: Job? = null

    init {
        load()
    }

    fun load() {
        local.update { it.copy(phase = if (it.profile == null) ParticipantPhase.Loading else it.phase) }
        // A retry replaces the load in flight, so an older answer never lands over a newer one.
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            val result = participants.profile(participantId)
            local.update { current ->
                result.fold(
                    onSuccess = { current.copy(phase = ParticipantPhase.Content, profile = it) },
                    onFailure = {
                        // A refresh that fails keeps what is on screen.
                        if (current.profile != null) {
                            current
                        } else {
                            val forbidden = result.waiveraErrorOrNull() is WaiveraError.Forbidden
                            current.copy(phase = if (forbidden) ParticipantPhase.Forbidden else ParticipantPhase.Error)
                        }
                    },
                )
            }
            if (result.isSuccess) loadTasks()
        }
    }

    fun loadTasks() {
        local.update { it.copy(tasks = if (it.tasks is Loadable.Ready) it.tasks else Loadable.Loading) }
        tasksJob?.cancel()
        tasksJob = viewModelScope.launch {
            val result = participants.openTasks(participantId)
            local.update { current ->
                current.copy(
                    tasks = result.fold(
                        onSuccess = { Loadable.Ready(it) },
                        onFailure = { current.tasks as? Loadable.Ready ?: Loadable.Failed },
                    ),
                )
            }
        }
    }

    fun openOnWeb() {
        val base = config.webBase
        val url = base?.resolve("/participant/$participantId/participant-snapshot")
            ?.takeIf { it.host == base.host && it.scheme == base.scheme }
        viewModelScope.launch {
            effectChannel.send(
                url?.let {
                    ParticipantEffect.OpenWeb(it.toString())
                } ?: ParticipantEffect.WebUnavailable,
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(participantId: Int): ParticipantViewModel
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Days are counted in the saved zone's calendar, so a DST change never makes a day 23 or 25 hours. */
        internal fun planStatus(end: LocalDate, today: LocalDate): PlanStatus {
            val days = ChronoUnit.DAYS.between(today, end).toInt()
            return when {
                days > 0 -> PlanStatus.EndsIn(days)
                days == 0 -> PlanStatus.EndsToday
                else -> PlanStatus.EndedAgo(-days)
            }
        }
    }
}
