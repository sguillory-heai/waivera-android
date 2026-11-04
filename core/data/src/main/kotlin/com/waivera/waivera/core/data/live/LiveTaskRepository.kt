package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskPage
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.network.dto.ApiResponse
import com.waivera.waivera.core.network.dto.TaskActionRequest
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.error.apiCall
import com.waivera.waivera.core.network.error.catchingWaivera
import com.waivera.waivera.core.network.time.WaiveraTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Reads tasks straight from `api/tasks`; nothing is cached, so the web and the phone never disagree. */
@Singleton
class LiveTaskRepository @Inject constructor(
    private val api: TaskApi,
    private val session: SessionRepository,
    private val json: Json,
) : TaskRepository {
    override suspend fun myTasks(window: TaskWindow, search: String, page: Int): Result<TaskPage> = catchingWaivera {
        val me = me()
        val response = apiCall {
            api.getMyTasks(
                userId = me,
                assignee = me,
                searchTerm = search.trim(),
                date = window.dateFilter,
                page = page,
            )
        }
        TaskPage(items = response.tasks.map(TaskMapping::summary), totalCount = response.totalCount)
    }

    override suspend fun counts(): Result<TaskCounts> = catchingWaivera {
        val me = me()
        coroutineScope {
            val open = async { apiCall { api.getMyTasks(userId = me, assignee = me, pageSize = 1) }.totalCount }
            val overdue = async {
                apiCall {
                    api.getMyTasks(userId = me, assignee = me, date = TaskWindow.Overdue.dateFilter, pageSize = 1)
                }
                    .totalCount
            }
            TaskCounts(open = open.await(), overdue = overdue.await())
        }
    }

    override suspend fun task(id: Int): Result<TaskDetail> = catchingWaivera {
        TaskMapping.detail(apiCall { api.getTask(id) })
    }

    override suspend fun activity(taskId: Int): Result<List<TaskActivity>> = catchingWaivera {
        TaskMapping.activity(apiCall { api.getActivity(taskId) })
    }

    override suspend fun activityTypes(): Result<List<ActivityType>> = catchingWaivera {
        TaskMapping.activityTypes(apiCall { api.getActivityTypes() })
    }

    override suspend fun logActivity(activity: NewActivity): Result<Unit> = catchingWaivera {
        val body = apiCall {
            api.logActivity(
                TaskActionRequest(
                    taskId = activity.taskId,
                    description = activity.description.trim(),
                    subTypeId = activity.typeId,
                    durationInMinutes = activity.minutes,
                    completionTime = WaiveraTime.formatQueryInstant(activity.completedAt),
                ),
            )
        }
        // The envelope can say no inside a 200; its own message is never shown.
        if (!succeeded(body)) throw WaiveraError.Unexpected()
    }

    private fun succeeded(body: JsonElement): Boolean {
        if (body !is JsonObject || !body.containsKey("success")) return true
        return runCatching { json.decodeFromJsonElement(ApiResponse.serializer(JsonElement.serializer()), body) }
            .getOrNull()?.success != false
    }

    private fun me(): String = session.state.value.currentUserId ?: throw WaiveraError.NotSignedIn()
}

/** The server's `date` filter for each window; all open tasks need none. */
internal val TaskWindow.dateFilter: List<String>?
    get() = when (this) {
        TaskWindow.DueSoon -> listOf("month")
        TaskWindow.Overdue -> listOf("overdue")
        TaskWindow.AllOpen -> null
    }
