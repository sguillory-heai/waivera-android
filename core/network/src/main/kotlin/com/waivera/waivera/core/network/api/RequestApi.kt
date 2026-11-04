package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.AdminRequestResponse
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.ComplianceEligibilityResponse
import com.waivera.waivera.core.network.dto.ComplianceReasonResponse
import com.waivera.waivera.core.network.dto.PagedResults
import com.waivera.waivera.core.network.dto.TaskAttachmentResponse
import com.waivera.waivera.core.network.dto.TaskClosureRequest
import com.waivera.waivera.core.network.dto.TaskDelayRequest
import com.waivera.waivera.core.network.dto.TaskDelayResult
import com.waivera.waivera.core.network.dto.TaskExtensionRequest
import com.waivera.waivera.core.network.dto.TaskExtensionResult
import com.waivera.waivera.core.network.dto.TransactionReasonResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Change requests on tasks and their review: `Controllers/AdmininistrativeTransactionController.cs`,
 * `Controllers/Compliance/TaskExtensionController.cs`, `TaskDelayController.cs`,
 * `TaskComplianceRequestEligibilityController.cs`, `ComplianceConfigurationController.cs` (reasons),
 * `LookupValuesController.cs` and `TaskController.cs` (`request-close`, attachments). The server
 * scopes every list to what the caller may see and re-checks every write.
 */
@Suppress("TooManyFunctions")
interface RequestApi {
    @GET("api/admin/request/self")
    suspend fun mine(
        @Query("PageNumber") page: Int = 1,
        @Query("PageSize") pageSize: Int = PAGE_SIZE,
        @Query("NeedTimeOffRequests") timeOff: Boolean = false,
    ): Response<ApiResponse<PagedResults<AdminRequestResponse>>>

    /** Pending requests this person may act on; an empty type name asks for every kind. */
    @GET("api/admin/request/pending-approvals")
    suspend fun waiting(
        @Query("pageNumber") page: Int = 1,
        @Query("pageSize") pageSize: Int = PAGE_SIZE,
        @Query("transactionalTypeName") type: String = "",
    ): Response<ApiResponse<PagedResults<AdminRequestResponse>>>

    @PUT("api/admin/request/approve/{id}")
    suspend fun approve(@Path("id") id: Int): Response<JsonElement>

    @PUT("api/admin/request/reject/{id}")
    suspend fun reject(@Path("id") id: Int): Response<JsonElement>

    @GET("api/tasks/{taskId}/compliance-request-eligibility")
    suspend fun eligibility(@Path("taskId") taskId: Int): Response<ComplianceEligibilityResponse>

    @GET("api/settings/organization/compliance/reasons")
    suspend fun complianceReasons(): Response<List<ComplianceReasonResponse>>

    @GET("api/lookup/reasons")
    suspend fun closureReasons(@Query("type") type: String = "CLOSURE"): Response<List<TransactionReasonResponse>>

    @GET("api/tasks/attachments/{taskId}")
    suspend fun attachments(@Path("taskId") taskId: Int): Response<List<TaskAttachmentResponse>>

    @POST("api/tasks/{taskId}/extensions")
    suspend fun askMoreTime(
        @Path("taskId") taskId: Int,
        @Body body: TaskExtensionRequest,
    ): Response<TaskExtensionResult>

    @POST("api/tasks/{taskId}/delays")
    suspend fun reportDelay(
        @Path("taskId") taskId: Int,
        @Body body: TaskDelayRequest,
    ): Response<ApiResponse<TaskDelayResult>>

    @POST("api/tasks/{taskId}/request-close")
    suspend fun askClosure(@Path("taskId") taskId: Int, @Body body: TaskClosureRequest): Response<JsonElement>

    companion object {
        const val PAGE_SIZE = 50
    }
}
