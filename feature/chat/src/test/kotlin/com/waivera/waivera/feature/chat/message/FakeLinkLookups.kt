package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.LinkedFaq
import com.waivera.waivera.core.data.model.LinkedResource
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.repository.LinkLookupRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError

/** Link lookups from fixed data; anything not listed is not found. Counts each read. */
class FakeLinkLookups(
    var tasks: Map<Int, TaskDetail> = emptyMap(),
    var resources: Map<String, LinkedResource> = emptyMap(),
    var faqs: Result<List<LinkedFaq>> = Result.success(emptyList()),
) : LinkLookupRepository {
    val reads = mutableListOf<String>()

    override suspend fun task(number: Int): Result<TaskDetail> {
        reads += "task:$number"
        return tasks[number]?.let { Result.success(it) } ?: Result.failure(WaiveraError.NotFound())
    }

    override suspend fun resource(id: String): Result<LinkedResource> {
        reads += "resource:$id"
        return resources[id]?.let { Result.success(it) } ?: Result.failure(WaiveraError.Forbidden())
    }

    override suspend fun faqs(): Result<List<LinkedFaq>> {
        reads += "faqs"
        return faqs
    }

    companion object {
        const val WEB = "https://app.waivera.test"
        val Config = NetworkConfig(apiBaseUrl = "https://api.waivera.test", webBaseUrl = WEB)

        fun resolver(lookups: LinkLookupRepository = FakeLinkLookups()) = ChatLinkResolver(lookups, Config)
    }
}
