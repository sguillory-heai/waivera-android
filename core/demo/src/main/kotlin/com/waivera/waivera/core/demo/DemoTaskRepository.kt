@file:Suppress("MagicNumber")

package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskPage
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Sample tasks for demo mode and screenshots; logged activity is kept in memory. */
@Singleton
class DemoTaskRepository @Inject constructor(private val backend: DemoBackend) : TaskRepository {
    private val logged = MutableStateFlow<Map<Int, List<TaskActivity>>>(emptyMap())
    private var nextActivityId = 90_000

    private val today: LocalDate get() = backend.now().atZone(DemoData.ZONE).toLocalDate()

    override suspend fun myTasks(window: TaskWindow, search: String, page: Int): Result<TaskPage> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val query = search.trim().lowercase()
        val items = DemoTasks.all(today)
            .filter { task ->
                val due = task.due ?: return@filter window == TaskWindow.AllOpen
                when (window) {
                    TaskWindow.DueSoon -> !due.isBefore(today) && !due.isAfter(today.plusMonths(1))
                    TaskWindow.Overdue -> due.isBefore(today)
                    TaskWindow.AllOpen -> true
                }
            }
            .filter {
                query.isEmpty() ||
                    it.title.lowercase().contains(query) ||
                    it.key.lowercase().contains(query) ||
                    it.about.orEmpty().lowercase().contains(query)
            }
            .map { DemoTasks.summary(it) }
        return Result.success(TaskPage(if (page == 1) items else emptyList(), items.size))
    }

    override suspend fun counts(): Result<TaskCounts> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val all = DemoTasks.all(today)
        return Result.success(TaskCounts(all.size, all.count { it.due?.isBefore(today) == true }))
    }

    override suspend fun task(id: Int): Result<TaskDetail> {
        backend.failRefreshes?.let { return Result.failure(it) }
        return DemoTasks.all(today).firstOrNull { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(WaiveraError.NotFound())
    }

    override suspend fun activity(taskId: Int): Result<List<TaskActivity>> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val now = backend.now()
        val earlier = listOf(
            TaskActivity(1, "Phone call", 20, now.minusSeconds(DAY * 2), "demo-priya", "Priya Raman", null),
            TaskActivity(2, "Documentation", 30, now.minusSeconds(DAY * 6), DemoData.ME, "You", null),
        )
        return Result.success(logged.value[taskId].orEmpty() + earlier)
    }

    override suspend fun activityTypes(): Result<List<ActivityType>> = Result.success(
        listOf(
            ActivityType(11, "Documentation", null, null),
            ActivityType(12, "Home visit", null, null),
            ActivityType(13, "Phone call", null, null),
            ActivityType(14, "Monthly contact", 15, "Monthly contact completed with the participant."),
        ),
    )

    override suspend fun logActivity(activity: NewActivity): Result<Unit> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val type = activityTypes().getOrThrow().firstOrNull { it.id == activity.typeId }?.name.orEmpty()
        val row = TaskActivity(
            id = nextActivityId++,
            type = type,
            minutes = activity.minutes,
            at = activity.completedAt,
            authorId = backend.myUserId,
            authorName = "You",
            description = activity.description,
        )
        logged.update { it + (activity.taskId to (listOf(row) + it[activity.taskId].orEmpty())) }
        return Result.success(Unit)
    }

    private companion object {
        const val DAY = 86_400L
    }
}

/** The demo person's open tasks, due relative to the demo day so the windows always have rows. */
object DemoTasks {
    fun all(today: LocalDate): List<TaskDetail> = listOf(
        task(991, "HEA-991", "Quarterly monitoring visit", today.minusDays(2), TaskState.InProgress, "Jordan Ellis"),
        task(1042, "HEA-1042", "Annual plan review", today.plusDays(5), TaskState.NotStarted, "Maya Thompson"),
        task(1017, "HEA-1017", "Update emergency contacts", today.plusDays(8), TaskState.InProgress, "Jordan Ellis"),
        task(987, "HEA-987", "Service authorization follow-up", today.plusDays(11), TaskState.InReview, "Luis Ortega"),
        task(
            1101,
            "HEA-1101",
            "Collect signed consent form",
            today.plusDays(45),
            TaskState.NotStarted,
            "Maya Thompson",
        ),
    )

    fun summary(task: TaskDetail) = TaskSummary(
        id = task.id,
        key = task.key,
        title = task.title,
        due = task.due,
        state = task.state,
        about = task.about,
    )

    @Suppress("LongParameterList")
    private fun task(id: Int, key: String, title: String, due: LocalDate, state: TaskState, about: String) = TaskDetail(
        id = id,
        key = key,
        title = title,
        description = "Review the current plan with the participant and note any changes in services.",
        state = state,
        type = "Plan review",
        start = due.minusDays(30),
        due = due,
        assignedBy = "Priya Raman",
        about = about,
        webPath = "/task/$key",
    )
}
