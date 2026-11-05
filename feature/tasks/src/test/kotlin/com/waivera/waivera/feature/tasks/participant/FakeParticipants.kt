package com.waivera.waivera.feature.tasks.participant

import com.waivera.waivera.core.data.model.ParticipantProfile
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.repository.ParticipantRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred

class FakeParticipants : ParticipantRepository {
    var profile: Result<ParticipantProfile> = Result.success(profile())
    var tasks: Result<List<TaskSummary>> = Result.success(emptyList())
    val asked = mutableListOf<Int>()
    var taskLoads = 0

    /** When set, a profile load waits for it, so tests can see the loading state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun profile(id: Int): Result<ParticipantProfile> {
        asked += id
        gate?.await()
        return profile
    }

    override suspend fun openTasks(id: Int): Result<List<TaskSummary>> {
        taskLoads++
        return tasks
    }

    companion object {
        fun profile(planEnd: LocalDate? = LocalDate.of(2027, 3, 31)) = ParticipantProfile(
            id = 501,
            name = "Jordan Ellis",
            initials = "JE",
            waiver = "Community Choices",
            region = "Region 4",
            mobile = "(555) 010-2231",
            email = "jordan@example.com",
            planStart = LocalDate.of(2026, 4, 1),
            planEnd = planEnd,
            certification = LocalDate.of(2026, 3, 15),
            linkage = LocalDate.of(2025, 2, 10),
            birthDate = LocalDate.of(1988, 6, 2),
            intake = "Transition",
            deactivated = false,
            compliant = true,
        )

        fun summary(id: Int, due: LocalDate?) = TaskSummary(id, "HEA-$id", "Task $id", due, TaskState.NotStarted, null)
    }
}
