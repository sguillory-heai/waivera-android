package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Fixed content for previews and screenshot tests. */
internal object TaskPreviewData {
    private val Zone: ZoneId = ZoneId.of("America/Chicago")
    private val Today: LocalDate = LocalDate.of(2026, 11, 4)
    private val Now: Instant = Today.atTime(14, 30).atZone(Zone).toInstant()

    fun task(state: TaskState = TaskState.NotStarted) = TaskDetail(
        id = 1042,
        key = "HEA-1042",
        title = "Annual plan review",
        description = "Review the annual plan with the family and update goals.",
        state = state,
        type = "Plan review",
        start = Today.minusDays(7),
        due = Today.plusDays(2),
        assignedBy = "Priya Raman",
        about = "Jordan Lee",
        webPath = "/task/HEA-1042",
    )

    fun detailState(state: TaskState = TaskState.NotStarted): TaskDetailUiState {
        val task = task(state)
        return TaskDetailUiState(
            phase = DetailPhase.Content,
            task = task,
            due = TaskFormat.due(task.due, Today),
            dueDate = task.due?.let { TaskFormat.date(it, Today) },
            activity = Loadable.Ready(
                listOf(
                    ActivityRow(
                        1,
                        "Phone call",
                        20,
                        TaskFormat.moment(Now.minusSeconds(DAY * 2), Zone, Today),
                        true,
                        "You",
                        null,
                    ),
                    ActivityRow(
                        2,
                        "Documentation",
                        30,
                        TaskFormat.moment(Now.minusSeconds(DAY * 7), Zone, Today),
                        false,
                        "Priya Raman",
                        "Drafted the goals section.",
                    ),
                ),
            ),
        )
    }

    fun logForm() = LogForm(
        types = Loadable.Ready(
            listOf(ActivityType(1, "Home visit", null, null), ActivityType(2, "Phone call", 15, null)),
        ),
        typeId = 1,
        minutes = 75,
        completedAt = Now,
        zone = Zone,
        whenText = TaskFormat.moment(Now, Zone, Today),
    )

    private const val DAY = 86_400L
}
