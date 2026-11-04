package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestOutcome
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskDelay
import com.waivera.waivera.core.data.model.TaskFile
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
import kotlinx.coroutines.CompletableDeferred
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
        requests: FakeRequests = FakeRequests(),
    ): TaskDetailViewModel {
        val vm = TaskDetailViewModel(
            7,
            tasks,
            requests,
            TestSessionRepository(),
            config,
            DemoGraph(backgroundScope).calendar,
            clock,
        )
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
    fun a_delay_keeps_a_past_due_task_from_reading_late() = runTest {
        // Nov 1 2026 is the US fall-back day; the dates are literal and do not shift.
        val clock = TestClock(Instant.parse("2026-11-01T15:00:00Z"))
        val pastDue = FakeTasks.task(due = LocalDate.of(2026, 10, 25))
        val tasks = FakeTasks().apply {
            detail = Result.success(
                pastDue.copy(
                    delay = TaskDelay("Hospitalized", null, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 1)),
                ),
            )
        }
        val vm = viewModel(tasks, clock)
        runCurrent()
        val state = vm.uiState.value
        assertTrue(state.due.overdue)
        assertFalse(state.late)
        assertEquals(DelayNote("Nov 1", "Hospitalized", null), state.delay)

        tasks.detail = Result.success(pastDue)
        vm.load()
        runCurrent()
        assertTrue(vm.uiState.value.late)
        assertNull(vm.uiState.value.delay)
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
            types =
                Result.success(
                    listOf(
                        ActivityType(3, "Monthly contact", recommendedMinutes = 30, fixedDescription = "Monthly call"),
                    ),
                )
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

    @Test
    fun ask_for_a_change_offers_only_what_the_role_and_the_task_allow() = runTest {
        val requests = FakeRequests(RequestAccess(canAskMoreTime = false, canAskClosure = true, loaded = true))
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        assertEquals(listOf(AskKind.Closure), vm.uiState.value.askChoices)

        requests.accessFlow.value = RequestAccess(canAskMoreTime = true, canAskClosure = true, loaded = true)
        runCurrent()
        assertEquals(AskKind.entries, vm.uiState.value.askChoices)
    }

    @Test
    fun a_pending_request_hides_ask_for_a_change() = runTest {
        val requests = FakeRequests().apply { optionsResult = Result.success(FakeRequests.options(pending = true)) }
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        assertFalse(vm.uiState.value.canAsk)
    }

    @Test
    fun employee_tasks_offer_closure_but_not_more_time_or_a_delay() = runTest {
        val tasks = FakeTasks().apply { detail = Result.success(FakeTasks.task().copy(isClientTask = false)) }
        val vm = viewModel(tasks)
        runCurrent()
        assertEquals(listOf(AskKind.Closure), vm.uiState.value.askChoices)
        vm.startAsk()
        runCurrent()
        // One choice skips the chooser.
        assertEquals(AskKind.Closure, vm.uiState.value.ask?.kind)
    }

    @Test
    fun more_time_needs_a_later_day_within_five_years_a_reason_and_a_justification() = runTest {
        val requests = FakeRequests()
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        vm.startAsk()
        vm.chooseAsk(AskKind.MoreTime)
        runCurrent()
        vm.submitAsk()
        val empty = vm.uiState.value.ask
        assertTrue(empty?.showErrors == true && empty.dueMissing && empty.reasonMissing && empty.explanationMissing)

        vm.setAskDue(FakeRequests.Due)
        assertTrue(vm.uiState.value.ask?.dueTooEarly == true)
        vm.setAskDue(FakeRequests.Due.plusYears(5).plusDays(1))
        assertTrue(vm.uiState.value.ask?.dueTooLate == true)
        vm.setAskDue(FakeRequests.Due.plusDays(7))
        vm.setAskReason(1)
        vm.setAskExplanation("x".repeat(AskForm.MAX_EXPLANATION + 1))
        assertTrue(vm.uiState.value.ask?.explanationTooLong == true)
        vm.submitAsk()
        assertTrue(requests.asked.isEmpty())
    }

    @Test
    fun a_reason_that_needs_evidence_needs_a_file_on_the_task() = runTest {
        val requests = FakeRequests().apply {
            reasons = Result.success(listOf(ChangeReason(2, "Hospital stay", null, requiresEvidence = true)))
            filesResult = Result.success(listOf(TaskFile(31, "Admission notice.pdf")))
        }
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        vm.startAsk()
        vm.chooseAsk(AskKind.MoreTime)
        runCurrent()
        vm.setAskDue(FakeRequests.Due.plusDays(7))
        vm.setAskReason(2)
        vm.setAskExplanation("Admitted Nov 3.")
        assertTrue(vm.uiState.value.ask?.evidenceMissing == true)
        vm.setAskEvidence(31)
        vm.submitAsk()
        runCurrent()
        assertEquals(listOf("more:2026-11-13:2:31"), requests.asked)
    }

    @Test
    fun a_sent_request_shows_as_pending_at_once_and_says_so_when_it_lands() = runTest {
        val requests = FakeRequests().apply { askGate = CompletableDeferred() }
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        fillMoreTime(vm)
        vm.submitAsk()
        runCurrent()
        // Optimistic: the sheet is closed and the banner shows before the server answers.
        assertNull(vm.uiState.value.ask)
        assertEquals(RequestKind.MoreTime, vm.uiState.value.pending?.kind)
        assertEquals("Fri, Nov 13", vm.uiState.value.pending?.newDue)
        assertFalse(vm.uiState.value.canAsk)

        requests.askGate?.complete(Unit)
        runCurrent()
        assertEquals(TaskDetailEffect.Asked(AskKind.MoreTime, inEffect = false), vm.effects.first())
    }

    @Test
    fun an_owners_own_request_takes_effect_and_reloads_the_task() = runTest {
        val tasks = FakeTasks()
        val requests = FakeRequests().apply { askResult = Result.success(RequestOutcome.Approved) }
        val vm = viewModel(tasks, requests = requests)
        runCurrent()
        fillMoreTime(vm)
        tasks.detail = Result.success(FakeTasks.task(due = FakeRequests.Due.plusDays(7)))
        vm.submitAsk()
        runCurrent()
        assertEquals(TaskDetailEffect.Asked(AskKind.MoreTime, inEffect = true), vm.effects.first())
        assertNull(vm.uiState.value.pending)
        assertEquals(FakeRequests.Due.plusDays(7), vm.uiState.value.task?.due)
    }

    @Test
    fun a_failed_request_takes_the_banner_down_and_brings_the_form_back_filled_in() = runTest {
        val requests = FakeRequests().apply { askResult = Result.failure(WaiveraError.Network()) }
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        fillMoreTime(vm)
        vm.submitAsk()
        runCurrent()
        assertNull(vm.uiState.value.pending)
        val form = vm.uiState.value.ask
        assertTrue(form?.failed == true)
        assertEquals("Visiting family until Nov 9.", form?.explanation)
        assertEquals(FakeRequests.Due.plusDays(7), form?.newDue)
    }

    @Test
    fun a_delay_is_checked_again_before_it_is_sent() = runTest {
        val requests = FakeRequests()
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        vm.startAsk()
        vm.chooseAsk(AskKind.Delay)
        runCurrent()
        vm.setAskReason(1)
        vm.setAskExplanation("Admitted Nov 3.")
        val moved = DelayPeriod(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 13), renewal = false)
        requests.optionsResult = Result.success(FakeRequests.options(delay = moved))
        vm.submitAsk()
        runCurrent()
        assertTrue(requests.asked.isEmpty())
        val form = vm.uiState.value.ask
        assertTrue(form?.delayChanged == true)
        assertEquals(moved, form?.delay)

        vm.submitAsk()
        runCurrent()
        assertEquals(listOf("delay:2026-11-05:2026-11-13:1"), requests.asked)
    }

    @Test
    fun closing_for_other_needs_a_comment() = runTest {
        val requests = FakeRequests()
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        vm.startAsk()
        vm.chooseAsk(AskKind.Closure)
        runCurrent()
        vm.setAskReason(5)
        assertTrue(vm.uiState.value.ask?.isValid == true)
        vm.setAskReason(6)
        assertTrue(vm.uiState.value.ask?.explanationMissing == true)
        vm.setAskExplanation("Duplicate of HEA-12")
        vm.submitAsk()
        runCurrent()
        assertEquals(listOf("closure:6:Duplicate of HEA-12"), requests.asked)
    }

    @Test
    fun back_from_a_form_returns_to_the_choices() = runTest {
        val vm = viewModel(FakeTasks())
        runCurrent()
        vm.startAsk()
        vm.chooseAsk(AskKind.MoreTime)
        assertTrue(vm.backToChoices())
        assertNull(vm.uiState.value.ask?.kind)
        assertFalse(vm.backToChoices())
    }

    private fun TestScope.fillMoreTime(vm: TaskDetailViewModel) {
        vm.startAsk()
        vm.chooseAsk(AskKind.MoreTime)
        runCurrent()
        vm.setAskDue(FakeRequests.Due.plusDays(7))
        vm.setAskReason(1)
        vm.setAskExplanation("Visiting family until Nov 9.")
    }
}
