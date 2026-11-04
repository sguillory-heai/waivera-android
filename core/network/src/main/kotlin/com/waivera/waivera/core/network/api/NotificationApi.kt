package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.EventNotificationResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * `Controllers/NotificationController.cs` (`api/notifications`): the stored notifications the
 * web bell shows. The list is a bare array, newest first; the counts and writes are enveloped.
 */
interface NotificationApi {
    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = PAGE_SIZE,
    ): Response<List<EventNotificationResponse>>

    @GET("api/notifications/unread-count")
    suspend fun getUnreadCount(): Response<ApiResponse<Int>>

    @PUT("api/notifications/{id}")
    suspend fun markRead(@Path("id") id: String): Response<JsonElement>

    @PUT("api/notifications/mark-all-read")
    suspend fun markAllRead(): Response<JsonElement>

    companion object {
        const val PAGE_SIZE = 30
    }
}
