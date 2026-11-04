package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.model.TaskDelay
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.network.dto.ActionSubTypeResponse
import com.waivera.waivera.core.network.dto.OrganizationResponse
import com.waivera.waivera.core.network.dto.TaskActionResponse
import com.waivera.waivera.core.network.dto.TaskAssignmentResponse
import com.waivera.waivera.core.network.dto.TaskAttachmentResponse
import com.waivera.waivera.core.network.dto.TaskDelaySummaryResponse
import com.waivera.waivera.core.network.dto.TaskListResponse
import com.waivera.waivera.core.network.dto.TaskParticipantResponse
import com.waivera.waivera.core.network.dto.TaskResponse
import com.waivera.waivera.core.network.dto.TaskUserResponse
import com.waivera.waivera.core.network.dto.TemplateFileResponse
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.Instant

/**
 * Turns the server's task shapes into the phone's. Due and start dates are literal calendar
 * dates: only their `YYYY-MM-DD` part is read, so no zone ever moves a task to another day.
 * Activity times are UTC instants.
 */
@Suppress("TooManyFunctions") // One mapper per task DTO, kept together with their shared helpers.
object TaskMapping {
    private const val EMPLOYEE_CONTEXT = "Employee"
    private const val EMPLOYEE_FILE = "employeeFile"

    fun summary(dto: TaskListResponse): TaskSummary {
        val assignee = assignee(dto.assignee, dto.assignees)
        return summaryOf(dto, assignee)
    }

    private fun summaryOf(dto: TaskListResponse, assignee: TaskUserResponse?) = TaskSummary(
        id = dto.id,
        key = key(dto.organization, dto.friendlyId),
        title = dto.title?.trim().orEmpty(),
        due = WaiveraTime.parseLocalDate(dto.effectiveEndDate ?: dto.endDate),
        state = TaskState.of(dto.taskStatus?.name),
        about = about(dto.participant, dto.subjectEmployee),
        assigneeId = assignee?.id,
        assigneeName = name(assignee),
        pendingClosure = dto.hasPendingDeletion,
        delayed = dto.currentTaskDelay != null,
        delayedThrough = WaiveraTime.parseLocalDate(dto.currentTaskDelay?.delayEndDate),
    )

    /**
     * The main assignee, as the server picks it: the first assignment that is neither the
     * reporter nor a co-assignee. Task endpoints send the list, not the single field.
     */
    private fun assignee(single: TaskUserResponse?, list: List<TaskAssignmentResponse>?): TaskUserResponse? =
        single ?: list.orEmpty().firstOrNull { !it.isReporter && !it.isCoAssignee }?.user

    fun detail(dto: TaskResponse): TaskDetail {
        val key = key(dto.organization, dto.friendlyId)
        return TaskDetail(
            id = dto.id,
            key = key,
            title = dto.title?.trim().orEmpty(),
            description = dto.description?.trim()?.takeIf(String::isNotEmpty),
            state = TaskState.of(dto.taskStatus?.name),
            type = dto.subType?.name?.trim()?.takeIf(String::isNotEmpty),
            start = WaiveraTime.parseLocalDate(dto.startDate),
            due = WaiveraTime.parseLocalDate(dto.effectiveEndDate ?: dto.endDate),
            assignedBy = name(dto.reporter),
            about = about(dto.participant, dto.subjectEmployee),
            webPath = "/task/$key",
            assigneeName = name(assignee(dto.assignee, dto.assignees)),
            isClientTask = !dto.taskContext.equals(EMPLOYEE_CONTEXT, ignoreCase = true),
            pendingRequest = dto.administrativeTransactionRequests.orEmpty()
                .map(RequestMapping::request)
                .filter { it.status == RequestStatus.Pending }
                .maxByOrNull { it.requestedAt ?: Instant.MIN },
            delay = dto.currentTaskDelay?.let(::delay),
            review = ReviewMapping.access(dto.capabilities, dto.isTaskReady),
            steps = ReviewMapping.steps(dto.workflowBlueprint),
            planDates = ReviewMapping.planDates(dto.participant),
        )
    }

    fun delay(dto: TaskDelaySummaryResponse): TaskDelay = TaskDelay(
        reason = dto.reasonName?.trim()?.takeIf(String::isNotEmpty),
        explanation = dto.explanation?.trim()?.takeIf(String::isNotEmpty),
        start = WaiveraTime.parseLocalDate(dto.delayStartDate),
        through = WaiveraTime.parseLocalDate(dto.delayEndDate),
    )

