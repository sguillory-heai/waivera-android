package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.ChatActivityResponse
import com.waivera.waivera.core.network.dto.ChatReadStateDto
import com.waivera.waivera.core.network.dto.ChatReadThroughRequest
import com.waivera.waivera.core.network.dto.ConversationResponse
import com.waivera.waivera.core.network.dto.CreateConversationRequest
import com.waivera.waivera.core.network.dto.EditMessageRequest
import com.waivera.waivera.core.network.dto.MessageResponse
import com.waivera.waivera.core.network.dto.MessageSendResponse
import com.waivera.waivera.core.network.dto.SendMessageRequest
import com.waivera.waivera.core.network.dto.UnifiedConversationResponse
import com.waivera.waivera.core.network.dto.UploadAttachmentResponse
import com.waivera.waivera.core.network.dto.UserInfoResponse
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** `Controllers/Chat/ChatController.cs` and `ChatSearchController.cs` (rest.md §2). */
@Suppress("TooManyFunctions")
interface ChatApi {
    /** DMs and groups only (no channels); `lastMessage` has no attachment fields (gaps.md G-05). */
    @GET("api/chat/my-conversations")
    suspend fun getMyConversations(
        @Query("includeMeetings") includeMeetings: Boolean,
    ): Response<List<ConversationResponse>>

    /** Includes channels. Always send both flags: `includeArchived` defaults to true server-side (G-13). */
    @GET("api/chat/all")
    suspend fun getAllConversations(
        @Query("includeMeetings") includeMeetings: Boolean,
        @Query("includeArchived") includeArchived: Boolean,
    ): Response<ApiResponse<List<UnifiedConversationResponse>>>

    /** `unreadCount` is always 0 here; never trust it (G-14). */
    @GET("api/chat/conversation/{conversationId}")
    suspend fun getConversation(@Path("conversationId") conversationId: Long): Response<ConversationResponse>

    /** Legacy web endpoint: returns deleted bodies verbatim (G-11). Never call it from the app. */
    @GET("api/chat/{conversationId}/messages")
    suspend fun getAllMessagesLegacy(@Path("conversationId") conversationId: Long): Response<List<MessageSendResponse>>

    /** Oldest-first bare array; "has more" is inferred from the page size (G-06). */
    @GET("api/chat/{conversationId}/messages/pagination")
    suspend fun getMessagesPage(
        @Path("conversationId") conversationId: Long,
        @Query("pageSize") pageSize: Int,
        @Query("beforeMessageId") beforeMessageId: Long?,
    ): Response<List<MessageSendResponse>>

    /** `before`/`after` are clamped 0..75 server-side. */
    @GET("api/chat/{conversationId}/messages/context/{messageId}")
    suspend fun getMessageContext(
        @Path("conversationId") conversationId: Long,
        @Path("messageId") messageId: Long,
        @Query("before") before: Int,
        @Query("after") after: Int,
    ): Response<List<MessageSendResponse>>

    /** Idempotent on `clientMessageId`. Bare response. */
    @POST("api/chat/send")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<MessageSendResponse>

    @POST("api/chat/create-conversation")
    suspend fun createConversation(
        @Body request: CreateConversationRequest,
    ): Response<ApiResponse<ConversationResponse>>

    /** Empty 200 body. */
    @POST("api/chat/{conversationId}/mark-as-read")
    suspend fun markAsRead(@Path("conversationId") conversationId: Long): Response<Unit>

    @PUT("api/chat/{conversationId}/read-through")
    suspend fun readThrough(
        @Path("conversationId") conversationId: Long,
        @Body request: ChatReadThroughRequest,
    ): Response<ChatReadStateDto>

    /** `pageSize` is clamped 1..100 server-side. No total count. */
    @GET("api/chat/recent-activity")
    suspend fun getRecentActivity(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
    ): Response<List<ChatActivityResponse>>

    @PUT("api/chat/recent-activity/{activityId}/read")
    suspend fun markActivityRead(@Path("activityId") activityId: Long): Response<Unit>

    @PUT("api/chat/recent-activity/message/{messageId}/read")
    suspend fun markMessageActivityRead(@Path("messageId") messageId: Long): Response<Unit>

    @PUT("api/chat/recent-activity/read-all")
    suspend fun markAllActivityRead(): Response<Unit>

    /** Ascending, including the root message itself. */
    @GET("api/chat/thread/{threadRootMessageId}")
    suspend fun getThread(@Path("threadRootMessageId") threadRootMessageId: Long): Response<List<MessageSendResponse>>

