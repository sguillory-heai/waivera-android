package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskPage
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred

/** A scripted task server: results per call, an optional gate to hold loads, and call records. */
internal class FakeTasks : TaskRepository {
    var all: List<TaskSummary> = emptyList()

    /** The server's day in the employee's zone (Chicago on the default test clock). */
    var today: LocalDate = LocalDate.of(2026, 11, 1)
    var failList: WaiveraError? = null

    /** Fails only the sections it matches, as one slow or broken request among several. */
    var failWhen: ((TaskQuery) -> Boolean)? = null
    var listGate: CompletableDeferred<Unit>? = null
    val listCalls = mutableListOf<Triple<TaskWindow, String, Int>>()
    val pageSizes = mutableListOf<Int>()
    val queries = mutableListOf<TaskQuery>()
    val countCalls = mutableListOf<Pair<TaskScope, Boolean>>()

    var detail: Result<TaskDetail> = Result.success(task())
    var activity: Result<List<TaskActivity>> = Result.success(emptyList())
    var types: Result<List<ActivityType>> = Result.success(listOf(ActivityType(1, "Home visit", null, null)))
    var logResult: Result<Unit> = Result.success(Unit)
    val logged = mutableListOf<NewActivity>()
    var attachments: Result<List<TaskAttachment>> = Result.success(emptyList())

    override suspend fun tasks(query: TaskQuery, page: Int, pageSize: Int): Result<TaskPage> {
        val search = query.search
        queries += query
        listCalls += Triple(query.window, search, page)
        pageSizes += pageSize
        listGate?.await()
        val failure = failList ?: WaiveraError.Network().takeIf { failWhen?.invoke(query) == true }
        if (failure != null) return Result.failure(failure)
        val matches = all
            .filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
            .filter { inSlice(it, query) }
        return Result.success(TaskPage(matches.drop((page - 1) * pageSize).take(pageSize), matches.size))
    }

    /** The server's cut for a section; an undated task (never sent by the server) reads as later. */
    private fun inSlice(task: TaskSummary, query: TaskQuery): Boolean {
        val slice = query.slice ?: return true
        if (query.scope == TaskScope.Casework && slice.delayed != task.delayed) return false
        if (slice.delayed) return true
        val due = task.due ?: return slice.dueTo == null
        return (slice.dueFrom?.let { !due.isBefore(it) } ?: true) &&
            (slice.dueTo?.let { !due.isAfter(it) } ?: true)
    }

    override suspend fun counts(scope: TaskScope, assignedToMe: Boolean): Result<TaskCounts> {
        countCalls += scope to assignedToMe
        return Result.success(TaskCounts(all.size, 0))
    }

    override suspend fun task(id: Int): Result<TaskDetail> = detail

    override suspend fun activity(taskId: Int): Result<List<TaskActivity>> = activity

    override suspend fun activityTypes(): Result<List<ActivityType>> = types

    override suspend fun attachments(taskId: Int): Result<List<TaskAttachment>> = attachments

    override suspend fun logActivity(activity: NewActivity): Result<Unit> {
        logged += activity
        return logResult
    }

    companion object {
        fun summary(id: Int, title: String, due: LocalDate? = null) =
            TaskSummary(id, "HEA-$id", title, due, TaskState.NotStarted, null)

        fun task(state: TaskState = TaskState.InProgress, due: LocalDate? = null) = TaskDetail(
            id = 7,
            key = "HEA-7",
            title = "Annual plan review",
            description = null,
            state = state,
            type = null,
            start = null,
            due = due,
            assignedBy = null,
            about = null,
            webPath = "/task/HEA-7",
        )
    }
}
