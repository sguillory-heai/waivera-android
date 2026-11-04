package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskAttachment
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
        val delays = query.delays.serverValue.takeIf { query.scope == TaskScope.Casework }
        val response = list(
            me,
            query.scope,
            query.assignedToMe,
            query.search.trim(),
            query.window.dateFilter,
            page,
            delays = delays,
        )
        TaskPage(items = response.tasks.map(TaskMapping::summary), totalCount = response.totalCount)
    }

    override suspend fun counts(scope: TaskScope, assignedToMe: Boolean): Result<TaskCounts> = catchingWaivera {
        val me = me()
        if (scope == TaskScope.Casework) requests.ensureAccess()
        val casework = scope == TaskScope.Casework
        coroutineScope {
            val open = async { list(me, scope, assignedToMe, "", null, 1, pageSize = 1).totalCount }
            // A delayed task is not really overdue, so overdue leaves delays out as the web's to-dos do.
            val overdue = async {
                val delays = DelayFilter.Hide.serverValue.takeIf { casework }
                list(me, scope, assignedToMe, "", TaskWindow.Overdue.dateFilter, 1, pageSize = 1, delays).totalCount
            }
            val delayed = if (casework) {
                async {
                    list(me, scope, assignedToMe, "", null, 1, pageSize = 1, DelayFilter.Only.serverValue).totalCount
                }
            } else {
                null
            }
            TaskCounts(open = open.await(), overdue = overdue.await(), delayed = delayed?.await() ?: 0)
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
        delays: String? = null,
    ) = apiCall {
        val casework = scope == TaskScope.Casework
        api.getMyTasks(
            userId = me,
            assignee = if (casework && !assignedToMe) null else me,
            taskContext = if (casework) CLIENT_CONTEXT else EMPLOYEE_CONTEXT,
            includePendingApprovals = if (casework) requests.access.value.includePendingApprovals else null,
            searchTerm = search,
            date = date,
            taskDelayFilter = delays,
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

    override suspend fun attachments(taskId: Int): Result<List<TaskAttachment>> = catchingWaivera {
        TaskMapping.attachments(apiCall { api.getAttachments(taskId) })
    }

    override suspend fun activityTypes(): Result<List<ActivityType>> = catchingWaivera {
        val types = apiCall { api.getActivityTypes() }
        // Without templates a file still goes up under its own name, as on the web.
        val templates = if (types.any { (it.requiresFile ?: 0) > 0 }) {
            catchingWaivera { apiCall { api.getTemplateFiles() } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        TaskMapping.activityTypes(types, templates)
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

/** The server's `taskDelayFilter`; [DelayFilter.Show] sends none. */
internal val DelayFilter.serverValue: String?
    get() = when (this) {
        DelayFilter.Show -> null
        DelayFilter.Hide -> "without-delays"
        DelayFilter.Only -> "delayed"
    }
