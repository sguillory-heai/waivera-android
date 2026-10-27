package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.CreateMeetingRoomRequest
import com.waivera.waivera.core.network.dto.CreateMeetingRoomResponse
import com.waivera.waivera.core.network.dto.MobilePushRegistrationRequest
import com.waivera.waivera.core.network.dto.MobilePushResultDto
import com.waivera.waivera.core.network.dto.MobilePushUnregisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** `Controllers/VideoController.cs`; the app only hands calls off to the web lobby. */
interface VideoApi {
    @POST("api/video/createmeetingroom")
    suspend fun createMeetingRoom(
        @Body request: CreateMeetingRoomRequest,
    ): Response<ApiResponse<CreateMeetingRoomResponse>>

    @POST("api/video/token")
    suspend fun getVideoToken(): Response<ApiResponse<String>>
}

/** Mirrors sprout_api `MobilePushController` (`api/mobile-push`). */
interface MobilePushApi {
    @POST("api/mobile-push/register")
    suspend fun register(@Body request: MobilePushRegistrationRequest): Response<MobilePushResultDto>

    @POST("api/mobile-push/unregister")
    suspend fun unregister(@Body request: MobilePushUnregisterRequest): Response<MobilePushResultDto>
}
