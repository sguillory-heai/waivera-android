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
    val taskContext: String? = null,
    val lastActivityDate: String? = null,
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
    val subjectEmployee: TaskUserResponse? = null,
    val taskContext: String? = null,
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
