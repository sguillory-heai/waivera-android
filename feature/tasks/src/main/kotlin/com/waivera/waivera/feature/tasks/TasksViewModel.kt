package com.waivera.waivera.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskPage
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskSlice
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

/**
 * One due group as the list shows it: the first [rows] of the server's [total] for that group.
 * [more] is the control under it, or null when the whole group fits in the first rows.
 */
@Immutable
data class TaskSection(
    val group: DueGroup,
    val rows: List<TaskRow>,
    val total: Int = rows.size,
    val more: SectionMore? = null,
)

/** The control under a capped section. */
@Immutable
sealed interface SectionMore {
    /** [hidden] tasks are not shown yet; a tap shows up to [TasksViewModel.SECTION_STEP] more. */
    data class ShowMore(val hidden: Int, val loading: Boolean = false, val failed: Boolean = false) : SectionMore

    /** Everything is open; a tap folds the section back to its first rows. */
    data object ShowFewer : SectionMore
}

@Immutable
data class TasksUiState(
    val segment: TasksSegment = TasksSegment.Casework,
    val window: TaskWindow = TaskWindow.DueSoon,
    val assignedToMe: Boolean = false,
    val delays: DelayFilter = DelayFilter.Show,
    val query: String = "",
    val sections: List<TaskSection> = emptyList(),
    /** Matches on the server for this list and search, including rows not shown yet. */
    val totalCount: Int = 0,
    val counts: TaskCounts? = null,
    val phase: TasksPhase = TasksPhase.Loading,
    val isRefreshing: Boolean = false,
    /** A refresh failed while the last good list stays on screen. */
    val refreshFailed: Boolean = false,
    @StringRes val errorMessage: Int? = null,
    val showsRequests: Boolean = false,
    /** Requests waiting on this person, for the count in the Requests segment. */
    val waitingCount: Int = 0,
) {
    val rowCount: Int get() = sections.sumOf { it.rows.size }

    val segments: List<TasksSegment>
        get() = if (showsRequests) TasksSegment.entries else listOf(TasksSegment.Casework, TasksSegment.MyWork)

    /** Delays are a client-task rule on the server and the web, so the chip is casework only. */
    val showsDelays: Boolean get() = segment == TasksSegment.Casework
}

