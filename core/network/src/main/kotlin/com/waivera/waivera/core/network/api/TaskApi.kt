package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ActionSubTypeResponse
import com.waivera.waivera.core.network.dto.TaskActionRequest
import com.waivera.waivera.core.network.dto.TaskActionResponse
import com.waivera.waivera.core.network.dto.TaskPageResponse
import com.waivera.waivera.core.network.dto.TaskResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * `Controllers/Tasks/TaskController.cs` (`api/tasks`) and `Controllers/TaskActionController.cs`
 * (`api/taskActions`): the person's own tasks, one task, its activity, and logging activity.
 */
interface TaskApi {
    /**
     * Tasks the server lets [userId] see. With [assignee] (the person themself) it is their own
     * work; without it and with [taskContext] `Client` it is the casework the web lists, scoped
     * on the server by the person's role. Finished tasks are left out unless a status is asked
     * for; [excludeStatus] also drops closed ones. [date] is `overdue`, `today`, `week` or
     * `month`, judged on the server in the employee's saved zone.
     */
    @Suppress("LongParameterList")
    @GET("api/tasks/user/{userId}")
    suspend fun getMyTasks(
        @Path("userId") userId: String,
        @Query("assignee") assignee: String?,
        @Query("taskContext") taskContext: String? = null,
        @Query("includePendingApprovals") includePendingApprovals: Boolean? = null,
        @Query("searchTerm") searchTerm: String = "",
        @Query("date") date: List<String>? = null,
        @Query("excludeStatus") excludeStatus: List<String>? = listOf(CLOSED),
        @Query("sort") sort: List<String>? = listOf(SORT_DUE),
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = PAGE_SIZE,
    ): Response<TaskPageResponse>

    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): Response<TaskResponse>

    @GET("api/tasks/{id}/actions")
    suspend fun getActivity(@Path("id") id: Int): Response<List<TaskActionResponse>>

    @GET("api/taskActions/actionSubTypes")
    suspend fun getActivityTypes(): Response<List<ActionSubTypeResponse>>

    /** Enveloped `ApiResponse<int?>` with the new activity's id. */
    @POST("api/taskActions/new")
    suspend fun logActivity(@Body request: TaskActionRequest): Response<JsonElement>

    companion object {
        const val PAGE_SIZE = 25
        const val CLOSED = "CLOSED"
        const val SORT_DUE = "due:asc"
    }
}
