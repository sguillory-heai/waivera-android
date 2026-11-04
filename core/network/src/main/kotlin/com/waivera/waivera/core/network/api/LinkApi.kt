package com.waivera.waivera.core.network.api

import com.waivera.waivera.core.network.dto.FaqCategoryListResponse
import com.waivera.waivera.core.network.dto.ResourceChatPreviewResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * What a Waivera link in a chat message points at, read the way the web's link previews read it:
 * `Controllers/Wiki/HelpController.cs` (published FAQs for the reader) and
 * `Controllers/ResourceChatController.cs` (a published resource the reader may see).
 */
interface LinkApi {
    @GET("api/help/faqs")
    suspend fun getPublishedFaqs(): Response<List<FaqCategoryListResponse>>

    @GET("api/resource-directory/chat/{id}")
    suspend fun getResourcePreview(@Path("id") id: String): Response<ResourceChatPreviewResponse>
}
