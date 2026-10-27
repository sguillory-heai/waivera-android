package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.CalendarEmployeeMilestoneResponseDto
import com.waivera.waivera.core.network.dto.CalendarEventResponseDto
import com.waivera.waivera.core.network.dto.CalendarFeedResponseDto
import com.waivera.waivera.core.network.dto.InternalInvitationResponseReq
import com.waivera.waivera.core.network.dto.PublicInvitationResponseReq
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** `Controllers/Calendar/CalendarEventController.cs` and `CalendarInvitationController.cs` (rest.md §3). */
@Suppress("TooManyFunctions")
interface CalendarApi {
    /**
     * Instants are ISO-8601 UTC with `Z` ([com.waivera.waivera.core.network.time.WaiveraTime.formatQueryInstant]).
     * Pass `calendarStartDate` and `calendarEndDateExclusive` together or not at all.
     */
    @Suppress("LongParameterList")
    @GET("api/CalendarEvent/GetAllEvent")
    suspend fun getAllEvents(
        @Query("startTime") startTime: String?,
        @Query("endTime") endTime: String?,
        @Query("id") targetUserId: String? = null,
        @Query("includeEvents") includeEvents: Boolean = true,
        @Query("includeTimeOff") includeTimeOff: Boolean = true,
        @Query("calendarStartDate") calendarStartDate: String? = null,
        @Query("calendarEndDateExclusive") calendarEndDateExclusive: String? = null,
        @Query("includeManagedEvents") includeManagedEvents: Boolean = false,
    ): Response<ApiResponse<CalendarFeedResponseDto>>

    @GET("api/CalendarEvent/milestones")
    suspend fun getMilestones(): Response<ApiResponse<List<CalendarEmployeeMilestoneResponseDto>>>

    @GET("api/CalendarEvent/GetEventbyId/{id}")
    suspend fun getEventById(@Path("id") id: String): Response<ApiResponse<CalendarEventResponseDto>>

    @POST("api/CalendarEvent/CreateEvent")
    suspend fun createEvent(@Body event: JsonObject): Response<JsonElement>

    @PUT("api/CalendarEvent/UpdateEventbyId/{id}")
    suspend fun updateEvent(
        @Path("id") id: String,
        @Query("scope") scope: String?,
        @Body event: JsonObject,
    ): Response<JsonElement>

    @DELETE("api/CalendarEvent/DeleteEvent/{id}")
    suspend fun deleteEvent(@Path("id") id: String, @Query("deleteScope") deleteScope: String = "single"): Response<JsonElement>

    @GET("api/CalendarEvent/next-upcoming-events/{userId}")
    suspend fun getNextUpcomingEvents(@Path("userId") userId: String): Response<JsonElement>

    @GET("api/CalendarEvent/upcomming/{userId}/getCalendarEventDates")
    suspend fun getUpcomingEventDates(@Path("userId") userId: String): Response<JsonElement>

    @POST("api/CalendarEvent/upcoming/Events-by-date")
    suspend fun getEventsByDate(@Body request: JsonObject): Response<JsonElement>

    @GET("api/CalendarEvent/visibility-options")
    suspend fun getVisibilityOptions(): Response<JsonElement>

    @GET("api/CalendarEvent/attendee-options")
    suspend fun getAttendeeOptions(): Response<JsonElement>

    @GET("api/CalendarEvent/GetAllLeaves")
    suspend fun getAllLeaves(): Response<JsonElement>

    /** Alias of `api/invitation/respond`. `data == false` means the response was not saved. */
    @POST("api/invitation/AcceptDenyMeeting")
    suspend fun respondToInvitation(@Body request: InternalInvitationResponseReq): Response<ApiResponse<Boolean>>

    /** Token-based variant for external attendees; no auth. */
    @POST("api/public/invitation/respond")
    suspend fun respondToPublicInvitation(@Body request: PublicInvitationResponseReq): Response<JsonElement>
}
