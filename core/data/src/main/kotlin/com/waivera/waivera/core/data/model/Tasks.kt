package com.waivera.waivera.core.data.model

import java.time.Instant
import java.time.LocalDate

/**
 * Which of the person's open tasks to list. The server judges the dates in the employee's saved
 * zone: [DueSoon] is today through a month out, [Overdue] is before today.
 */
enum class TaskWindow { DueSoon, Overdue, AllOpen }

/**
 * Whose tasks to list. [Casework] is the web's client task list, scoped on the server by role
 * (an owner sees the agency, a coordinator their caseload); [Mine] is the person's own employee
 * work (training, credentials), as the web's Employee tasks view.
 */
enum class TaskScope { Casework, Mine }

/**
 * How casework treats tasks under an approved delay, as the web's Task Delay filter: [Show] lists
 * them (in their own group), [Hide] leaves them out, [Only] lists nothing else. A delay never moves
 * the due date; it only means the task is not really overdue while it lasts.
 */
enum class DelayFilter { Show, Hide, Only }

/** One list request. [assignedToMe] narrows casework to the person's own tasks. */
data class TaskQuery(
    val scope: TaskScope,
    val window: TaskWindow,
    val search: String = "",
    val assignedToMe: Boolean = false,
    /** Casework only: the server applies delay filters to client tasks alone. */
    val delays: DelayFilter = DelayFilter.Show,
)

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
    val assigneeId: String? = null,
    val assigneeName: String? = null,
    /** A closure request waits on review. */
    val pendingClosure: Boolean = false,
    /** An approved delay covers today, judged in the employee's zone: the task is not really overdue. */
    val delayed: Boolean = false,
    /** That delay's last day, when the server sends one. */
    val delayedThrough: LocalDate? = null,
)

data class TaskPage(val items: List<TaskSummary>, val totalCount: Int)

/** [overdue] leaves out delayed tasks, as the web's to-dos do; [delayed] counts those under a delay today. */
data class TaskCounts(val open: Int, val overdue: Int, val delayed: Int = 0)

/** The approved delay covering today. The due date stays as it was. */
data class TaskDelay(val reason: String?, val explanation: String?, val start: LocalDate?, val through: LocalDate?)

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
    val assigneeName: String? = null,
    /** A participant task; employee tasks have no extension or delay requests. */
    val isClientTask: Boolean = true,
    /** A change request waiting on review, if any. */
    val pendingRequest: ChangeRequest? = null,
    /** The approved delay covering today, if any. */
    val delay: TaskDelay? = null,
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
data class ActivityType(
    val id: Int,
    val name: String,
    val recommendedMinutes: Int?,
    val fixedDescription: String?,
    /** Logging it takes one uploaded file; the server then sets its time to [recommendedMinutes]. */
    val needsFile: Boolean = false,
    /** The template that file is saved as, when the organization still has it. */
    val fileTemplate: FileTemplate? = null,
)

data class NewActivity(
    val taskId: Int,
    val typeId: Int,
    val minutes: Int,
    val completedAt: Instant,
    val description: String,
)

/** How a task file can be shown on the phone; [Other] opens on the web. */
enum class FileKind {
    Pdf,
    Image,
    Text,
    Word,
    Other,
    ;

    companion object {
        private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
        private val texts = setOf("txt", "text", "log", "md")

        /** The name's extension decides, as on the web; the type only fills in for a bare name. */
        fun of(name: String, mimeType: String?): FileKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            val mime = mimeType.orEmpty().lowercase()
            return when {
                ext == "pdf" -> Pdf
                ext in images -> Image
                ext in texts -> Text
                ext == "docx" -> Word
                ext.isNotEmpty() -> Other
                mime == "application/pdf" -> Pdf
                mime.startsWith("image/") -> Image
                mime == "text/plain" -> Text
                mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> Word
                else -> Other
            }
        }
    }
}

/** A file on a task. Opening reads it on the phone; saving or sharing needs the web's download permission. */
data class TaskAttachment(
    val attachmentId: Int,
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
    val addedAt: Instant?,
    val addedBy: String?,
    /** Served through the employee-file route, checked against the task. */
    val employeeFile: Boolean,
    val kind: FileKind,
) {
    /** Shown here when the phone can draw it and it is small enough, as the web's preview limit. */
    val viewable: Boolean get() = kind != FileKind.Other && sizeBytes <= MAX_VIEW_BYTES

    companion object {
        /** The web previews Word files up to 20 MB; the phone uses the same limit for every type. */
        const val MAX_VIEW_BYTES = 20L * 1024 * 1024

        /** Saving or sharing stops past this size, so a broken download can't fill the phone. */
        const val MAX_EXPORT_BYTES = 200L * 1024 * 1024
    }
}
