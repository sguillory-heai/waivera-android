package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.TaskActivity
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
    var pageSize = 2
    var failList: WaiveraError? = null
    var listGate: CompletableDeferred<Unit>? = null
    val listCalls = mutableListOf<Triple<TaskWindow, String, Int>>()
    val queries = mutableListOf<TaskQuery>()
    val countCalls = mutableListOf<Pair<TaskScope, Boolean>>()

    var detail: Result<TaskDetail> = Result.success(task())
    var activity: Result<List<TaskActivity>> = Result.success(emptyList())
    var types: Result<List<ActivityType>> = Result.success(listOf(ActivityType(1, "Home visit", null, null)))
    var logResult: Result<Unit> = Result.success(Unit)
    val logged = mutableListOf<NewActivity>()

    override suspend fun tasks(query: TaskQuery, page: Int): Result<TaskPage> {
        val search = query.search
        queries += query
        listCalls += Triple(query.window, search, page)
        listGate?.await()
        failList?.let { return Result.failure(it) }
        val matches = all.filter { search.isBlank() || it.title.contains(search, ignoreCase = true) }
        return Result.success(TaskPage(matches.drop((page - 1) * pageSize).take(pageSize), matches.size))
    }

    override suspend fun counts(scope: TaskScope, assignedToMe: Boolean): Result<TaskCounts> {
        countCalls += scope to assignedToMe
        return Result.success(TaskCounts(all.size, 0))
    }

    override suspend fun task(id: Int): Result<TaskDetail> = detail

    override suspend fun activity(taskId: Int): Result<List<TaskActivity>> = activity

    override suspend fun activityTypes(): Result<List<ActivityType>> = types

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
