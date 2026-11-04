package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.LiveTaskRepository
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.testing.FixtureServer
import com.waivera.waivera.core.testing.TestSessionRepository
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.RecordedRequest

/** The task list's delay rules as the server sees them: query parameters and the header counts. */
class TaskRepositoryTest {
    private val server = FixtureServer()

    @AfterTest
    fun tearDown() = server.close()

    private val repository = LiveTaskRepository(
        server.service<TaskApi>(),
        TestSessionRepository(),
        WaiveraJson,
        loadedAccess(),
    )

    private fun RecordedRequest.param(name: String): String? = url.queryParameter(name)

    @Test
    fun casework_sends_the_delays_choice_and_my_work_never_does() = runTest {
        server.json("GET /api/tasks/user/*", """{"tasks":[],"totalCount":0}""")
        repository.tasks(TaskQuery(TaskScope.Casework, TaskWindow.AllOpen, delays = DelayFilter.Hide), 1).getOrThrow()
        repository.tasks(TaskQuery(TaskScope.Casework, TaskWindow.AllOpen, delays = DelayFilter.Only), 1).getOrThrow()
        repository.tasks(TaskQuery(TaskScope.Casework, TaskWindow.AllOpen), 1).getOrThrow()
        repository.tasks(TaskQuery(TaskScope.Mine, TaskWindow.AllOpen, delays = DelayFilter.Only), 1).getOrThrow()
        val sent = server.requests.map { it.param("taskDelayFilter") }
        assertEquals(listOf("without-delays", "delayed", null, null), sent)
    }

    @Test
    fun overdue_leaves_delays_out_and_delayed_is_counted_apart() = runTest {
        server.route("GET /api/tasks/user/*") { request ->
            val total = when {
                request.param("taskDelayFilter") == "delayed" -> 2
                request.param("date") == "overdue" -> 3
                else -> 14
            }
            FixtureServer.json("""{"tasks":[],"totalCount":$total}""")
        }
        val counts = repository.counts(TaskScope.Casework, assignedToMe = false).getOrThrow()
        assertEquals(14, counts.open)
        assertEquals(3, counts.overdue)
        assertEquals(2, counts.delayed)
        val overdueCall = server.requests.single { it.param("date") == "overdue" }
        assertEquals("without-delays", overdueCall.param("taskDelayFilter"))
    }

    @Test
    fun my_work_counts_have_no_delay_rule() = runTest {
        server.json("GET /api/tasks/user/*", """{"tasks":[],"totalCount":4}""")
        val counts = repository.counts(TaskScope.Mine, assignedToMe = false).getOrThrow()
        assertEquals(0, counts.delayed)
        assertEquals(2, server.requests.size)
        server.requests.forEach { assertNull(it.param("taskDelayFilter")) }
    }

    /** Access is already loaded; nothing else on requests is called by the task list. */
    private fun loadedAccess(): RequestRepository {
        val access = MutableStateFlow(RequestAccess(loaded = true))
        return Proxy.newProxyInstance(
            RequestRepository::class.java.classLoader,
            arrayOf(RequestRepository::class.java),
        ) {
                _,
                method,
                _,
            ->
            when {
                method.name == "getAccess" -> access
                // Kotlin mangles the names of functions that return Result.
                method.name.startsWith("ensureAccess") -> Result.success(Unit)
                else -> unused(method.name)
            }
        } as RequestRepository
    }

    private fun unused(name: String): Nothing = error("Not used by the task list: $name")
}
