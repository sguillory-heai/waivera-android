package com.waivera.waivera.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.ChangeOptions
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestOutcome
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.RequestRepository
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
    val moment: TaskFormat.Moment?,
    val byMe: Boolean,
    val author: String?,
    val description: String?,
    /** Just logged here, so it lands with the arrival motion. */
    val isNew: Boolean = false,
)

/** A task file as its row reads: "PDF · 180 KB · Nov 3 · Priya Raman". */
@Immutable
data class FileRow(
    val attachmentId: Int,
    val name: String,
    val kind: FileKind,
    val sizeBytes: Long,
    val added: String?,
    val addedBy: String?,
    /** False when it opens on the web: a type the phone can't show, or too large. */
    val viewable: Boolean,
)

@Immutable
data class TaskDetailUiState(
    val phase: DetailPhase = DetailPhase.Loading,
    val task: TaskDetail? = null,
    val due: DueText = DueText.None,
    val dueDate: String? = null,
    val activity: Loadable<List<ActivityRow>> = Loadable.Loading,
    val files: Loadable<List<FileRow>> = Loadable.Loading,
    val log: LogForm? = null,
    @StringRes val errorMessage: Int? = null,
    /** A change request waiting on review, from the server or just sent from here. */
    val pending: PendingBanner? = null,
    /** What the person may ask for now; empty hides Ask for a change. */
    val askChoices: List<AskKind> = emptyList(),
    val ask: AskForm? = null,
    /** Today in the saved zone. */
    val today: LocalDate = LocalDate.MIN,
    /** The approved delay covering today: the task is not really overdue while it lasts. */
    val delay: DelayNote? = null,
) {
    /** Past due with no delay covering it; the due date and status turn red only then. */
    val late: Boolean get() = due.overdue && delay == null

    val canLog: Boolean get() = task != null && !task.isFinished
    val canAsk: Boolean get() = task != null && !task.isFinished && pending == null && askChoices.isNotEmpty()
}

/** The active delay, already worded: [through] is "Nov 20", null when the server sent no end. */
@Immutable
data class DelayNote(val through: String?, val reason: String?, val explanation: String?)

/** The banner for a pending request, already worded for the person's day. */
@Immutable
data class PendingBanner(
    val kind: RequestKind,
    val newDue: String? = null,
    val delayStart: String? = null,
    val delayEnd: String? = null,
)

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

    /** A change was asked for; [inEffect] when the server applied it at once (an owner's own request). */
    data class Asked(val kind: AskKind, val inEffect: Boolean) : TaskDetailEffect

    data class OpenWeb(val url: String) : TaskDetailEffect

    data object WebUnavailable : TaskDetailEffect

    /** Opens the read-only viewer for a file on this task. */
    data class OpenFile(val taskId: Int, val attachmentId: Int) : TaskDetailEffect
}