    /**
     * Oldest first, so new work lands at the bottom as in a conversation. Replies in an
     * activity's thread and removed activity are not shown.
     */
    fun activity(items: List<TaskActionResponse>): List<TaskActivity> = items
        .filter { !it.deleted && it.parentTaskActionId == null }
        .map { dto ->
            TaskActivity(
                id = dto.id,
                type = dto.actionSubType?.actionSubTypeName?.let(::readable).orEmpty(),
                minutes = dto.durationInMinutes.coerceAtLeast(0),
                at = WaiveraTime.parseInstant(dto.completionTime) ?: WaiveraTime.parseInstant(dto.createDate),
                authorId = dto.author?.id,
                authorName = name(dto.author),
                description = dto.description?.trim()?.takeIf(String::isNotEmpty),
            )
        }
        .sortedWith(compareBy<TaskActivity, Instant?>(nullsFirst()) { it.at }.thenBy { it.id })

    /** Files with no id or one an employee-file deletion removed are left out; newest first. */
    fun attachments(items: List<TaskAttachmentResponse>): List<TaskAttachment> = items
        .filter { !it.employeeFileDeleted }
        .mapNotNull { dto ->
            val file = dto.file ?: return@mapNotNull null
            val fileId = file.id?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val name = file.fileName?.trim()?.takeIf(String::isNotEmpty) ?: fileId
            TaskAttachment(
                attachmentId = dto.id,
                fileId = fileId,
                name = name,
                sizeBytes = file.size.coerceAtLeast(0),
                addedAt = WaiveraTime.parseInstant(file.createDate),
                addedBy = name(file.author),
                employeeFile = dto.fileStorageKind.equals(EMPLOYEE_FILE, ignoreCase = true),
                kind = FileKind.of(name, file.mimeType),
            )
        }
        .sortedWith(
            compareByDescending<TaskAttachment, Instant?>(nullsFirst()) {
                it.addedAt
            }.thenByDescending { it.attachmentId },
        )

    /**
     * The types a person can log. One that needs a file names a template by id (sprout_web
     * `LogActivityModal`); a template the organization no longer has leaves the file under its own name.
     */
    fun activityTypes(
        items: List<ActionSubTypeResponse>,
        templates: List<TemplateFileResponse> = emptyList(),
    ): List<ActivityType> = items
        .filter { it.isUserLoggable && !it.actionSubTypeName.isNullOrBlank() }
        .map {
            val templateId = it.requiresFile?.takeIf { id -> id > 0 }
            ActivityType(
                id = it.id,
                name = readable(it.actionSubTypeName.orEmpty()),
                recommendedMinutes = it.recommendedTimeInMinutes?.takeIf { minutes -> minutes > 0 },
                fixedDescription = it.description?.trim()?.takeIf(String::isNotEmpty),
                needsFile = templateId != null,
                fileTemplate = templateId?.let { id -> templates.firstOrNull { t -> t.id == id } }?.let(::template),
            )
        }
        .sortedBy { it.name.lowercase() }

    private fun template(dto: TemplateFileResponse): FileTemplate? {
        val name = dto.fileName?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return FileTemplate(dto.id, name, dto.type?.id ?: UploadTarget.PLAIN_TYPE_ID, dto.employeeDocumentCategoryId)
    }

    /** `HEA-1042` as the web labels it; just the number when the agency has no abbreviation. */
    fun key(organization: OrganizationResponse?, friendlyId: Int): String {
        val abbreviation = organization?.abbreviation?.trim()?.takeIf(String::isNotEmpty)
        return if (abbreviation == null) friendlyId.toString() else "$abbreviation-$friendlyId"
    }

    fun name(user: TaskUserResponse?): String? {
        user ?: return null
        val display = user.displayName?.trim()?.takeIf(String::isNotEmpty)
        return display ?: listOfNotNull(user.firstName?.trim(), user.lastName?.trim())
            .filter(String::isNotEmpty)
            .joinToString(" ")
            .ifEmpty { null }
    }

    private fun about(participant: TaskParticipantResponse?, employee: TaskUserResponse?): String? {
        val participantName = participant?.let {
            listOfNotNull(it.firstName?.trim(), it.lastName?.trim()).filter(String::isNotEmpty).joinToString(" ")
        }?.ifEmpty { null }
        return participantName ?: name(employee)
    }

    /** Some system types are stored in capitals (`PHONE CALL`); people read them in sentence case. */
    private fun readable(name: String): String {
        val trimmed = name.trim()
        if (trimmed.any(Char::isLowerCase)) return trimmed
        return trimmed.lowercase().replaceFirstChar(Char::uppercaseChar)
    }
}