    @PUT("api/chat/message/{messageId}")
    suspend fun editMessage(
        @Path("messageId") messageId: Long,
        @Body request: EditMessageRequest,
    ): Response<ApiResponse<MessageResponse>>

    @DELETE("api/chat/message/{messageId}")
    suspend fun deleteMessage(@Path("messageId") messageId: Long): Response<ApiResponse<MessageResponse>>

    /** Toggle; `data` is the new bookmarked state. */
    @POST("api/chat/message/{messageId}/bookmark")
    suspend fun toggleBookmark(@Path("messageId") messageId: Long): Response<ApiResponse<Boolean>>

    /** Returns 500 for legacy bot rows (G-04); callers fall back to per-conversation search. */
    @GET("api/chat/bookmarks")
    suspend fun getBookmarks(): Response<ApiResponse<List<MessageResponse>>>

    @GET("api/chat/conversation/{conversationId}/bookmarks")
    suspend fun getConversationBookmarks(
        @Path("conversationId") conversationId: Long,
    ): Response<ApiResponse<List<MessageResponse>>>

    /** `query` must be at least 2 characters when present. */
    @Suppress("LongParameterList")
    @GET("api/chat/search/conversation/{conversationId}")
    suspend fun searchConversation(
        @Path("conversationId") conversationId: Long,
        @Query("query") query: String? = null,
        @Query("authorId") authorId: String? = null,
        @Query("filesOnly") filesOnly: Boolean = false,
        @Query("bookmarkedOnly") bookmarkedOnly: Boolean = false,
        @Query("includeThreads") includeThreads: Boolean = true,
        @Query("pageIndex") pageIndex: Int = 1,
        @Query("pageSize") pageSize: Int = SEARCH_PAGE_SIZE,
    ): Response<ApiResponse<List<MessageSendResponse>>>

    @GET("api/chat/search/conversation/{conversationId}/authors")
    suspend fun searchConversationAuthors(@Path("conversationId") conversationId: Long): Response<JsonElement>

    @GET("api/chat/search/messages")
    suspend fun searchMessages(
        @Query("query") query: String,
        @Query("pageIndex") pageIndex: Int = 1,
        @Query("pageSize") pageSize: Int = GLOBAL_SEARCH_PAGE_SIZE,
    ): Response<JsonElement>

    /** Single multipart part named exactly `file` (pitfalls §33). */
    @Multipart
    @POST("api/chat/upload-attachment")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): Response<UploadAttachmentResponse>

    /** Binary download; 423 when the scan rejected the file (G-15). */
    @Streaming
    @GET("api/chat/message/{messageId}/attachment")
    suspend fun downloadAttachment(@Path("messageId") messageId: Long): Response<ResponseBody>

    /** Only `id` and `participants` are filled. A 200 without `data` is a failure (G-12). */
    @GET("api/chat/privatechats/{targetUserId}/conversation")
    suspend fun getPrivateConversation(
        @Path("targetUserId") targetUserId: String,
    ): Response<ApiResponse<ConversationResponse>>

    @GET("api/chat/privatechats/{targetUserId}/messages")
    suspend fun getPrivateMessages(
        @Path("targetUserId") targetUserId: String,
    ): Response<ApiResponse<List<MessageResponse>>>

    /** 403 for every failure mode, deliberately. */
    @GET("api/chat/getorcreate/conversationforEvent/{calendarEventId}")
    suspend fun getOrCreateConversationForEvent(
        @Path("calendarEventId") calendarEventId: String,
    ): Response<ApiResponse<ConversationResponse>>

    /** "Leave conversation" from the client's point of view. */
    @DELETE("api/chat/delete/conversation/{conversationId}")
    suspend fun leaveConversation(@Path("conversationId") conversationId: Long): Response<ApiResponse<String>>

    /** Trusts the path user id (G-16); pass only the signed-in user's id. */
    @GET("api/chat/user/{userId}/get-myChannels")
    suspend fun getMyChannels(@Path("userId") userId: String): Response<ApiResponse<List<ConversationResponse>>>

    /** SUPER ADMIN only; not for ordinary staff builds. */
    @GET("api/chat/initiation-directory")
    suspend fun getInitiationDirectory(): Response<List<UserInfoResponse>>

    companion object {
        const val MESSAGE_PAGE_SIZE = 50
        const val ACTIVITY_PAGE_SIZE = 30
        const val SEARCH_PAGE_SIZE = 20
        const val GLOBAL_SEARCH_PAGE_SIZE = 30
        const val CONTEXT_WINDOW = 30
    }
}
