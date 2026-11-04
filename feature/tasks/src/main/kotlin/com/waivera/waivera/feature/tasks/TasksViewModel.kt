package com.waivera.waivera.feature.tasks

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.network.error.waiveraErrorOrNull
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TasksPhase { Loading, Error, Empty, Content }

/** One task card, already worded for the person's day. */
@Immutable
data class TaskRow(
    val id: Int,
    val key: String,
    val title: String,
    val about: String?,
    val due: DueText,
    val state: TaskState,
)

@Immutable
data class TasksUiState(
    val window: TaskWindow = TaskWindow.DueSoon,
    val query: String = "",
    val rows: List<TaskRow> = emptyList(),
    /** Matches on the server for this window and search, including pages not loaded yet. */
    val totalCount: Int = 0,
    val counts: TaskCounts? = null,
    val phase: TasksPhase = TasksPhase.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** A refresh failed while the last good list stays on screen. */
    val refreshFailed: Boolean = false,
    @StringRes val errorMessage: Int? = null,
) {
    val canLoadMore: Boolean get() = phase == TasksPhase.Content && rows.size < totalCount
}

/**
 * The Tasks tab: the person's own open tasks, from the server. Changing the window or the
 * search starts over at page one; scrolling near the end loads the next page.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: TaskRepository,
    calendar: CalendarRepository,
    private val clock: Clock,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val zone = calendar.zone.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, WaiveraTime.FallbackZone)
    private var loadJob: Job? = null

    val uiState: StateFlow<TasksUiState> = combine(local, zone) { l, zone ->
        val today = clock.instant().atZone(zone).toLocalDate()
        TasksUiState(
            window = l.window,
            query = l.query,
            rows = l.items.map { row(it, today) },
            totalCount = l.totalCount,
            counts = l.counts,
            phase = l.phase,
            isRefreshing = l.isRefreshing,
            isLoadingMore = l.isLoadingMore,
            refreshFailed = l.refreshFailed,
            errorMessage = l.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TasksUiState())

    init {
        reload(showSkeleton = true)
        viewModelScope.launch {
            local.map { it.query }.distinctUntilChanged().drop(1).debounce(SEARCH_DEBOUNCE_MILLIS).collect {
                reload(showSkeleton = true)
            }
        }
    }

    fun setWindow(window: TaskWindow) {
        if (window == local.value.window) return
        local.update { it.copy(window = window) }
        reload(showSkeleton = true)
    }

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun refresh() = reload(showSkeleton = false)

    fun loadMore() {
        val l = local.value
        if (l.isLoadingMore ||
            l.phase != TasksPhase.Content ||
            l.items.size >= l.totalCount ||
            loadJob?.isActive == true
        ) {
            return
        }
        local.update { it.copy(isLoadingMore = true) }
        val request = l.request
        loadJob = viewModelScope.launch {
            val next = tasks.myTasks(request.window, request.query, l.page + 1)
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
                    onFailure = { current.copy(isLoadingMore = false) },
                )
            }
        }
    }

    private fun reload(showSkeleton: Boolean) {
        loadJob?.cancel()
        val request = local.value.request
        local.update {
            it.copy(
                phase = if (showSkeleton || it.phase == TasksPhase.Error) TasksPhase.Loading else it.phase,
                items = if (showSkeleton) emptyList() else it.items,
                isRefreshing = !showSkeleton,
                isLoadingMore = false,
                refreshFailed = false,
            )
        }
        loadJob = viewModelScope.launch {
            launch { tasks.counts().onSuccess { counts -> local.update { it.copy(counts = counts) } } }
            val result = tasks.myTasks(request.window, request.query, page = 1)
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
                    onFailure = { error ->
                        val hasList = current.phase == TasksPhase.Content || current.phase == TasksPhase.Empty
                        current.copy(
                            phase = if (hasList && current.items.isNotEmpty()) TasksPhase.Content else TasksPhase.Error,
                            isRefreshing = false,
                            refreshFailed = hasList && current.items.isNotEmpty(),
                            errorMessage = result.waiveraErrorOrNull()?.messageRes,
                        )
                    },
                )
            }
        }
    }

    private fun row(task: TaskSummary, today: LocalDate) = TaskRow(
        id = task.id,
        key = task.key,
        title = task.title,
        about = task.about,
        due = TaskFormat.due(task.due, today),
        state = task.state,
    )

    private data class Request(val window: TaskWindow, val query: String)

    private data class Local(
        val window: TaskWindow = TaskWindow.DueSoon,
        val query: String = "",
        val items: List<TaskSummary> = emptyList(),
        val totalCount: Int = 0,
        val page: Int = 1,
        val counts: TaskCounts? = null,
        val phase: TasksPhase = TasksPhase.Loading,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val refreshFailed: Boolean = false,
        @StringRes val errorMessage: Int? = null,
    ) {
        val request: Request get() = Request(window, query.trim())
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
