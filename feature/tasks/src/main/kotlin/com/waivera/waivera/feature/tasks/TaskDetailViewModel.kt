package com.waivera.waivera.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.waiveraErrorOrNull
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

enum class DetailPhase { Loading, Error, Content }

/** A section that loads on its own, so the task shows while its activity is still coming. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data object Failed : Loadable<Nothing>

    data class Ready<T>(val value: T) : Loadable<T>
}

@Immutable
data class ActivityRow(
    val id: Int,
    val type: String,
    val minutes: Int,
    val moment: TaskFormat.Moment?,
    val byMe: Boolean,
    val author: String?,
    val description: String?,
    /** Just logged here, so it lands with the arrival motion. */
    val isNew: Boolean = false,
)

@Immutable
data class TaskDetailUiState(
    val phase: DetailPhase = DetailPhase.Loading,
    val task: TaskDetail? = null,
    val due: DueText = DueText.None,
    val dueDate: String? = null,
    val activity: Loadable<List<ActivityRow>> = Loadable.Loading,
    val log: LogForm? = null,
    @StringRes val errorMessage: Int? = null,
) {
    val canLog: Boolean get() = task != null && !task.isFinished
}

/**
 * The Log activity sheet. A type with a fixed description or a recommended time locks that
 * field, as on the web. Times are picked in the employee's saved zone and sent as a UTC instant.
 */
@Immutable
data class LogForm(
    val types: Loadable<List<ActivityType>> = Loadable.Loading,
    val typeId: Int? = null,
    val minutes: Int = DEFAULT_MINUTES,
    val completedAt: Instant,
    val description: String = "",
    val zone: ZoneId,
    val whenText: TaskFormat.Moment,
    /** Today in the saved zone; later days can't be picked. */
    val today: LocalDate? = null,
    val isSaving: Boolean = false,
    val showErrors: Boolean = false,
    val failed: Boolean = false,
    /** The picked time is after now. */
    val inFuture: Boolean = false,
) {
    val type: ActivityType? get() = (types as? Loadable.Ready)?.value?.firstOrNull { it.id == typeId }
    val minutesLocked: Boolean get() = type?.recommendedMinutes != null
    val descriptionLocked: Boolean get() = type?.fixedDescription != null
    val typeMissing: Boolean get() = typeId == null
    val descriptionMissing: Boolean get() = description.isBlank()
    val isValid: Boolean get() = !typeMissing && !descriptionMissing && !inFuture && minutes > 0

    companion object {
        const val DEFAULT_MINUTES = 15
        const val STEP_MINUTES = 15
        const val MAX_MINUTES = 24 * 60
    }
}

sealed interface TaskDetailEffect {
    data object Logged : TaskDetailEffect

    data class OpenWeb(val url: String) : TaskDetailEffect

    data object WebUnavailable : TaskDetailEffect
}

