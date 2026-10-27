package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.Base64AvatarResultDto
import com.waivera.waivera.core.network.dto.EmployeeFilesPageDto
import com.waivera.waivera.core.network.dto.GetEmployeeProfileDto
import com.waivera.waivera.core.network.dto.TimeOffProfileDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** `Controllers/EmployeeController.cs` (rest.md §4). */
interface EmployeeApi {
    @GET("api/employee/getProfile/{employeeId}")
    suspend fun getProfile(@Path("employeeId") employeeId: String): Response<ApiResponse<GetEmployeeProfileDto>>

    @Suppress("LongParameterList")
    @GET("api/employee/get-employee-files")
    suspend fun getEmployeeFiles(
        @Query("userId") userId: String,
        @Query("pageNumber") pageNumber: Int,
        @Query("pageSize") pageSize: Int,
        @Query("searchText") searchText: String? = null,
        @Query("sortBy") sortBy: String = "DateAdded",
        @Query("sortDirection") sortDirection: String = "desc",
    ): Response<ApiResponse<EmployeeFilesPageDto>>

    @GET("api/employee/{targetUserId}")
    suspend fun getEmployee(@Path("targetUserId") targetUserId: String): Response<JsonElement>

    @GET("api/employee/coworker-profile/{employeeId}")
    suspend fun getCoworkerProfile(@Path("employeeId") employeeId: String): Response<JsonElement>

    @GET("api/employee/{employeeId}/aboutme")
    suspend fun getAboutMe(@Path("employeeId") employeeId: String): Response<JsonElement>

    @GET("api/employee/managed-employees")
    suspend fun getManagedEmployees(): Response<JsonElement>

    @Streaming
    @GET("api/employee/employeefile/download/{fileId}")
    suspend fun downloadEmployeeFile(@Path("fileId") fileId: String): Response<ResponseBody>
}

/** `Controllers/TimeOffController.cs` (route prefix `api/TimeOff`). */
interface TimeOffApi {
    @GET("api/TimeOff/get-timeOffs-for-user-profile/{userId}")
    suspend fun getTimeOffForProfile(@Path("userId") userId: String): Response<ApiResponse<List<TimeOffProfileDto>>>

    @POST("api/TimeOff/add-update-timeOff")
    suspend fun addOrUpdateTimeOff(@Body request: JsonObject): Response<JsonElement>

    /** Route shape is not documented in rest.md; confirm against TimeOffController before use. */
    @DELETE("api/TimeOff/{id}")
    suspend fun deleteTimeOff(@Path("id") id: Long): Response<JsonElement>

    @GET("api/TimeOff/get-timeOffs-by-user")
    suspend fun getTimeOffByUser(): Response<JsonElement>

    @GET("api/TimeOff/get-timeOff-by-id/{id}")
    suspend fun getTimeOffById(@Path("id") id: Long): Response<JsonElement>

    @GET("api/TimeOff/request-history")
    suspend fun getRequestHistory(): Response<JsonElement>
}

/** `Controllers/FileController.cs`. */
interface FileApi {
    /** `base64Image` is a data URI; 404 (bare text) when the user has no avatar. */
    @GET("api/file/avatar/{userId}")
    suspend fun getAvatar(@Path("userId") userId: String): Response<Base64AvatarResultDto>

    /** SAS URLs for the whole organization: `[{ id, avatarUrl }]`. */
    @GET("api/file/avatar")
    suspend fun getOrganizationAvatars(): Response<JsonElement>

    /** Returns a plain string. */
    @Multipart
    @POST("api/file/upload/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): Response<ResponseBody>
}
