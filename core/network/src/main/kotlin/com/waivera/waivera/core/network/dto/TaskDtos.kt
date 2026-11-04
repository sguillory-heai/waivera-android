package com.waivera.waivera.core.network.dto

import kotlinx.serialization.Serializable

/*
 * Mirrors of the sprout_api task contract the phone reads. Every field the phone does not use
 * is left out (ignoreUnknownKeys), and dates stay strings until mapping: task start and due
 * dates are literal calendar dates sent as `YYYY-MM-DDT00:00:00`, while activity times are
 * UTC instants (timezone-standard.md, task comment timestamp boundary).
 */

/** `GET api/tasks/user/{userId}` returns `{ tasks, totalCount }`. */
@Serializable
data class TaskPageResponse(val tasks: List<TaskListResponse> = emptyList(), val totalCount: Int = 0)

/** `dto/Response/TaskListResponse.cs`. */
@Serializable
data class TaskListResponse(
    val id: Int = 0,
    val friendlyId: Int = 0,
    val title: String? = null,
    val organization: OrganizationResponse? = null,
    val subType: TaskSubTypeResponse? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val effectiveEndDate: String? = null,
    val taskStatus: StatusResponse? = null,
    val participant: TaskParticipantResponse? = null,
    val subjectEmployee: TaskUserResponse? = null,
    val assignee: TaskUserResponse? = null,
    /** Who the task is assigned to; the server fills this, not [assignee], on list endpoints. */
    val assignees: List<TaskAssignmentResponse>? = null,
    val taskContext: String? = null,
    val lastActivityDate: String? = null,
    val hasPendingDeletion: Boolean = false,
    val currentTaskDelay: TaskDelaySummaryResponse? = null,
)

/**
 * `TaskDelayListResponse`: the approved delay whose period covers the caller's today (the server
 * judges it in their saved zone). Its dates are literal calendar dates.
 */
@Serializable
data class TaskDelaySummaryResponse(
    val id: Long = 0,
    val reasonName: String? = null,
    val explanation: String? = null,
    val delayStartDate: String? = null,
    val delayEndDate: String? = null,
)

/** `dto/Response/TaskResponse.cs`, the fields the task screen shows. */
@Serializable
data class TaskResponse(
    val id: Int = 0,
    val friendlyId: Int = 0,
    val title: String? = null,
    val description: String? = null,
    val organization: OrganizationResponse? = null,
    val subType: TaskSubTypeResponse? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val effectiveEndDate: String? = null,
    val completionTime: String? = null,
    val taskStatus: StatusResponse? = null,
    val participant: TaskParticipantResponse? = null,
    val reporter: TaskUserResponse? = null,
    val assignee: TaskUserResponse? = null,
    val assignees: List<TaskAssignmentResponse>? = null,
    val subjectEmployee: TaskUserResponse? = null,
    val taskContext: String? = null,
    /** Change requests on the task; a pending one shows on the task page. */
    val administrativeTransactionRequests: List<AdminRequestResponse>? = null,
    val currentTaskDelay: TaskDelaySummaryResponse? = null,
)

@Serializable
data class StatusResponse(val id: Int = 0, val name: String? = null)

@Serializable
data class TaskSubTypeResponse(val id: Int = 0, val name: String? = null)

/** `ParticipantResponse`: only the name; the rest is participant record detail. */
@Serializable
data class TaskParticipantResponse(val id: Int = 0, val firstName: String? = null, val lastName: String? = null)

/** `UserResponse`. */
@Serializable
data class TaskUserResponse(
    val id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
)

/** `dto/Response/TaskAssignmentReponse.cs`: one person on a task, and in what role. */
@Serializable
data class TaskAssignmentResponse(
    val user: TaskUserResponse? = null,
    val isReporter: Boolean = false,
    val isCoAssignee: Boolean = false,
)

/** `TaskActionResponse` from `GET api/tasks/{taskId}/actions`, oldest first. */
@Serializable
data class TaskActionResponse(
    val id: Int = 0,
    val taskId: Int = 0,
    val author: TaskUserResponse? = null,
    val description: String? = null,
    val actionSubType: ActionSubTypeResponse? = null,
    val createDate: String? = null,
    val completionTime: String? = null,
    val durationInMinutes: Int = 0,
    val deleted: Boolean = false,
    val parentTaskActionId: Int? = null,
)

/** `Models/ActionSubType.cs` from `GET api/taskActions/actionSubTypes`: the loggable activity types. */
@Serializable
data class ActionSubTypeResponse(
    val id: Int = 0,
    val actionSubTypeName: String? = null,
    val recommendedTimeInMinutes: Int? = null,
    val description: String? = null,
    val requiresFile: Int? = null,
    val isUserLoggable: Boolean = false,
)

/** `dto/Request/TaskActionRequest.cs` for `POST api/taskActions/new`. [completionTime] is ISO-8601 UTC. */
@Serializable
data class TaskActionRequest(
    val taskId: Int,
    val description: String,
    val subTypeId: Int,
    val durationInMinutes: Int,
    val completionTime: String,
)

/**
 * `TaskRequiresAttachmentResponse` from `GET api/tasks/requiredAttachments/{taskId}`: the files
 * still missing on a task. A requirement with a [TemplateFileResponse.form] is filled as a form.
 */
@Serializable
data class RequiredAttachmentResponse(
    val id: Int = 0,
    val templateFile: TemplateFileResponse? = null,
    val removalRequestPending: Boolean = false,
)

@Serializable
data class TemplateFileResponse(
    val id: Int = 0,
    val fileName: String? = null,
    val type: FileTypeResponse? = null,
    val form: FormRefResponse? = null,
    /** The employee file category an upload for this template is filed under. */
    val employeeDocumentCategoryId: Int? = null,
)

@Serializable
data class FileTypeResponse(val id: Int = 0, val type: String? = null)

@Serializable
data class FormRefResponse(val id: Int = 0, val name: String? = null)

/** `POST api/file/upload`: the saved file and the name it was saved under ("Fax (2).pdf"). */
@Serializable
data class TaskUploadResponse(val fileId: String? = null, val fileName: String? = null)

/** `dto/Response/DocumentCategoryDto.cs`: an employee file category. */
@Serializable
data class DocumentCategoryResponse(val id: Int = 0, val documentCategoryName: String? = null)