/**
 * The Tasks tab. Casework is the web's client task list, scoped by the server to the person's
 * role (their caseload, or the whole agency for owners); My work is their own employee tasks.
 * Each due group is its own server list, so its count is the real total and opening more of it
 * never depends on scrolling past the others. Changing the segment, window, chip or search starts
 * over with the first rows of each group.
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
    private val moreJobs = mutableMapOf<DueGroup, Job>()
    private var countsJob: Job? = null

    val uiState: StateFlow<TasksUiState> = combine(local, zone, requests.access, requests.waiting) {
            l,
            zone,
            access,
            waiting,
        ->
        val today = clock.instant().atZone(zone).toLocalDate()
        val me = session.state.value.currentUserId
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
            sections = distinctRows(l.sections.filter { it.total > 0 }.map { it.section(today, me) }),
            totalCount = l.sections.sumOf { it.total },
            counts = l.counts,
            phase = l.phase,
            isRefreshing = l.isRefreshing,
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
        // Sections are cut by date in the saved zone, so a zone that changes after a load reloads them.
        viewModelScope.launch {
            zone.drop(1).collect { if (local.value.loadedScope != null) reload(showSkeleton = false) }
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

    /** Shows up to [SECTION_STEP] more of [group], from rows already loaded or the next pages. */
    fun showMore(group: DueGroup) {
        val section = local.value.sections.firstOrNull { it.group == group } ?: return
        if (moreJobs[group]?.isActive == true || listJob?.isActive == true) return
        val target = (section.shown + SECTION_STEP).coerceAtMost(section.total)
        if (target <= section.items.size) {
            updateSection(group) { it.copy(shown = target, failed = false) }
            return
        }
        updateSection(group) { it.copy(loading = true, failed = false) }
        val request = local.value.request
        moreJobs[group] = viewModelScope.launch {
            // One fresh read from the top, so a task closed on the web since the last page can't
            // shift the offsets and leave a row that no later page brings back.
            val size = (target + SECTION_FIRST - 1) / SECTION_FIRST * SECTION_FIRST
            val page = tasks.tasks(request.copy(slice = section.slice), 1, size)
            if (local.value.request != request) return@launch
            updateSection(group) { current ->
                val result = page.getOrNull() ?: return@updateSection current.copy(loading = false, failed = true)
                val items = result.items.distinctBy(TaskSummary::id)
                // A short page means the server has nothing past it, whatever its count says.
                val total = if (result.items.size < size) items.size else result.totalCount.coerceAtLeast(items.size)
                current.copy(
                    items = items,
                    total = total,
                    shown = target.coerceAtMost(items.size).coerceAtLeast(SECTION_FIRST),
                    loading = false,
                )
            }
        }
    }

    /** Folds [group] back to its first rows; the rows stay loaded for the next Show more. */
    fun showFewer(group: DueGroup) = updateSection(group) { it.copy(shown = SECTION_FIRST, failed = false) }

    private fun updateSection(group: DueGroup, change: (SectionLoad) -> SectionLoad) = local.update { l ->
        l.copy(sections = l.sections.map { if (it.group == group) change(it) else it })
    }

    private fun reload(showSkeleton: Boolean) {
        listJob?.cancel()
        moreJobs.values.forEach(Job::cancel)
        moreJobs.clear()
        countsJob?.cancel()
        val request = local.value.request
        val today = clock.instant().atZone(zone.value).toLocalDate()
        // A refresh keeps how far each section was open; a new list starts every section closed.
        val previous = if (showSkeleton) emptyMap() else local.value.sections.associate { it.group to it.shown }
        val plan = sectionPlan(request, today)
        local.update {
            it.copy(
                phase = if (showSkeleton || it.phase == TasksPhase.Error) TasksPhase.Loading else it.phase,
                // Show more jobs are cancelled, so no section may keep waiting on one.
                sections = if (showSkeleton) emptyList() else it.sections.map { s -> s.copy(loading = false) },
                counts = if (showSkeleton) null else it.counts,
                isRefreshing = !showSkeleton,
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
            val results = plan.map { (group, slice) ->
                val shown = previous[group] ?: SECTION_FIRST
                // Whole pages of SECTION_FIRST, as open as the section was before.
                val size = (shown + SECTION_FIRST - 1) / SECTION_FIRST * SECTION_FIRST
                async { Triple(group, slice, tasks.tasks(request.copy(slice = slice), page = 1, pageSize = size)) }
            }.awaitAll()
            local.update { current ->
                if (current.request != request) current else current.withResults(results, previous)
            }
        }
    }

    /** The list after a reload: a section that failed keeps what it showed before; the others update. */
    private fun Local.withResults(
        results: List<Triple<DueGroup, TaskSlice, Result<TaskPage>>>,
        previous: Map<DueGroup, Int>,
    ): Local {
        val current = this
        val failure = results.firstOrNull { it.third.isFailure }?.third
        // A section that failed keeps what it showed before; the others still update.
        val sections = results.mapNotNull { (group, slice, result) ->
            if (result.isSuccess) {
                loaded(group, slice, result, previous[group])
            } else {
                // Today's dates, so a later Show more on the kept rows asks for the right days.
                current.sections.firstOrNull { it.group == group }?.copy(slice = slice)
            }
        }
        val phase = when {
            sections.any { it.total > 0 } -> TasksPhase.Content
            failure != null -> TasksPhase.Error
            else -> TasksPhase.Empty
        }
        return current.copy(
            sections = sections,
            phase = phase,
            isRefreshing = false,
            refreshFailed = failure != null && phase == TasksPhase.Content,
            errorMessage = failure?.waiveraErrorOrNull()?.messageRes,
        )
    }

    /** A section's first answer; [shown] is how far it was open before a refresh. */
    private fun loaded(group: DueGroup, slice: TaskSlice, result: Result<TaskPage>, shown: Int?): SectionLoad {
        val page = result.getOrThrow()
        val items = page.items.distinctBy(TaskSummary::id)
        return SectionLoad(
            group = group,
            slice = slice,
            items = items,
            total = page.totalCount.coerceAtLeast(items.size),
            // Never below the first rows, so a section that was empty still asks for a page.
            shown = (shown ?: SECTION_FIRST).coerceAtMost(items.size).coerceAtLeast(SECTION_FIRST),
        )
    }

    private data class Local(
        val segment: TasksSegment = TasksSegment.Casework,
        val window: TaskWindow = TaskWindow.DueSoon,
        val assignedToMe: Boolean = false,
        val delays: DelayFilter = DelayFilter.Show,
        val delaysPicked: Boolean = false,
        val query: String = "",
        val sections: List<SectionLoad> = emptyList(),
        val counts: TaskCounts? = null,
        val phase: TasksPhase = TasksPhase.Loading,
        val isRefreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        @StringRes val errorMessage: Int? = null,
        /** The list scope [sections] belong to, so leaving Requests keeps a list that still fits. */
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
    }

    /** One due group's server list: the rows loaded so far, how many show, and its total. */
    private data class SectionLoad(
        val group: DueGroup,
        val slice: TaskSlice,
        val items: List<TaskSummary> = emptyList(),
        val total: Int = 0,
        val shown: Int = SECTION_FIRST,
        val loading: Boolean = false,
        val failed: Boolean = false,
    ) {
        fun section(today: LocalDate, me: String?): TaskSection {
            val visible = items.take(shown)
            val hidden = total - visible.size
            return TaskSection(
                group = group,
                rows = visible.map { taskRow(it, today, me) },
                total = total,
                more = when {
                    hidden > 0 -> SectionMore.ShowMore(hidden, loading = loading, failed = failed)
                    total > SECTION_FIRST -> SectionMore.ShowFewer
                    else -> null
                },
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val SEARCH_DEBOUNCE_MILLIS = 300L

        /** Rows each section opens with (Sheldon, 2026-11-05). */
        const val SECTION_FIRST = 5

        /** Rows each Show more adds. */
        const val SECTION_STEP = 10

        /**
         * The sections a list asks the server for, in display order, with the same week edges as
         * [group]. Delayed casework is one section of its own; every other section leaves it out.
         */
        internal fun sectionPlan(
            query: TaskQuery,
            today: LocalDate,
            locale: Locale = Locale.getDefault(),
        ): List<Pair<DueGroup, TaskSlice>> {
            val casework = query.scope == TaskScope.Casework
            val delayed = casework && query.delays != DelayFilter.Hide
            val others = !(casework && query.delays == DelayFilter.Only)
            val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
            val nextWeek = weekStart.plusWeeks(1)
            val past = query.window != TaskWindow.DueSoon
            val ahead = query.window != TaskWindow.Overdue
            return buildList {
                if (others && past) add(DueGroup.Overdue to TaskSlice(dueTo = today.minusDays(1)))
                if (delayed) add(DueGroup.Delayed to TaskSlice(delayed = true))
                if (others && ahead) {
                    add(DueGroup.ThisWeek to TaskSlice(dueFrom = today, dueTo = nextWeek.minusDays(1)))
                    add(DueGroup.NextWeek to TaskSlice(dueFrom = nextWeek, dueTo = nextWeek.plusDays(DAYS_IN_WEEK - 1)))
                    add(DueGroup.Later to TaskSlice(dueFrom = nextWeek.plusWeeks(1)))
                }
            }
        }

        private const val DAYS_IN_WEEK = 7L

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

/**
 * A task in two sections at once (the server moved it between two section loads) shows only in
 * the first, as the list keys rows by task id.
 */
private fun distinctRows(sections: List<TaskSection>): List<TaskSection> {
    val seen = mutableSetOf<Int>()
    return sections.map { section -> section.copy(rows = section.rows.filter { seen.add(it.id) }) }
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
