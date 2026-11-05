package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.ParticipantInfoResponse
import com.waivera.waivera.core.network.dto.ParticipantTasksResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** The read-only participant profile (web Participant Profile tab and Recent Tasks). */
interface ParticipantApi {
    /** 403 unless the caller may view this participant (`participant:view` or legacy `view:participant`). */
    @GET("api/participants/{id}")
    suspend fun participant(@Path("id") id: Int): Response<ParticipantInfoResponse>

    /**
     * Tasks about the participant, latest due first, one page at a time. Without `viewAll` the server
     * leaves out completed and deleted tasks but still returns closed ones.
     */
    @GET("api/tasks/participant/{id}")
    suspend fun tasks(
        @Path("id") id: Int,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = PAGE_SIZE,
        @Query("viewAll") viewAll: Boolean = false,
    ): Response<ParticipantTasksResponse>

    /** Whether the caller's own profile change for this participant waits on approval (web "Update Pending"). */
    @GET("api/employee/{userId}/{participantId}/profile-change/last/is-Pending")
    suspend fun changePending(
        @Path("userId") userId: String,
        @Path("participantId") participantId: Int,
    ): Response<ApiResponse<Boolean>>

    companion object {
        const val PAGE_SIZE = 50
    }
}
