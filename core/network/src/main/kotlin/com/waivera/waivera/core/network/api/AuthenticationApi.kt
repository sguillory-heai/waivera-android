package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.AuthBootstrapRequest
import com.waivera.waivera.core.network.dto.AuthBootstrapResponse
import com.waivera.waivera.core.network.dto.ChangePasswordRequest
import com.waivera.waivera.core.network.dto.GetUserNameResponseDBVM
import com.waivera.waivera.core.network.dto.OrganizationDto
import com.waivera.waivera.core.network.dto.OrganizationResponse
import com.waivera.waivera.core.network.dto.PagedResponse
import com.waivera.waivera.core.network.dto.UpdateContactAndTimezoneRequest
import com.waivera.waivera.core.network.dto.UserInfoResponse
import com.waivera.waivera.core.network.dto.UserLoginResponseDto
import com.waivera.waivera.core.network.dto.UserWithRoleDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * `Controllers/Authentication.cs` and `Controllers/IdentityController.cs`. Endpoints whose
 * request or response DTO is not documented in rest.md use [JsonObject]/[JsonElement] until the
 * contract is pinned.
 *
 * Suppressed: Mirrors every route of the sprout_api controller.
 */
@Suppress("TooManyFunctions")
interface AuthenticationApi {
    /** Bootstrap after Firebase sign-in. Identity fields live under `data` (gaps.md G-02). */
    @GET("api/Authentication/login")
    suspend fun login(): Response<ApiResponse<UserLoginResponseDto>>

    /**
     * Roles and permissions, as the web loads them. `identityTransition` true leaves the sign-in
     * times alone: the phone calls this to re-check what to offer, not to sign in.
     */
    @POST("api/Authentication/bootstrap")
    suspend fun bootstrap(
        @Body request: AuthBootstrapRequest = AuthBootstrapRequest(identityTransition = true),
    ): Response<ApiResponse<AuthBootstrapResponse>>

    @GET("api/Authentication/GetUserDetailsByUserID/{userId}")
    suspend fun getUserDetails(@Path("userId") userId: String): Response<ApiResponse<UserWithRoleDto>>

    @PUT("api/Authentication/UpdateUser/{userId}")
    suspend fun updateUser(
        @Path("userId") userId: String,
        @Body request: JsonObject,
    ): Response<ApiResponse<UserWithRoleDto>>

    /** The only mobile-reachable writer of the employee's display zone. */
    @PUT("api/Authentication/{userId}/UpdateContactAndTimezone")
    suspend fun updateContactAndTimezone(
        @Path("userId") userId: String,
        @Body request: UpdateContactAndTimezoneRequest,
    ): Response<ApiResponse<String>>

    /**
     * Changes the password server-side (Firebase and `LastChangedPassword` together). 400 is the
     * password policy, 401 a sign-in that is not recent enough. Firebase then ends every
     * existing session, so the caller signs in again with the new password.
     */
    @POST("api/Authentication/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Unit>

    /** 404 when the list is empty. */
    @GET("api/Authentication/OrganizationDetails")
    suspend fun getOrganizationDetails(): Response<ApiResponse<List<OrganizationDto>>>

    /** Includes waiver participants; not used by the staff app (docs/chat-stability-round3). */
    @GET("api/Authentication/get-combined-user-list")
    suspend fun getCombinedUserList(): Response<ApiResponse<List<GetUserNameResponseDBVM>>>

    @POST("api/Authentication/GetAllUsers")
    suspend fun getAllUsers(@Body filter: JsonObject): Response<ApiResponse<PagedResponse<UserWithRoleDto>>>

    @GET("api/Authentication/employee-roles")
    suspend fun getEmployeeRoles(): Response<JsonElement>

    @GET("api/Authentication/{employeeId}/UserPerformance")
    suspend fun getUserPerformance(@Path("employeeId") employeeId: String): Response<JsonElement>

    /** Bare, not enveloped. 404 when the user has no organization. */
    @GET("api/users/organization")
    suspend fun getCurrentOrganization(): Response<OrganizationResponse>

    /**
     * The staff directory: users in the caller's own organization plus the caller's caseload
     * participants, the list the web chat uses. Bare, not enveloped. Never use
     * `get-username-list` for this; it returns every active user in every organization.
     */
    @GET("api/users")
    suspend fun getUsers(@Query("onlyActive") onlyActive: Boolean = true): Response<List<UserInfoResponse>>

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<JsonElement>
}