@HiltViewModel(assistedFactory = TaskDetailViewModel.Factory::class)
@Suppress("TooManyFunctions") // One small handler per Log activity field.
class TaskDetailViewModel @AssistedInject constructor(
    @Assisted private val taskId: Int,
    private val tasks: TaskRepository,
    private val session: SessionRepository,
    private val config: NetworkConfig,
    calendar: CalendarRepository,
    private val clock: Clock,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val effectChannel = Channel<TaskDetailEffect>(Channel.BUFFERED)
    val effects: Flow<TaskDetailEffect> = effectChannel.receiveAsFlow()
    private val zone = calendar.zone.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WaiveraTime.FallbackZone)

    val uiState: StateFlow<TaskDetailUiState> = combine(local, zone) { l, zone ->
        val today = clock.instant().atZone(zone).toLocalDate()
        val me = session.state.value.currentUserId
        TaskDetailUiState(
            phase = l.phase,
            task = l.task,
            due = TaskFormat.due(l.task?.due, today),
            dueDate = l.task?.due?.let { TaskFormat.date(it, today) },
            activity = when (val a = l.activity) {
                Loadable.Loading -> Loadable.Loading
                Loadable.Failed -> Loadable.Failed
                is Loadable.Ready -> Loadable.Ready(a.value.map { activityRow(it, zone, today, me, it.id in l.newIds) })
            },
            log = l.log?.let { form ->
                form.copy(
                    zone = zone,
                    whenText = TaskFormat.moment(form.completedAt, zone, today),
                    today = today,
                    inFuture = form.completedAt.isAfter(clock.instant()),
                )
            },
            errorMessage = l.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TaskDetailUiState())

    init {
        load()
    }

    fun load() {
        local.update { it.copy(phase = if (it.task == null) DetailPhase.Loading else it.phase) }
        viewModelScope.launch {
            val result = tasks.task(taskId)
            local.update { current ->
                result.fold(
                    onSuccess = { current.copy(phase = DetailPhase.Content, task = it, errorMessage = null) },
                    onFailure = {
                        if (current.task != null) {
                            current
                        } else {
                            current.copy(
                                phase = DetailPhase.Error,
                                errorMessage = result.waiveraErrorOrNull()?.messageRes,
                            )
                        }
                    },
                )
            }
        }
        loadActivity()
    }

    fun loadActivity() {
        viewModelScope.launch {
            val result = tasks.activity(taskId)
            local.update { current ->
                result.fold(
                    onSuccess = { current.copy(activity = Loadable.Ready(it)) },
                    onFailure = {
                        if (current.activity is Loadable.Ready) current else current.copy(activity = Loadable.Failed)
                    },
                )
            }
        }
    }

    fun openOnWeb() {
        val path = local.value.task?.webPath ?: return
        val base = config.webBase
        val url = base?.resolve(path)?.takeIf { it.host == base.host && it.scheme == base.scheme }
        viewModelScope.launch {
            effectChannel.send(url?.let { TaskDetailEffect.OpenWeb(it.toString()) } ?: TaskDetailEffect.WebUnavailable)
        }
    }

    fun startLog() {
        if (!uiState.value.canLog) return
        val now = clock.instant().truncatedTo(ChronoUnit.MINUTES)
        local.update {
            it.copy(
                log = LogForm(
                    completedAt = now,
                    zone = zone.value,
                    whenText = TaskFormat.moment(now, zone.value, clock.instant().atZone(zone.value).toLocalDate()),
                ),
            )
        }
        loadTypes()
    }

    fun loadTypes() {
        updateLog { it.copy(types = Loadable.Loading) }
        viewModelScope.launch {
            val result = tasks.activityTypes()
            updateLog { form -> form.copy(types = result.fold({ Loadable.Ready(it) }, { Loadable.Failed })) }
        }
    }

    fun dismissLog() {
        if (local.value.log?.isSaving == true) return
        local.update { it.copy(log = null) }
    }

    fun chooseType(id: Int) = updateLog { form ->
        val type = (form.types as? Loadable.Ready)?.value?.firstOrNull { it.id == id } ?: return@updateLog form
        form.copy(
            typeId = id,
            minutes = type.recommendedMinutes ?: if (form.minutesLocked) LogForm.DEFAULT_MINUTES else form.minutes,
            description = type.fixedDescription ?: if (form.descriptionLocked) "" else form.description,
            failed = false,
        )
    }

    fun stepMinutes(up: Boolean) = updateLog { form ->
        if (form.minutesLocked) return@updateLog form
        val next = form.minutes + if (up) LogForm.STEP_MINUTES else -LogForm.STEP_MINUTES
        form.copy(minutes = next.coerceIn(LogForm.STEP_MINUTES, LogForm.MAX_MINUTES))
    }

    fun setDescription(text: String) = updateLog { form ->
        if (form.descriptionLocked) form else form.copy(description = text.take(MAX_DESCRIPTION), failed = false)
    }

    /** [date] and [minuteOfDay] are wall-clock values in the employee's saved zone. */
    fun setWhen(date: LocalDate, minuteOfDay: Int) = updateLog { form ->
        val local = date.atStartOfDay().plusMinutes(minuteOfDay.toLong())
        form.copy(completedAt = local.atZone(zone.value).toInstant())
    }

    fun submitLog() {
        val form = uiState.value.log ?: return
        if (form.isSaving) return
        if (!form.isValid) {
            updateLog { it.copy(showErrors = true) }
            return
        }
        val typeId = form.typeId ?: return
        updateLog { it.copy(isSaving = true, failed = false) }
        viewModelScope.launch {
            val result = tasks.logActivity(
                NewActivity(
                    taskId = taskId,
                    typeId = typeId,
                    minutes = form.minutes,
                    completedAt = form.completedAt,
                    description = form.description,
                ),
            )
            if (result.isSuccess) {
                val before = (local.value.activity as? Loadable.Ready)?.value?.map(TaskActivity::id)?.toSet().orEmpty()
                local.update { it.copy(log = null) }
                effectChannel.send(TaskDetailEffect.Logged)
                val refreshed = tasks.activity(taskId)
                refreshed.onSuccess { items ->
                    local.update { current ->
                        current.copy(
                            activity = Loadable.Ready(items),
                            newIds =
                            items.map(TaskActivity::id).toSet() - before,
                        )
                    }
                }
            } else {
                updateLog { it.copy(isSaving = false, failed = true) }
            }
        }
    }

    private fun updateLog(transform: (LogForm) -> LogForm) = local.update { l ->
        l.log?.let { l.copy(log = transform(it)) }
            ?: l
    }

    private fun activityRow(item: TaskActivity, zone: ZoneId, today: LocalDate, me: String?, isNew: Boolean) =
        ActivityRow(
            id = item.id,
            type = item.type,
            minutes = item.minutes,
            moment = item.at?.let { TaskFormat.moment(it, zone, today) },
            byMe = me != null && item.authorId == me,
            author = item.authorName,
            description = item.description,
            isNew = isNew,
        )

    private data class Local(
        val phase: DetailPhase = DetailPhase.Loading,
        val task: TaskDetail? = null,
        val activity: Loadable<List<TaskActivity>> = Loadable.Loading,
        val newIds: Set<Int> = emptySet(),
        val log: LogForm? = null,
        @StringRes val errorMessage: Int? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(taskId: Int): TaskDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_DESCRIPTION = 4_000
    }
}
