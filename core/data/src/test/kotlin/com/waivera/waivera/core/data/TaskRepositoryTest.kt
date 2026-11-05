package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.LiveTaskRepository
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskSlice
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.api.TaskApi
import com.waivera.waivera.core.testing.FixtureServer
import com.waivera.waivera.core.testing.TestSessionRepository
import java.lang.reflect.Proxy
import java.time.LocalDate
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
    fun each_section_asks_for_its_own_slice_and_page_size() = runTest {
        server.json("GET /api/tasks/user/*", """{"tasks":[],"totalCount":0}""")
        val dueSoon = TaskQuery(TaskScope.Casework, TaskWindow.DueSoon)
        // The week of the US fall-back change (Sunday Nov 1 2026): plain dates, no offsets.
        val week = TaskSlice(dueFrom = LocalDate.of(2026, 11, 1), dueTo = LocalDate.of(2026, 11, 7))
        repository.tasks(dueSoon.copy(slice = week), page = 2, pageSize = 5).getOrThrow()
        repository.tasks(dueSoon.copy(slice = TaskSlice(delayed = true)), 1, 5).getOrThrow()
        val overdueSlice = TaskSlice(dueTo = LocalDate.of(2026, 10, 31))
        repository.tasks(TaskQuery(TaskScope.Casework, TaskWindow.AllOpen, slice = overdueSlice), 1, 5).getOrThrow()
        repository.tasks(TaskQuery(TaskScope.Mine, TaskWindow.AllOpen, slice = overdueSlice), 1, 5).getOrThrow()
        val (thisWeek, delayed, overdue) = server.requests
        val mine = server.requests[3]
        assertEquals("2026-11-01", thisWeek.param("dateRangeStart"))
        assertEquals("2026-11-07", thisWeek.param("dateRangeEnd"))
        assertEquals("month", thisWeek.param("date"))
        assertEquals("without-delays", thisWeek.param("taskDelayFilter"))
        assertEquals("2", thisWeek.param("page"))
        assertEquals("5", thisWeek.param("pageSize"))
        assertEquals("delayed", delayed.param("taskDelayFilter"))
        assertEquals("month", delayed.param("date"))
        assertNull(delayed.param("dateRangeStart"))
        // Overdue is cut by date like the week sections, never by the server's own "today".
        assertNull(overdue.param("date"))
        assertEquals("2026-10-31", overdue.param("dateRangeEnd"))
        assertEquals("without-delays", overdue.param("taskDelayFilter"))
        assertEquals("2026-10-31", mine.param("dateRangeEnd"))
        assertNull(mine.param("taskDelayFilter"))
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
