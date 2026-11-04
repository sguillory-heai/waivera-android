package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskPage
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.data.repository.RequestRepository
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
    private val requests: RequestRepository,
) : TaskRepository {
    override suspend fun tasks(query: TaskQuery, page: Int): Result<TaskPage> = catchingWaivera {
        val me = me()
        if (query.scope == TaskScope.Casework) requests.ensureAccess()
        val response = list(me, query.scope, query.assignedToMe, query.search.trim(), query.window.dateFilter, page)
        TaskPage(items = response.tasks.map(TaskMapping::summary), totalCount = response.totalCount)
    }

    override suspend fun counts(scope: TaskScope, assignedToMe: Boolean): Result<TaskCounts> = catchingWaivera {
        val me = me()
        if (scope == TaskScope.Casework) requests.ensureAccess()
        coroutineScope {
            val open = async { list(me, scope, assignedToMe, "", null, 1, pageSize = 1).totalCount }
            val overdue = async {
                list(me, scope, assignedToMe, "", TaskWindow.Overdue.dateFilter, 1, pageSize = 1).totalCount
            }
            TaskCounts(open = open.await(), overdue = overdue.await())
        }
    }

    /**
     * Casework is the web's client task list: the server scopes it by role, with pending approvals
     * for the roles the web includes them for. My work is the person's own employee tasks.
     */
    @Suppress("LongParameterList")
    private suspend fun list(
        me: String,
        scope: TaskScope,
        assignedToMe: Boolean,
        search: String,
        date: List<String>?,
        page: Int,
        pageSize: Int = TaskApi.PAGE_SIZE,
    ) = apiCall {
        val casework = scope == TaskScope.Casework
        api.getMyTasks(
            userId = me,
            assignee = if (casework && !assignedToMe) null else me,
            taskContext = if (casework) CLIENT_CONTEXT else EMPLOYEE_CONTEXT,
            includePendingApprovals = if (casework) requests.access.value.includePendingApprovals else null,
            searchTerm = search,
            date = date,
            page = page,
            pageSize = pageSize,
        )
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

    private companion object {
        const val CLIENT_CONTEXT = "Client"
        const val EMPLOYEE_CONTEXT = "Employee"
    }
}

/** The server's `date` filter for each window; all open tasks need none. */
internal val TaskWindow.dateFilter: List<String>?
    get() = when (this) {
        TaskWindow.DueSoon -> listOf("month")
        TaskWindow.Overdue -> listOf("overdue")
        TaskWindow.AllOpen -> null
    }
