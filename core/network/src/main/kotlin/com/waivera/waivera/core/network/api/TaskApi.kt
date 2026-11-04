package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ActionSubTypeResponse
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.DocumentCategoryResponse
import com.waivera.waivera.core.network.dto.RequiredAttachmentResponse
import com.waivera.waivera.core.network.dto.TaskActionRequest
import com.waivera.waivera.core.network.dto.TaskActionResponse
import com.waivera.waivera.core.network.dto.TaskAttachmentResponse
import com.waivera.waivera.core.network.dto.TaskPageResponse
import com.waivera.waivera.core.network.dto.TaskResponse
import com.waivera.waivera.core.network.dto.TaskUploadResponse
import com.waivera.waivera.core.network.dto.TemplateFileResponse
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * `Controllers/Tasks/TaskController.cs` (`api/tasks`) and `Controllers/TaskActionController.cs`
 * (`api/taskActions`): the person's own tasks, one task, its activity, and logging activity.
 */
@Suppress("TooManyFunctions") // One function per endpoint the Tasks tab reads or writes.
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
        @Query("taskDelayFilter") taskDelayFilter: String? = null,
        @Query("excludeStatus") excludeStatus: List<String>? = listOf(CLOSED),
        @Query("sort") sort: List<String>? = listOf(SORT_DUE),
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = PAGE_SIZE,
    ): Response<TaskPageResponse>

    @GET("api/tasks/{id}")
    suspend fun getTask(@Path("id") id: Int): Response<TaskResponse>

    /** A task by the number people see, in the caller's agency, with the same access checks. */
    @GET("api/tasks/lookup/{friendlyId}")
    suspend fun lookupTask(@Path("friendlyId") friendlyId: Int): Response<TaskResponse>

    @GET("api/tasks/{id}/actions")
    suspend fun getActivity(@Path("id") id: Int): Response<List<TaskActionResponse>>

    @GET("api/taskActions/actionSubTypes")
    suspend fun getActivityTypes(): Response<List<ActionSubTypeResponse>>

    /** Enveloped `ApiResponse<int?>` with the new activity's id. */
    @POST("api/taskActions/new")
    suspend fun logActivity(@Body request: TaskActionRequest): Response<JsonElement>

    /** The task's files, as the web's attachment list shows them; access is checked per task. */
    @GET("api/tasks/attachments/{taskId}")
    suspend fun getAttachments(@Path("taskId") taskId: Int): Response<List<TaskAttachmentResponse>>

    /** A case file's bytes; the server checks the reader's access to this file on every call. */
    @Streaming
    @GET("api/file/download/{fileId}")
    suspend fun downloadCaseFile(@Path("fileId") fileId: String): Response<ResponseBody>

    /** An employee file attached to an employee task, checked against that task. */
    @Streaming
    @GET("api/employee-file/task-view/{taskId}/{fileId}")
    suspend fun downloadEmployeeTaskFile(
        @Path("taskId") taskId: Int,
        @Path("fileId") fileId: String,
    ): Response<ResponseBody>

    /** The required files still missing on the task; access is checked per task (404 otherwise). */
    @GET("api/tasks/requiredAttachments/{taskId}")
    suspend fun getRequiredAttachments(@Path("taskId") taskId: Int): Response<List<RequiredAttachmentResponse>>

    /** The organization's file templates; an activity type that needs a file names one by id. */
    @GET("api/file/templateFiles")
    suspend fun getTemplateFiles(): Response<List<TemplateFileResponse>>

    /**
     * The web's `uploadRequiredFile`: one file onto a task, optionally filling a requirement. The
     * server checks edit access, a locked task (409), the type and size, and the requirement.
     */
    @Multipart
    @POST("api/file/upload")
    suspend fun uploadTaskFile(@Part parts: List<MultipartBody.Part>): Response<TaskUploadResponse>

    /** Employee file categories; an extra file on an employee task needs one. */
    @GET("api/documentCategory/getall")
    suspend fun getDocumentCategories(): Response<ApiResponse<List<DocumentCategoryResponse>>>

    companion object {
        const val PAGE_SIZE = 25
        const val CLOSED = "CLOSED"
        const val SORT_DUE = "due:asc"
    }
}
