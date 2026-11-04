package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class TasksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.viewModel(tasks: FakeTasks, clock: TestClock = TestClock()): TasksViewModel {
        val vm = TasksViewModel(tasks, DemoGraph(backgroundScope).calendar, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun shows_the_skeleton_while_the_first_page_loads() = runTest {
        val tasks = FakeTasks().apply {
            all = listOf(FakeTasks.summary(1, "Plan review"))
            listGate = CompletableDeferred()
        }
        val vm = viewModel(tasks)
        runCurrent()
        assertEquals(TasksPhase.Loading, vm.uiState.value.phase)

        tasks.listGate?.complete(Unit)
        runCurrent()
        assertEquals(TasksPhase.Content, vm.uiState.value.phase)
        assertEquals(listOf("HEA-1"), vm.uiState.value.rows.map { it.key })
    }

    @Test
    fun an_empty_window_says_so() = runTest {
        val vm = viewModel(FakeTasks())
        runCurrent()
        assertEquals(TasksPhase.Empty, vm.uiState.value.phase)
    }

    @Test
    fun a_failed_first_load_shows_the_error_and_retry_recovers() = runTest {
        val tasks = FakeTasks().apply {
            all = listOf(FakeTasks.summary(1, "Plan review"))
            failList = WaiveraError.Network()
        }
        val vm = viewModel(tasks)
        runCurrent()
        assertEquals(TasksPhase.Error, vm.uiState.value.phase)

        tasks.failList = null
        vm.refresh()
        runCurrent()
        assertEquals(TasksPhase.Content, vm.uiState.value.phase)
    }

    @Test
    fun a_failed_refresh_keeps_the_list_and_flags_it() = runTest {
        val tasks = FakeTasks().apply { all = listOf(FakeTasks.summary(1, "Plan review")) }
        val vm = viewModel(tasks)
        runCurrent()

        tasks.failList = WaiveraError.Network()
        vm.refresh()
        runCurrent()
        val state = vm.uiState.value
        assertEquals(TasksPhase.Content, state.phase)
        assertEquals(1, state.rows.size)
        assertTrue(state.refreshFailed)
    }

    @Test
    fun scrolling_to_the_end_loads_the_next_page_once() = runTest {
        val tasks = FakeTasks().apply { all = (1..5).map { FakeTasks.summary(it, "Task $it") } }
        val vm = viewModel(tasks)
        runCurrent()
        assertTrue(vm.uiState.value.canLoadMore)

        tasks.listGate = CompletableDeferred()
        vm.loadMore()
        vm.loadMore()
        runCurrent()
        assertTrue(vm.uiState.value.isLoadingMore)
        tasks.listGate?.complete(Unit)
        runCurrent()
        assertEquals(4, vm.uiState.value.rows.size)
        assertEquals(listOf(1, 2), tasks.listCalls.map { it.third })
    }

    @Test
    fun search_waits_for_typing_to_pause_then_starts_over() = runTest {
        val tasks = FakeTasks().apply { all = listOf(FakeTasks.summary(1, "Plan review"), FakeTasks.summary(2, "Intake")) }
        val vm = viewModel(tasks)
        runCurrent()

        vm.onQueryChange("in")
        vm.onQueryChange("inta")
        advanceTimeBy(SEARCH_PAUSE)
        runCurrent()
        assertEquals(listOf("", "inta"), tasks.listCalls.map { it.second })
        assertEquals(listOf("HEA-2"), vm.uiState.value.rows.map { it.key })
    }

    @Test
    fun switching_window_asks_the_server_for_that_window() = runTest {
        val tasks = FakeTasks()
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.Overdue)
        runCurrent()
        assertEquals(TaskWindow.Overdue, tasks.listCalls.last().first)
    }

    @Test
    fun due_dates_read_against_the_saved_zone_day_across_the_fall_back_change() = runTest {
        // Clocks fell back in Chicago at 2 AM on Nov 1 2026. 03:30 UTC on Nov 2 is 9:30 PM CST on
        // Nov 1, so a task due Nov 1 is due today, though the UTC day (and a UTC phone) says Nov 2.
        val clock = TestClock(Instant.parse("2026-11-02T03:30:00Z"))
        val tasks = FakeTasks().apply {
            all = listOf(FakeTasks.summary(1, "Plan review", due = LocalDate.of(2026, 11, 1)))
        }
        val vm = viewModel(tasks, clock)
        runCurrent()
        assertEquals(DueText.Today, vm.uiState.value.rows.single().due)
    }

    private companion object {
        const val SEARCH_PAUSE = 301L
    }
}
