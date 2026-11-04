package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.core.testing.TestSessionRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class TaskDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val web = NetworkConfig(apiBaseUrl = "https://api.example.com", webBaseUrl = "https://app.example.com")

    private fun TestScope.viewModel(
        tasks: FakeTasks,
        clock: TestClock = TestClock(),
        config: NetworkConfig = web,
    ): TaskDetailViewModel {
        val vm = TaskDetailViewModel(7, tasks, TestSessionRepository(), config, DemoGraph(backgroundScope).calendar, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private fun entry(id: Int, by: String? = "usr_alex_rivera") =
        TaskActivity(id, "Phone call", 20, Instant.parse("2026-11-01T15:00:00Z"), by, "Alex Rivera", null)

    @Test
    fun loads_the_task_and_its_activity() = runTest {
        val tasks = FakeTasks().apply { activity = Result.success(listOf(entry(1), entry(2, by = "someone"))) }
        val vm = viewModel(tasks)
        runCurrent()
        val state = vm.uiState.value
        assertEquals(DetailPhase.Content, state.phase)
        val rows = assertIs<Loadable.Ready<List<ActivityRow>>>(state.activity).value
        assertEquals(listOf(true, false), rows.map { it.byMe })
        assertTrue(state.canLog)
    }

    @Test
    fun no_activity_is_an_empty_list_not_an_error() = runTest {
        val vm = viewModel(FakeTasks())
        runCurrent()
        assertEquals(Loadable.Ready(emptyList()), vm.uiState.value.activity)
    }

    @Test
    fun a_failed_task_load_shows_the_error_and_retry_recovers() = runTest {
        val tasks = FakeTasks().apply { detail = Result.failure(WaiveraError.NotFound()) }
        val vm = viewModel(tasks)
        runCurrent()
        assertEquals(DetailPhase.Error, vm.uiState.value.phase)

        tasks.detail = Result.success(FakeTasks.task())
        vm.load()
        runCurrent()
        assertEquals(DetailPhase.Content, vm.uiState.value.phase)
    }

    @Test
    fun failed_activity_keeps_the_task_on_screen() = runTest {
        val tasks = FakeTasks().apply { activity = Result.failure(WaiveraError.Network()) }
        val vm = viewModel(tasks)
        runCurrent()
        assertEquals(DetailPhase.Content, vm.uiState.value.phase)
        assertEquals(Loadable.Failed, vm.uiState.value.activity)
    }

    @Test
    fun a_finished_task_cannot_take_new_activity() = runTest {
        val tasks = FakeTasks().apply { detail = Result.success(FakeTasks.task(TaskState.Completed)) }
        val vm = viewModel(tasks)
        runCurrent()
        assertFalse(vm.uiState.value.canLog)
        vm.startLog()
        assertNull(vm.uiState.value.log)
    }

    @Test
    fun logging_needs_a_type_and_a_description() = runTest {
        val tasks = FakeTasks()
        val vm = viewModel(tasks)
        runCurrent()
        vm.startLog()
        runCurrent()
        vm.submitLog()
        val form = vm.uiState.value.log
        assertTrue(form?.showErrors == true)
        assertTrue(form?.typeMissing == true)
        assertTrue(tasks.logged.isEmpty())
    }

    @Test
    fun a_type_with_a_set_time_and_text_locks_them() = runTest {
        val tasks = FakeTasks().apply {
            types = Result.success(listOf(ActivityType(3, "Monthly contact", recommendedMinutes = 30, fixedDescription = "Monthly call")))
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.startLog()
        runCurrent()
        vm.chooseType(3)
        vm.stepMinutes(up = true)
        vm.setDescription("changed")
        val form = vm.uiState.value.log
        assertEquals(30, form?.minutes)
        assertEquals("Monthly call", form?.description)
    }

    @Test
    fun a_logged_activity_closes_the_sheet_and_appears() = runTest {
        val tasks = FakeTasks()
        val vm = viewModel(tasks)
        runCurrent()
        vm.startLog()
        runCurrent()
        vm.chooseType(1)
        vm.stepMinutes(up = true)
        vm.setDescription("Visited the family")
        tasks.activity = Result.success(listOf(entry(9)))
        vm.submitLog()
        runCurrent()

        assertEquals(TaskDetailEffect.Logged, vm.effects.first())
        assertNull(vm.uiState.value.log)
        assertEquals(30, tasks.logged.single().minutes)
        val rows = assertIs<Loadable.Ready<List<ActivityRow>>>(vm.uiState.value.activity).value
        assertTrue(rows.single().isNew)
    }

    @Test
    fun a_failed_log_keeps_what_was_typed() = runTest {
        val tasks = FakeTasks().apply { logResult = Result.failure(WaiveraError.Network()) }
        val vm = viewModel(tasks)
        runCurrent()
        vm.startLog()
        runCurrent()
        vm.chooseType(1)
        vm.setDescription("Visited the family")
        vm.submitLog()
        runCurrent()
        val form = vm.uiState.value.log
        assertTrue(form?.failed == true)
        assertFalse(form?.isSaving == true)
        assertEquals("Visited the family", form?.description)
    }

    @Test
    fun the_picked_time_is_a_saved_zone_wall_clock_across_the_fall_back_change() = runTest {
        // Clocks fell back in Chicago at 2 AM on Nov 1 2026: 9:30 AM that day is UTC-6, not UTC-5.
        val clock = TestClock(Instant.parse("2026-11-01T20:00:00Z"))
        val vm = viewModel(FakeTasks(), clock)
        runCurrent()
        vm.startLog()
        runCurrent()
        vm.setWhen(LocalDate.of(2026, 11, 1), minuteOfDay = 9 * 60 + 30)
        assertEquals(Instant.parse("2026-11-01T15:30:00Z"), vm.uiState.value.log?.completedAt)

        vm.setWhen(LocalDate.of(2026, 10, 31), minuteOfDay = 9 * 60 + 30)
        assertEquals(Instant.parse("2026-10-31T14:30:00Z"), vm.uiState.value.log?.completedAt)
    }

    @Test
    fun a_time_after_now_cannot_be_logged() = runTest {
        val clock = TestClock(Instant.parse("2026-11-01T20:00:00Z"))
        val tasks = FakeTasks()
        val vm = viewModel(tasks, clock)
        runCurrent()
        vm.startLog()
        runCurrent()
        vm.chooseType(1)
        vm.setDescription("Visited")
        vm.setWhen(LocalDate.of(2026, 11, 1), minuteOfDay = 23 * 60)
        assertTrue(vm.uiState.value.log?.inFuture == true)
        vm.submitLog()
        assertTrue(tasks.logged.isEmpty())
    }

    @Test
    fun open_on_web_uses_the_configured_web_app() = runTest {
        val vm = viewModel(FakeTasks())
        runCurrent()
        vm.openOnWeb()
        assertEquals(TaskDetailEffect.OpenWeb("https://app.example.com/task/HEA-7"), vm.effects.first())
    }

    @Test
    fun open_on_web_without_a_web_app_explains_why() = runTest {
        val vm = viewModel(FakeTasks(), config = NetworkConfig(apiBaseUrl = "https://api.example.com", webBaseUrl = ""))
        runCurrent()
        vm.openOnWeb()
        assertEquals(TaskDetailEffect.WebUnavailable, vm.effects.first())
    }
}
