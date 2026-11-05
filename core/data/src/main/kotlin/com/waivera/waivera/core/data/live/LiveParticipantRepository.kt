package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ParticipantProfile
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.ParticipantRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.api.ParticipantApi
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Singleton
class LiveParticipantRepository @Inject constructor(
    private val api: ParticipantApi,
    private val session: SessionRepository,
) : ParticipantRepository {
    override suspend fun profile(id: Int): Result<ParticipantProfile> = catchingWaivera {
        coroutineScope {
            // The pending flag is a nicety: the profile still shows when that check fails.
            val pending = async {
                val me = session.state.value.currentUserId ?: return@async false
                catchingWaivera { apiCall { api.changePending(me, id) }.data == true }.getOrDefault(false)
            }
            val info = apiCall { api.participant(id) }
            ParticipantMapping.profile(info, pending.await())
        }
    }

    override suspend fun openTasks(id: Int): Result<List<TaskSummary>> = catchingWaivera {
        // Pages come latest due first, so stopping at page one would drop the most overdue tasks.
        val all = mutableListOf<TaskSummary>()
        var page = 1
        do {
            val result = apiCall { api.tasks(id, page = page) }
            all += result.items.map(TaskMapping::summary)
            page++
        } while (result.items.isNotEmpty() && all.size < result.totalCount && page <= MAX_PAGES)
        // The server still returns closed tasks; they are not open.
        all.filterNot { it.state == TaskState.Closed || it.state == TaskState.Completed }
            .sortedWith(compareBy(nullsLast()) { it.due })
    }

    private companion object {
        const val MAX_PAGES = 10
    }
}
