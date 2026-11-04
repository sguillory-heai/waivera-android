package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.ApprovalRequirementsResponse
import com.waivera.waivera.core.network.dto.ApprovalResultResponse
import com.waivera.waivera.core.network.dto.ApproveTaskStageBody
import com.waivera.waivera.core.network.dto.EmployeeClosureBody
import com.waivera.waivera.core.network.dto.EmployeeClosureOptionsResponse
import com.waivera.waivera.core.network.dto.LateContextResponse
import com.waivera.waivera.core.network.dto.LateJustificationBody
import com.waivera.waivera.core.network.dto.LateJustificationResult
import com.waivera.waivera.core.network.dto.LateReviewBody
import com.waivera.waivera.core.network.dto.RejectOptionResponse
import com.waivera.waivera.core.network.dto.RejectTaskBody
import com.waivera.waivera.core.network.dto.TaskFormDraftResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Submitting, approving and rejecting a task, with the steps the web runs on the way
 * (sprout_web `TaskPage.HandleApprove`): the late reason and its review, unfinished forms, the
 * employee closure choice and the stage's own requirements.
 */
@Suppress("TooManyFunctions") // One function per endpoint the review chain calls.
interface WorkflowApi {
    /** [mode] is `CurrentStage` or `AllRemaining`. */
    @GET("api/workflow/task/{taskId}/approval-requirements")
    suspend fun requirements(
        @Path("taskId") taskId: Int,
        @Query("mode") mode: String,
    ): Response<ApprovalRequirementsResponse>

    /** Refusals come back as 409/422 with an `ApprovalErrorResponse` the caller reads itself. */
    @POST("api/workflow/approve/{taskId}")
    suspend fun approve(
        @Path("taskId") taskId: Int,
        @Body body: ApproveTaskStageBody,
    ): Response<ApprovalResultResponse>

    /** 404 means there is no earlier step to send it back to. */
    @GET("api/workflow/task/{taskId}/reject-options")
    suspend fun rejectOptions(@Path("taskId") taskId: Int): Response<List<RejectOptionResponse>>

    @POST("api/workflow/reject/{taskId}")
    suspend fun reject(@Path("taskId") taskId: Int, @Body body: RejectTaskBody): Response<ApiResponse<Boolean>>

    @GET("api/form/v2/task/{taskId}/drafts")
    suspend fun drafts(@Path("taskId") taskId: Int): Response<List<TaskFormDraftResponse>>

    @GET("api/tasks/{taskId}/employee-closure-options")
    suspend fun employeeClosureOptions(@Path("taskId") taskId: Int): Response<EmployeeClosureOptionsResponse>

    @PUT("api/tasks/{taskId}/employee-closure-choice")
    suspend fun employeeClosureChoice(
        @Path("taskId") taskId: Int,
        @Body body: EmployeeClosureBody,
    ): Response<JsonElement>

    @GET("api/tasks/{taskId}/justifications/context")
    suspend fun lateContext(@Path("taskId") taskId: Int): Response<LateContextResponse>

    @POST("api/tasks/{taskId}/justifications")
    suspend fun justify(
        @Path("taskId") taskId: Int,
        @Body body: LateJustificationBody,
    ): Response<LateJustificationResult>

    @POST("api/tasks/{taskId}/justifications/{adjustmentId}/reviews")
    suspend fun reviewJustification(
        @Path("taskId") taskId: Int,
        @Path("adjustmentId") adjustmentId: Long,
        @Body body: LateReviewBody,
    ): Response<JsonElement>
}
