package com.waivera.waivera.core.demo

import com.waivera.waivera.core.data.model.ParticipantProfile
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.repository.ParticipantRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The demo agency's participants, with plan dates relative to the demo day. */
@Singleton
class DemoParticipantRepository @Inject constructor(private val backend: DemoBackend) : ParticipantRepository {
    private val today: LocalDate get() = backend.now().atZone(DemoData.ZONE).toLocalDate()

    override suspend fun profile(id: Int): Result<ParticipantProfile> {
        backend.failRefreshes?.let { return Result.failure(it) }
        return DemoParticipants.profile(id, today)?.let { Result.success(it) }
            ?: Result.failure(WaiveraError.NotFound())
    }

    override suspend fun openTasks(id: Int): Result<List<TaskSummary>> {
        backend.failRefreshes?.let { return Result.failure(it) }
        return Result.success(
            DemoTasks.casework(today)
                .filter { it.participantId == id }
                .sortedByDescending { it.due }
                .map(DemoTasks::summary),
        )
    }
}

/** Names the demo tasks are about, and their profiles. */
object DemoParticipants {
    private val ids = mapOf(
        "Jordan Ellis" to 501,
        "Maya Thompson" to 502,
        "Luis Ortega" to 503,
        "Ana Ruiz" to 504,
        "Sam Lee" to 505,
    )

    fun idOf(name: String): Int? = ids[name]

    @Suppress("MagicNumber") // Sample dates.
    fun profile(id: Int, today: LocalDate): ParticipantProfile? {
        val name = ids.entries.firstOrNull { it.value == id }?.key ?: return null
        val parts = name.split(" ")
        val planStart = today.minusMonths(9).withDayOfMonth(1)
        return ParticipantProfile(
            id = id,
            name = name,
            initials = parts.mapNotNull { it.firstOrNull() }.joinToString(""),
            waiver = if (id % 2 == 0) "Community Choices Waiver" else "ROW Waiver",
            region = "Region 4 · Acadiana",
            mobile = "(337) 555-01" + (id % 100).toString().padStart(2, '0'),
            email = name.lowercase().replace(' ', '.') + "@example.com",
            planStart = planStart,
            planEnd = planStart.plusYears(1).minusDays(1),
            certification = planStart.minusMonths(1).plusDays(14),
            linkage = LocalDate.of(2024, 3, 3),
            birthDate = LocalDate.of(1987, 7, id % 28 + 1),
            intake = "Initial",
            deactivated = false,
            compliant = id != 501,
            changePending = id == 503,
        )
    }
}
