package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.LinkedFaq
import com.waivera.waivera.core.data.model.LinkedResource
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.repository.LinkLookupRepository
import com.waivera.waivera.core.network.api.LinkApi
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import javax.inject.Inject

/** Live [LinkLookupRepository]; the same reads the web's chat link previews make. */
class LiveLinkLookupRepository @Inject constructor(private val tasks: TaskApi, private val links: LinkApi) :
    LinkLookupRepository {
    override suspend fun task(number: Int): Result<TaskDetail> = catchingWaivera {
        TaskMapping.detail(apiCall { tasks.lookupTask(number) })
    }

    override suspend fun resource(id: String): Result<LinkedResource> = catchingWaivera {
        val dto = apiCall { links.getResourcePreview(id) }
        val title = dto.title?.trim()?.takeIf(String::isNotEmpty) ?: throw WaiveraError.NotFound()
        LinkedResource(
            id = id,
            title = title,
            provider = dto.providerName?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) },
            city = dto.city?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    override suspend fun faqs(): Result<List<LinkedFaq>> = catchingWaivera {
        apiCall { links.getPublishedFaqs() }.flatMap { it.articles }.mapNotNull { article ->
            val id = article.id?.lowercase() ?: return@mapNotNull null
            val title = article.title?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            LinkedFaq(
                id = id,
                slug = article.slug.orEmpty(),
                title = title,
                summary = article.summary?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }
}
