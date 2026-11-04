package com.waivera.waivera.core.network.dto

import kotlinx.serialization.Serializable

/*
 * Mirrors of what chat link previews read: published FAQs (`api/help/faqs`) and a resource's
 * chat preview (`api/resource-directory/chat/{id}`). Tasks reuse [TaskResponse] from
 * `api/tasks/lookup/{friendlyId}`.
 */

/** `dto/Response/Wiki/FaqResponse.cs` `FaqCategoryListResponse`. */
@Serializable
data class FaqCategoryListResponse(
    val id: String? = null,
    val name: String? = null,
    val articles: List<FaqListArticleResponse> = emptyList(),
)

/** `dto/Response/Wiki/FaqResponse.cs` `FaqListArticleResponse`. */
@Serializable
data class FaqListArticleResponse(
    val id: String? = null,
    val title: String? = null,
    val slug: String? = null,
    val summary: String? = null,
)

/** The anonymous object `ResourceChatController.Preview` returns; only published resources. */
@Serializable
data class ResourceChatPreviewResponse(
    val id: String? = null,
    val title: String? = null,
    val providerName: String? = null,
    val city: String? = null,
    val stateCodes: List<String> = emptyList(),
    val phone: String? = null,
)