@HiltViewModel(assistedFactory = TaskDetailViewModel.Factory::class)
@Suppress("TooManyFunctions", "LargeClass") // One small handler per Log activity and Ask for a change field.
class TaskDetailViewModel @AssistedInject constructor(
    @Assisted private val taskId: Int,
    private val tasks: TaskRepository,
    private val requests: RequestRepository,
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

    val uiState: StateFlow<TaskDetailUiState> = combine(local, zone, requests.access) { l, zone, access ->
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
            files = when (val f = l.files) {
                Loadable.Loading -> Loadable.Loading
                Loadable.Failed -> Loadable.Failed
                is Loadable.Ready -> Loadable.Ready(f.value.map { fileRow(it, zone, today) })
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
            pending = pendingBanner(l, today),
            delay = l.task?.delay?.let {
                DelayNote(it.through?.let { end -> TaskFormat.shortDate(end, today) }, it.reason, it.explanation)
            },
            askChoices = choices(l, access),
            ask = l.ask,
            today = today,
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
                    onSuccess = { task ->
                        // A request the server now shows replaces the one drawn here while it was sent.
                        current.copy(
                            phase = DetailPhase.Content,
                            task = task,
                            errorMessage = null,
                            optimistic = current.optimistic.takeIf { task.pendingRequest == null },
                        )
                    },
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
        loadFiles()
        loadOptions()
    }

    fun loadFiles() {
        viewModelScope.launch {
            val result = tasks.attachments(taskId)
            local.update { current ->
                result.fold(
                    onSuccess = { current.copy(files = Loadable.Ready(it)) },
                    onFailure = {
                        if (current.files is Loadable.Ready) current else current.copy(files = Loadable.Failed)
                    },
                )
            }
        }
    }

    /** The phone shows what it can draw; anything else opens the task on the web, where it can be downloaded. */
    fun openFile(attachmentId: Int) {
        val file =
            (local.value.files as? Loadable.Ready)?.value?.firstOrNull { it.attachmentId == attachmentId } ?: return
        if (file.viewable) {
            viewModelScope.launch { effectChannel.send(TaskDetailEffect.OpenFile(taskId, attachmentId)) }
        } else {
            openOnWeb()
        }
    }

    /** What may be asked for here, from the same role policies and eligibility check the web uses. */
    private fun loadOptions() {
        viewModelScope.launch {
            requests.ensureAccess()
            requests.options(taskId).onSuccess { options -> local.update { it.copy(options = options) } }
        }
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

    fun startAsk() {
        val state = uiState.value
        if (!state.canAsk) return
        val choices = state.askChoices
        local.update { it.copy(ask = AskForm(choices = choices)) }
        // One choice goes straight to its form.
        choices.singleOrNull()?.let(::chooseAsk)
    }

    fun chooseAsk(kind: AskKind) {
        val l = local.value
        val options = l.options
        updateAsk {
            it.copy(
                kind = kind,
                reasons = Loadable.Loading,
                reasonId = null,
                newDue = null,
                evidenceId = null,
                currentDue = options?.currentDue ?: l.task?.due,
                delay = options?.delay,
                showErrors = false,
                failed = false,
                delayChanged = false,
            )
        }
        loadAskReasons()
        if (kind != AskKind.Closure) loadAskFiles()
    }

    /** From a form back to the chooser, when there was a choice to make. */
    fun backToChoices(): Boolean {
        val form = local.value.ask ?: return false
        if (form.isSaving || form.kind == null || form.choices.size < 2) return false
        updateAsk { AskForm(choices = it.choices) }
        return true
    }

    fun dismissAsk() {
        if (local.value.ask?.isSaving == true) return
        local.update { it.copy(ask = null) }
    }

    fun loadAskReasons() {
        val kind = local.value.ask?.kind ?: return
        updateAsk { it.copy(reasons = Loadable.Loading) }
        viewModelScope.launch {
            val result = when (kind) {
                AskKind.MoreTime -> requests.moreTimeReasons()
                AskKind.Delay -> requests.delayReasons()
                AskKind.Closure -> requests.closureReasons()
            }
            updateAsk { form ->
                if (form.kind !=
                    kind
                ) {
                    form
                } else {
                    form.copy(reasons = result.fold({ Loadable.Ready(it) }, { Loadable.Failed }))
                }
            }
        }
    }

    fun loadAskFiles() {
        updateAsk { it.copy(files = Loadable.Loading) }
        viewModelScope.launch {
            val result = requests.files(taskId)
            updateAsk { it.copy(files = result.fold({ Loadable.Ready(it) }, { Loadable.Failed })) }
        }
    }

    fun setAskReason(id: Long) = updateAsk { form ->
        val reason = (form.reasons as? Loadable.Ready)?.value?.firstOrNull { it.id == id } ?: return@updateAsk form
        form.copy(reasonId = reason.id, evidenceId = form.evidenceId.takeIf { reason.requiresEvidence }, failed = false)
    }

    fun setAskDue(date: LocalDate) = updateAsk { it.copy(newDue = date, failed = false) }

    fun setAskExplanation(text: String) = updateAsk {
        // A little past the limit stays typed, so the count can say how much to remove.
        it.copy(explanation = text.take(AskForm.MAX_EXPLANATION + EXPLANATION_SLACK), failed = false)
    }

    fun setAskEvidence(id: Int?) = updateAsk { it.copy(evidenceId = id, failed = false) }

    /**
     * Sends the request. The pending banner shows at once and the sheet closes; if the server says
     * no, the banner goes and the sheet comes back with everything still filled in.
     */
    fun submitAsk() {
        val form = local.value.ask ?: return
        val kind = form.kind ?: return
        if (form.isSaving) return
        if (!form.isValid) {
            updateAsk { it.copy(showErrors = true) }
            return
        }
        val reasonId = form.reasonId ?: return
        local.update {
            it.copy(ask = null, optimistic = PendingDraft(kind, form.newDue, form.delay))
        }
        viewModelScope.launch {
            val result = send(kind, form, reasonId)
            result.fold(
                onSuccess = { outcome ->
                    local.update {
                        it.copy(
                            optimistic = it.optimistic.takeIf { outcome == RequestOutcome.Pending },
                            options = null,
                        )
                    }
                    effectChannel.send(TaskDetailEffect.Asked(kind, inEffect = outcome == RequestOutcome.Approved))
                    load()
                },
                onFailure = { error ->
                    val changed = error as? DelayChanged
                    local.update {
                        it.copy(
                            optimistic = null,
                            ask = form.copy(
                                failed = changed == null,
                                delayChanged = changed != null,
                                delay = if (changed != null) changed.period else form.delay,
                            ),
                        )
                    }
                },
            )
        }
    }

    private suspend fun send(kind: AskKind, form: AskForm, reasonId: Long): Result<RequestOutcome> {
        val evidence = form.evidenceId.takeIf { form.needsEvidence }
        return when (kind) {
            AskKind.MoreTime ->
                form.newDue
                    ?.let { requests.askMoreTime(taskId, it, reasonId, form.explanation, evidence) }
                    ?: Result.failure(IllegalStateException("A new due date is required"))
            AskKind.Delay -> sendDelay(form, reasonId, evidence)
            AskKind.Closure -> requests.askClosure(taskId, reasonId, form.explanation).map { RequestOutcome.Pending }
        }
    }

    // The period is the server's; check it again just before sending, as the web does.
    private suspend fun sendDelay(form: AskForm, reasonId: Long, evidence: Int?): Result<RequestOutcome> =
        requests.options(taskId).fold(
            onSuccess = { fresh ->
                local.update { it.copy(options = fresh) }
                val period = fresh.delay
                if (period != null && period == form.delay) {
                    requests.reportDelay(taskId, period, reasonId, form.explanation, evidence)
                } else {
                    Result.failure(DelayChanged(period))
                }
            },
            onFailure = { Result.failure(it) },
        )

    private fun updateAsk(transform: (AskForm) -> AskForm) = local.update { l ->
        l.ask?.let { l.copy(ask = transform(it)) }
            ?: l
    }

    private fun choices(l: Local, access: RequestAccess): List<AskKind> {
        val task = l.task
        // Nothing is offered until the server's check has answered, so a request already waiting
        // is never doubled while it loads or after it failed.
        val options = l.options
        if (task == null || options == null || options.hasPendingRequest) return emptyList()
        return buildList {
            if (task.isClientTask && access.canAskMoreTime && options.canAskMoreTime) add(AskKind.MoreTime)
            if (task.isClientTask && access.canAskMoreTime && options.delay != null) add(AskKind.Delay)
            if (access.canAskClosure) add(AskKind.Closure)
        }
    }

    private fun pendingBanner(l: Local, today: LocalDate): PendingBanner? {
        val draft = l.optimistic
        val request = l.task?.pendingRequest
        fun day(date: LocalDate?) = date?.let { TaskFormat.date(it, today) }
        return when {
            draft != null -> PendingBanner(
                kind = when (draft.kind) {
                    AskKind.MoreTime -> RequestKind.MoreTime
                    AskKind.Delay -> RequestKind.Delay
                    AskKind.Closure -> RequestKind.Closure
                },
                newDue = day(draft.newDue),
                delayStart = day(draft.delay?.start),
                delayEnd = day(draft.delay?.end),
            )
            request != null -> PendingBanner(
                kind = request.kind,
                newDue = day(request.requestedDue),
                delayStart = day(request.delayStart),
                delayEnd = day(request.delayEnd),
            )
            else -> null
        }
    }

    /** The delay period moved or closed while the sheet was open. */
    private class DelayChanged(val period: DelayPeriod?) : IllegalStateException("The delay period changed")

    private data class PendingDraft(val kind: AskKind, val newDue: LocalDate?, val delay: DelayPeriod?)

    private fun updateLog(transform: (LogForm) -> LogForm) = local.update { l ->
        l.log?.let { l.copy(log = transform(it)) }
            ?: l
    }

    private fun fileRow(file: TaskAttachment, zone: ZoneId, today: LocalDate) = FileRow(
        attachmentId = file.attachmentId,
        name = file.name,
        kind = file.kind,
        sizeBytes = file.sizeBytes,
        added = file.addedAt?.let { TaskFormat.shortDate(it.atZone(zone).toLocalDate(), today) },
        addedBy = file.addedBy,
        viewable = file.viewable,
    )

    private fun activityRow(item: TaskActivity, zone: ZoneId, today: LocalDate, me: String?, isNew: Boolean) =
        ActivityRow(
            id = item.id,
            type = item.type,
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
        val files: Loadable<List<TaskAttachment>> = Loadable.Loading,
        val newIds: Set<Int> = emptySet(),
        val log: LogForm? = null,
        @StringRes val errorMessage: Int? = null,
        val options: ChangeOptions? = null,
        val ask: AskForm? = null,
        /** Drawn as pending while the request is on its way, until the server's own copy shows. */
        val optimistic: PendingDraft? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(taskId: Int): TaskDetailViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_DESCRIPTION = 4_000
        const val EXPLANATION_SLACK = 200
    }
}
