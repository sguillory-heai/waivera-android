package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.model.LinkedFaq
import com.waivera.waivera.core.data.model.LinkedResource
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.repository.LinkLookupRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Demo link previews: the demo tasks by number, one resource and one FAQ article. */
@Singleton
class DemoLinkLookupRepository @Inject constructor(private val backend: DemoBackend) : LinkLookupRepository {
    private val today: LocalDate get() = backend.now().atZone(DemoData.ZONE).toLocalDate()

    override suspend fun task(number: Int): Result<TaskDetail> = (DemoTasks.casework(today) + DemoTasks.employee(today))
        .firstOrNull { it.key.substringAfterLast('-') == number.toString() }
        ?.let { Result.success(it) }
        ?: Result.failure(WaiveraError.NotFound())

    override suspend fun resource(id: String): Result<LinkedResource> = if (id == DemoData.RESOURCE_ID) {
        Result.success(LinkedResource(id, "Lakeview Respite Care", provider = null, city = "Baton Rouge"))
    } else {
        Result.failure(WaiveraError.NotFound())
    }

    override suspend fun faqs(): Result<List<LinkedFaq>> = Result.success(
        listOf(
            LinkedFaq(
                id = DemoData.FAQ_ID,
                slug = "submitting-mileage",
                title = "Submitting mileage",
                summary = "Log each trip the day you drive it, with the start and end address.",
            ),
        ),
    )
}
