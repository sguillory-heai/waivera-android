@file:Suppress("MagicNumber")

package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskDelay
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

    override suspend fun tasks(query: TaskQuery, page: Int): Result<TaskPage> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val window = query.window
        val search = query.search.trim().lowercase()
        val items = (if (query.scope == TaskScope.Casework) DemoTasks.casework(today) else DemoTasks.employee(today))
            .filter { !query.assignedToMe || it.assigneeName == null }
            .filter { matchesDelays(it, query) }
            .filter { task ->
                val due = task.due ?: return@filter window == TaskWindow.AllOpen
                when (window) {
                    TaskWindow.DueSoon -> !due.isBefore(today) && !due.isAfter(today.plusMonths(1))
                    TaskWindow.Overdue -> due.isBefore(today)
                    TaskWindow.AllOpen -> true
                }
            }
            .filter {
                search.isEmpty() ||
                    it.title.lowercase().contains(search) ||
                    it.key.lowercase().contains(search) ||
                    it.about.orEmpty().lowercase().contains(search)
            }
            .map { DemoTasks.summary(it) }
        return Result.success(TaskPage(if (page == 1) items else emptyList(), items.size))
    }

    /** The server applies the delay filter to client tasks only. */
    private fun matchesDelays(task: TaskDetail, query: TaskQuery): Boolean = when {
        query.scope != TaskScope.Casework -> true
        query.delays == DelayFilter.Hide -> task.delay == null
        query.delays == DelayFilter.Only -> task.delay != null
        else -> true
    }

    override suspend fun counts(scope: TaskScope, assignedToMe: Boolean): Result<TaskCounts> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val all = when {
            scope == TaskScope.Mine -> DemoTasks.employee(today)
            assignedToMe -> DemoTasks.casework(today).filter { it.assigneeName == null }
            else -> DemoTasks.casework(today)
        }
        val delayed = if (scope == TaskScope.Casework) all.count { it.delay != null } else 0
        val overdue = all.count { it.due?.isBefore(today) == true && it.delay == null }
        return Result.success(TaskCounts(all.size, overdue, delayed))
    }

    override suspend fun task(id: Int): Result<TaskDetail> {
        backend.failRefreshes?.let { return Result.failure(it) }
        return (DemoTasks.casework(today) + DemoTasks.employee(today)).firstOrNull {
            it.id == id
        }?.let { Result.success(it) }
            ?: Result.failure(WaiveraError.NotFound())
    }

    override suspend fun activity(taskId: Int): Result<List<TaskActivity>> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val now = backend.now()
        val earlier = listOf(
            TaskActivity(2, "Documentation", 30, now.minusSeconds(DAY * 6), DemoData.ME, "You", null),
            TaskActivity(1, "Phone call", 20, now.minusSeconds(DAY * 2), "demo-priya", "Priya Raman", null),
        )
        // Oldest first, as the live list reads; new entries were kept newest first.
        return Result.success(earlier + logged.value[taskId].orEmpty().reversed())
    }

    override suspend fun attachments(taskId: Int): Result<List<TaskAttachment>> {
        backend.failRefreshes?.let { return Result.failure(it) }
        val now = backend.now()
        return Result.success(
            listOf(
                demoFile(1, "Care plan 2026.pdf", 182_000, now.minusSeconds(DAY), "Priya Raman"),
                demoFile(2, "Visit notes.txt", 2_400, now.minusSeconds(DAY * 3), "You"),
                demoFile(3, "Home photo.png", 640_000, now.minusSeconds(DAY * 4), "Priya Raman"),
                demoFile(4, "Intake packet.docx", 26_000_000, now.minusSeconds(DAY * 30), "Jordan Lee"),
            ),
        )
    }

    private fun demoFile(id: Int, name: String, size: Long, at: java.time.Instant, by: String) = TaskAttachment(
        attachmentId = id,
        fileId = "demo-file-$id",
        name = name,
        sizeBytes = size,
        addedAt = at,
        addedBy = by,
        employeeFile = false,
        kind = FileKind.of(name, null),
    )

    override suspend fun activityTypes(): Result<List<ActivityType>> = Result.success(
        listOf(
            ActivityType(11, "Documentation", null, null),
            ActivityType(12, "Home visit", null, null),
            ActivityType(13, "Phone call", null, null),
            ActivityType(14, "Monthly contact", 15, "Monthly contact completed with the participant."),
            ActivityType(
                15,
                "Signed visit form",
                30,
                null,
                needsFile = true,
                fileTemplate = FileTemplate(903, "Signed visit form", 1, null),
            ),
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
        task(991, "HEA-991", "Quarterly monitoring visit", today.minusDays(2), TaskState.InProgress, "Jordan Ellis")
            .copy(
                delay = TaskDelay(
                    reason = "Participant hospitalized",
                    explanation = "Jordan is in the hospital until next week; the visit moves to after discharge.",
                    start = today.minusDays(3),
                    through = today.plusDays(5),
                ),
            ),
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

    /** The agency's client tasks the demo person can see: their own and two coworkers'. */
    fun casework(today: LocalDate): List<TaskDetail> = all(today) + listOf(
        task(1063, "HEA-1063", "Monthly contact note", today.plusDays(3), TaskState.InProgress, "Ana Ruiz")
            .copy(assigneeName = "Priya Raman"),
        task(1078, "HEA-1078", "Annual plan signatures", today.minusDays(4), TaskState.NotStarted, "Sam Lee")
            .copy(assigneeName = "Luis Ortega"),
    )

    /** The demo person's own employee tasks (training, paperwork), for My work. */
    fun employee(today: LocalDate): List<TaskDetail> = listOf(
        task(1102, "HEA-1102", "Complete HIPAA refresher", today.plusDays(1), TaskState.NotStarted, null)
            .copy(isClientTask = false, type = "Training"),
        task(1088, "HEA-1088", "Upload CPR certificate", today.minusDays(2), TaskState.InProgress, null)
            .copy(isClientTask = false, type = "Credential"),
        task(1110, "HEA-1110", "Sign updated handbook", today.plusDays(9), TaskState.NotStarted, null)
            .copy(isClientTask = false, type = "Acknowledgement"),
    )

    fun summary(task: TaskDetail) = TaskSummary(
        id = task.id,
        key = task.key,
        title = task.title,
        due = task.due,
        state = task.state,
        about = task.about,
        assigneeName = task.assigneeName,
        pendingClosure = task.pendingRequest?.kind == RequestKind.Closure,
        delayed = task.delay != null,
        delayedThrough = task.delay?.through,
    )

    @Suppress("LongParameterList")
    private fun task(id: Int, key: String, title: String, due: LocalDate, state: TaskState, about: String?) =
        TaskDetail(
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
