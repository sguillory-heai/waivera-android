package com.waivera.waivera.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.network.error.waiveraErrorOrNull
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TasksPhase { Loading, Error, Empty, Content }

/** The Tasks tab's segments. Requests shows only to people the web shows Admin Requests to. */
enum class TasksSegment { Casework, MyWork, Requests }

/**
 * How the list groups cards, in display order. A task under an approved delay sits in [Delayed]
 * whatever its due date, so [Overdue] only holds work that is really late (the web's rule).
 */
enum class DueGroup { Overdue, Delayed, ThisWeek, NextWeek, Later, NoDue }

/** At most one badge per card: a closure request first, then the work state. A delay has its own line. */
enum class RowFlag { ClosureRequested, InProgress, InReview }

/** One task card, already worded for the person's day. */
@Immutable
data class TaskRow(
    val id: Int,
    val key: String,
    val title: String,
    val about: String?,
    val due: DueText,
    val state: TaskState,
    /** The date tile: month and day of the due date, or null without one. */
    val tileMonth: String? = null,
    val tileDay: String? = null,
    /** Whole days past due; null when not overdue or when a delay covers it. */
    val daysLate: Int? = null,
    /** An approved delay covers today: the card reads "Delayed through …" instead of late. */
    val delayed: Boolean = false,
    /** The delay's last day as the card prints it ("Nov 20"); null when the server sent none. */
    val delayedThrough: String? = null,
    /** Someone else owns it; null when it is the person's own. */
    val assignee: String? = null,
    val flag: RowFlag? = null,
    val group: DueGroup = DueGroup.NoDue,
)

@Immutable
data class TaskSection(val group: DueGroup, val rows: List<TaskRow>)

@Immutable
data class TasksUiState(
    val segment: TasksSegment = TasksSegment.Casework,
    val window: TaskWindow = TaskWindow.DueSoon,
    val assignedToMe: Boolean = false,
    val delays: DelayFilter = DelayFilter.Show,
    val query: String = "",
    val sections: List<TaskSection> = emptyList(),
    /** Matches on the server for this list and search, including pages not loaded yet. */
    val totalCount: Int = 0,
    val counts: TaskCounts? = null,
    val phase: TasksPhase = TasksPhase.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val pageFailed: Boolean = false,
    /** A refresh failed while the last good list stays on screen. */
    val refreshFailed: Boolean = false,
    @StringRes val errorMessage: Int? = null,
    val showsRequests: Boolean = false,
    /** Requests waiting on this person, for the count in the Requests segment. */
    val waitingCount: Int = 0,
) {
    val rowCount: Int get() = sections.sumOf { it.rows.size }

    /** False after a failed next page, until a refresh, so a bad page is not retried in a loop. */
    val canLoadMore: Boolean get() = phase == TasksPhase.Content && rowCount < totalCount && !pageFailed

    val segments: List<TasksSegment>
        get() = if (showsRequests) TasksSegment.entries else listOf(TasksSegment.Casework, TasksSegment.MyWork)

    /** Delays are a client-task rule on the server and the web, so the chip is casework only. */
    val showsDelays: Boolean get() = segment == TasksSegment.Casework
}

