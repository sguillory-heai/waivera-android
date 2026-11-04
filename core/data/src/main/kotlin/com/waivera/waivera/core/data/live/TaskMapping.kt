package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.network.dto.ActionSubTypeResponse
import com.waivera.waivera.core.network.dto.OrganizationResponse
import com.waivera.waivera.core.network.dto.TaskActionResponse
import com.waivera.waivera.core.network.dto.TaskListResponse
import com.waivera.waivera.core.network.dto.TaskParticipantResponse
import com.waivera.waivera.core.network.dto.TaskResponse
import com.waivera.waivera.core.network.dto.TaskUserResponse
import com.waivera.waivera.core.network.time.WaiveraTime

/**
 * Turns the server's task shapes into the phone's. Due and start dates are literal calendar
 * dates: only their `YYYY-MM-DD` part is read, so no zone ever moves a task to another day.
 * Activity times are UTC instants.
 */
object TaskMapping {
    private const val EMPLOYEE_CONTEXT = "Employee"

    fun summary(dto: TaskListResponse): TaskSummary = TaskSummary(
        id = dto.id,
        key = key(dto.organization, dto.friendlyId),
        title = dto.title?.trim().orEmpty(),
        due = WaiveraTime.parseLocalDate(dto.effectiveEndDate ?: dto.endDate),
        state = TaskState.of(dto.taskStatus?.name),
        about = about(dto.participant, dto.subjectEmployee),
        assigneeId = dto.assignee?.id,
        assigneeName = name(dto.assignee),
        pendingClosure = dto.hasPendingDeletion,
        delayed = dto.currentTaskDelay != null,
    )

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
            assigneeName = name(dto.assignee),
            isClientTask = !dto.taskContext.equals(EMPLOYEE_CONTEXT, ignoreCase = true),
            pendingRequest = dto.administrativeTransactionRequests.orEmpty()
                .map(RequestMapping::request)
                .filter { it.status == RequestStatus.Pending }
                .maxByOrNull { it.requestedAt ?: java.time.Instant.MIN },
        )
    }

    /** Replies in an activity's thread and removed activity are not shown. */
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
        .sortedWith(compareByDescending<TaskActivity> { it.at }.thenByDescending { it.id })

    /** Types that need a file go to the web, which has the file picker the server checks. */
    fun activityTypes(items: List<ActionSubTypeResponse>): List<ActivityType> = items
        .filter { it.isUserLoggable && (it.requiresFile ?: 0) <= 0 && !it.actionSubTypeName.isNullOrBlank() }
        .map {
            ActivityType(
                id = it.id,
                name = readable(it.actionSubTypeName.orEmpty()),
                recommendedMinutes = it.recommendedTimeInMinutes?.takeIf { minutes -> minutes > 0 },
                fixedDescription = it.description?.trim()?.takeIf(String::isNotEmpty),
            )
        }
        .sortedBy { it.name.lowercase() }

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
