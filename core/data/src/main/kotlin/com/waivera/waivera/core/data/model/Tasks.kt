package com.waivera.waivera.core.data.model

import java.time.Instant
import java.time.LocalDate

/**
 * Which of the person's open tasks to list. The server judges the dates in the employee's saved
 * zone: [DueSoon] is today through a month out, [Overdue] is before today.
 */
enum class TaskWindow { DueSoon, Overdue, AllOpen }

/** A task's workflow state, by its server name. Unknown names keep their text in [TaskState.Other]. */
sealed interface TaskState {
    data object NotStarted : TaskState

    data object InProgress : TaskState

    data object InReview : TaskState

    data object Completed : TaskState

    data object Closed : TaskState

    data class Other(val name: String) : TaskState

    companion object {
        fun of(name: String?): TaskState = when (name?.trim()?.uppercase()) {
            null, "" -> Other("")
            "NOT STARTED", "OPEN", "NEW", "TO DO" -> NotStarted
            "IN PROGRESS", "ACTIVE" -> InProgress
            "IN REVIEW", "PENDING APPROVAL", "AWAITING APPROVAL" -> InReview
            "COMPLETED" -> Completed
            "CLOSED" -> Closed
            else -> Other(name.trim())
        }
    }
}

/** One row of the task list: list fields only, nothing else is kept on the phone. */
data class TaskSummary(
    val id: Int,
    /** The agency's label, as the web shows it: `HEA-1042`. */
    val key: String,
    val title: String,
    /** A literal calendar date, never shifted by a zone. */
    val due: LocalDate?,
    val state: TaskState,
    /** Who or what the task is about: a participant or, for employee tasks, an employee. */
    val about: String?,
)

data class TaskPage(val items: List<TaskSummary>, val totalCount: Int)

data class TaskCounts(val open: Int, val overdue: Int)

data class TaskDetail(
    val id: Int,
    val key: String,
    val title: String,
    val description: String?,
    val state: TaskState,
    val type: String?,
    val start: LocalDate?,
    val due: LocalDate?,
    val assignedBy: String?,
    val about: String?,
    /** The web app's page for the task, relative to the configured web base. */
    val webPath: String,
) {
    /** Completed or closed: the server refuses new activity. */
    val isFinished: Boolean get() = state == TaskState.Completed || state == TaskState.Closed
}

/** One logged activity on a task. */
data class TaskActivity(
    val id: Int,
    val type: String,
    val minutes: Int,
    /** When the work happened, else when it was logged. A UTC instant. */
    val at: Instant?,
    val authorId: String?,
    val authorName: String?,
    val description: String?,
)

/**
 * An activity type the person may log. [fixedDescription] and [recommendedMinutes] lock those
 * fields, as on the web; types that need a file are left to the web.
 */
data class ActivityType(val id: Int, val name: String, val recommendedMinutes: Int?, val fixedDescription: String?)

data class NewActivity(
    val taskId: Int,
    val typeId: Int,
    val minutes: Int,
    val completedAt: Instant,
    val description: String,
)