/**
 * The Tasks tab. Casework is the web's client task list, scoped by the server to the person's
 * role (their caseload, or the whole agency for owners); My work is their own employee tasks.
 * Changing the segment, window, chip or search starts over at page one.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
@Suppress("TooManyFunctions") // One small handler per control on the tab.
class TasksViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val requests: RequestRepository,
    private val session: SessionRepository,
    calendar: CalendarRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val zone = calendar.zone.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WaiveraTime.FallbackZone)
    private var listJob: Job? = null
    private var pageJob: Job? = null
    private var countsJob: Job? = null

    val uiState: StateFlow<TasksUiState> = combine(local, zone, requests.access, requests.waiting) {
            l,
            zone,
            access,
            waiting,
        ->
        val today = clock.instant().atZone(zone).toLocalDate()
        val me = session.state.value.currentUserId
        val rows = l.items.map { taskRow(it, today, me) }
        val segment = if (l.segment == TasksSegment.Requests && access.loaded && !access.showsRequests) {
            TasksSegment.Casework
        } else {
            l.segment
        }
        TasksUiState(
            segment = segment,
            window = l.window,
            assignedToMe = l.assignedToMe,
            delays = l.delays,
            query = l.query,
            sections = rows.groupBy { it.group }.toSortedMap().map { (group, items) -> TaskSection(group, items) },
            totalCount = l.totalCount,
            counts = l.counts,
            phase = l.phase,
            isRefreshing = l.isRefreshing,
            isLoadingMore = l.isLoadingMore,
            pageFailed = l.pageFailed,
            refreshFailed = l.refreshFailed,
            errorMessage = l.errorMessage,
            showsRequests = access.showsRequests,
            waitingCount = waiting?.count { it.kind.reviewableHere }.takeIf { access.showsRequests } ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TasksUiState())

    init {
        // The first page waits for the saved Delays choice, so a hidden-delays list never flashes them.
        viewModelScope.launch {
            val saved = settings.settings.map { it.taskDelays }.catch { emit(DelayFilter.Show) }.first()
            // A pick made before the saved one arrived wins; it already loaded its own list.
            if (!local.value.delaysPicked) {
                local.update { it.copy(delays = saved) }
                reload(showSkeleton = true)
            }
        }
        viewModelScope.launch {
            local.map { it.query }.distinctUntilChanged().drop(1).debounce(SEARCH_DEBOUNCE_MILLIS).collect {
                reload(showSkeleton = true)
            }
        }
    }

    fun setSegment(segment: TasksSegment) {
        val current = local.value.segment
        if (segment == current) return
        local.update { it.copy(segment = segment) }
        when {
            segment == TasksSegment.Requests -> viewModelScope.launch { requests.refreshWaiting() }
            // Casework and My work are different lists; Requests back to the same list keeps it.
            current == TasksSegment.Requests && local.value.scope == local.value.loadedScope -> Unit
            else -> reload(showSkeleton = true)
        }
    }

    fun setWindow(window: TaskWindow) {
        if (window == local.value.window) return
        local.update { it.copy(window = window) }
        reload(showSkeleton = true)
    }

    fun setAssignedToMe(on: Boolean) {
        if (on == local.value.assignedToMe) return
        local.update { it.copy(assignedToMe = on) }
        reload(showSkeleton = true)
    }

    fun setDelays(filter: DelayFilter) {
        if (filter == local.value.delays) return
        local.update { it.copy(delays = filter, delaysPicked = true) }
        reload(showSkeleton = true)
        viewModelScope.launch { runCatching { settings.setTaskDelays(filter) } }
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun refresh() {
        // A pull also re-checks what the person may see, so a role change on the web shows here.
        viewModelScope.launch { requests.refreshAccess() }
        reload(showSkeleton = false)
    }

    fun loadMore() {
        val l = local.value
        if (!l.canLoadMore || pageJob?.isActive == true || listJob?.isActive == true) return
        local.update { it.copy(isLoadingMore = true) }
        val request = l.request
        pageJob = viewModelScope.launch {
            val next = tasks.tasks(request, l.page + 1)
            local.update { current ->
                if (current.request != request) return@update current.copy(isLoadingMore = false)
                next.fold(
                    onSuccess = { page ->
                        val known = current.items.map(TaskSummary::id).toSet()
                        current.copy(
                            items = current.items + page.items.filter { it.id !in known },
                            totalCount = page.totalCount,
                            page = current.page + 1,
                            isLoadingMore = false,
                        )
                    },
                    // A failed next page keeps what is shown; the next scroll asks again.
                    onFailure = { current.copy(isLoadingMore = false, pageFailed = true) },
                )
            }
        }
    }

    private fun reload(showSkeleton: Boolean) {
        listJob?.cancel()
        pageJob?.cancel()
        countsJob?.cancel()
        val request = local.value.request
        local.update {
            it.copy(
                phase = if (showSkeleton || it.phase == TasksPhase.Error) TasksPhase.Loading else it.phase,
                items = if (showSkeleton) emptyList() else it.items,
                counts = if (showSkeleton) null else it.counts,
                isRefreshing = !showSkeleton,
                isLoadingMore = false,
                pageFailed = false,
                refreshFailed = false,
                loadedScope = request.scope,
            )
        }
        if (!showSkeleton) viewModelScope.launch { requests.refreshWaiting() }
        countsJob = viewModelScope.launch {
            tasks.counts(request.scope, request.assignedToMe).onSuccess { counts ->
                local.update { if (it.request.scope == request.scope) it.copy(counts = counts) else it }
            }
        }
        listJob = viewModelScope.launch {
            val result = tasks.tasks(request, page = 1)
            local.update { current ->
                if (current.request != request) return@update current
                result.fold(
                    onSuccess = { page ->
                        current.copy(
                            items = page.items,
                            totalCount = page.totalCount,
                            page = 1,
                            phase = if (page.items.isEmpty()) TasksPhase.Empty else TasksPhase.Content,
                            isRefreshing = false,
                            errorMessage = null,
                        )
                    },
                    onFailure = {
                        val keeps = current.items.isNotEmpty()
                        current.copy(
                            phase = if (keeps) TasksPhase.Content else TasksPhase.Error,
                            isRefreshing = false,
                            refreshFailed = keeps,
                            errorMessage = result.waiveraErrorOrNull()?.messageRes,
                        )
                    },
                )
            }
        }
    }

    private data class Local(
        val segment: TasksSegment = TasksSegment.Casework,
        val window: TaskWindow = TaskWindow.DueSoon,
        val assignedToMe: Boolean = false,
        val delays: DelayFilter = DelayFilter.Show,
        val delaysPicked: Boolean = false,
        val query: String = "",
        val items: List<TaskSummary> = emptyList(),
        val totalCount: Int = 0,
        val page: Int = 1,
        val counts: TaskCounts? = null,
        val phase: TasksPhase = TasksPhase.Loading,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val pageFailed: Boolean = false,
        val refreshFailed: Boolean = false,
        @StringRes val errorMessage: Int? = null,
        /** The list scope [items] belong to, so leaving Requests keeps a list that still fits. */
        val loadedScope: TaskScope? = null,
    ) {
        /** Requests keeps the last task list's scope underneath it. */
        val scope: TaskScope
            get() = when (segment) {
                TasksSegment.MyWork -> TaskScope.Mine
                TasksSegment.Requests -> loadedScope ?: TaskScope.Casework
                TasksSegment.Casework -> TaskScope.Casework
            }

        val request: TaskQuery
            get() = TaskQuery(
                scope = scope,
                window = window,
                search = query.trim(),
                assignedToMe = assignedToMe && scope == TaskScope.Casework,
                delays = if (scope == TaskScope.Casework) delays else DelayFilter.Show,
            )

        val canLoadMore: Boolean
            get() = !isLoadingMore && !pageFailed && phase == TasksPhase.Content && items.size < totalCount
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val SEARCH_DEBOUNCE_MILLIS = 300L

        /** Weeks start on the locale's first day, as the phone's own calendar does. */
        internal fun group(due: LocalDate?, today: LocalDate, locale: Locale = Locale.getDefault()): DueGroup {
            if (due == null) return DueGroup.NoDue
            if (due.isBefore(today)) return DueGroup.Overdue
            val first = WeekFields.of(locale).firstDayOfWeek
            val weekStart = today.with(TemporalAdjusters.previousOrSame(first))
            val nextWeek = weekStart.plusWeeks(1)
            return when {
                due.isBefore(nextWeek) -> DueGroup.ThisWeek
                due.isBefore(nextWeek.plusWeeks(1)) -> DueGroup.NextWeek
                else -> DueGroup.Later
            }
        }
    }
}

/** A task as its card shows it; [me] leaves the owner off the person's own tasks. */
internal fun taskRow(task: TaskSummary, today: LocalDate, me: String?): TaskRow {
    val due = task.due
    val late = due?.takeIf {
        !task.delayed && it.isBefore(today)
    }?.let { ChronoUnit.DAYS.between(it, today).toInt() }
    return TaskRow(
        id = task.id,
        key = task.key,
        title = task.title,
        about = task.about,
        due = TaskFormat.due(due, today),
        state = task.state,
        tileMonth = due?.let(TaskFormat::tileMonth),
        tileDay = due?.dayOfMonth?.toString(),
        daysLate = late,
        delayed = task.delayed,
        delayedThrough = task.delayedThrough?.let { TaskFormat.shortDate(it, today) },
        assignee = task.assigneeName?.takeIf { task.assigneeId == null || task.assigneeId != me },
        flag = when {
            task.pendingClosure -> RowFlag.ClosureRequested
            task.state == TaskState.InProgress -> RowFlag.InProgress
            task.state == TaskState.InReview -> RowFlag.InReview
            else -> null
        },
        group = if (task.delayed) DueGroup.Delayed else TasksViewModel.group(due, today),
    )
}
