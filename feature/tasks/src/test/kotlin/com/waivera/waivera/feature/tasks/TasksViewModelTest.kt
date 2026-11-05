package com.waivera.waivera.feature.tasks

import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskSlice
import com.waivera.waivera.core.data.model.TaskWindow
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.core.testing.TestSessionRepository
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
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

    private fun TestScope.viewModel(
        tasks: FakeTasks,
        clock: TestClock = TestClock(),
        requests: FakeRequests = FakeRequests(),
        graph: DemoGraph = DemoGraph(backgroundScope),
    ): TasksViewModel {
        val vm = TasksViewModel(tasks, requests, TestSessionRepository(), graph.calendar, graph.settings, clock)
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
        assertEquals(listOf("HEA-1"), vm.uiState.value.rows().map { it.key })
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
        assertEquals(1, state.rowCount)
        assertTrue(state.refreshFailed)
    }

    @Test
    fun a_section_counts_the_servers_total_and_shows_its_first_five() = runTest {
        val tasks = FakeTasks().apply {
            all =
                (1..12).map {
                    FakeTasks.summary(it, "Late $it", due = LocalDate.of(2026, 10, 1).plusDays(it.toLong()))
                } +
                (21..23).map {
                    FakeTasks.summary(it, "Delayed $it", due = LocalDate.of(2026, 10, 1)).copy(delayed = true)
                }
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.Overdue)
        runCurrent()
        val (overdue, delayed) = vm.uiState.value.sections
        assertEquals(DueGroup.Overdue, overdue.group)
        assertEquals(12, overdue.total)
        assertEquals(5, overdue.rows.size)
        assertEquals(SectionMore.ShowMore(hidden = 7), overdue.more)
        assertEquals(3, delayed.total)
        assertNull(delayed.more)
        assertEquals(15, vm.uiState.value.totalCount)
    }

    @Test
    fun show_more_adds_ten_in_one_read_then_folds_back_without_asking_again() = runTest {
        val tasks = FakeTasks().apply {
            all =
                (1..17).map { FakeTasks.summary(it, "Late $it", due = LocalDate.of(2026, 10, 1).plusDays(it.toLong())) }
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.Overdue)
        runCurrent()

        tasks.listGate = CompletableDeferred()
        val before = tasks.listCalls.size
        vm.showMore(DueGroup.Overdue)
        vm.showMore(DueGroup.Overdue)
        runCurrent()
        assertEquals(SectionMore.ShowMore(hidden = 12, loading = true), vm.uiState.value.sections.single().more)
        tasks.listGate?.complete(Unit)
        runCurrent()
        // One read from the top for all fifteen, asked for once.
        assertEquals(listOf(1), tasks.listCalls.drop(before).map { it.third })
        assertEquals(listOf(15), tasks.pageSizes.drop(before))
        var section = vm.uiState.value.sections.single()
        assertEquals((1..15).toList(), section.rows.map { it.id })
        assertEquals(17, section.total)
        assertEquals(SectionMore.ShowMore(hidden = 2), section.more)

        vm.showMore(DueGroup.Overdue)
        runCurrent()
        section = vm.uiState.value.sections.single()
        assertEquals(17, section.rows.size)
        assertEquals(SectionMore.ShowFewer, section.more)

        val loaded = tasks.listCalls.size
        vm.showFewer(DueGroup.Overdue)
        assertEquals(5, vm.uiState.value.sections.single().rows.size)
        vm.showMore(DueGroup.Overdue)
        runCurrent()
        assertEquals(15, vm.uiState.value.sections.single().rows.size)
        assertEquals(loaded, tasks.listCalls.size)
    }

    @Test
    fun a_task_closed_elsewhere_before_show_more_never_strands_a_row() = runTest {
        val tasks = FakeTasks().apply {
            all =
                (1..12).map { FakeTasks.summary(it, "Late $it", due = LocalDate.of(2026, 10, 1).plusDays(it.toLong())) }
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.Overdue)
        runCurrent()

        // Task 2 is closed on the web, so every later row moves up one place on the server.
        tasks.all = tasks.all.filterNot { it.id == 2 }
        vm.showMore(DueGroup.Overdue)
        runCurrent()
        val section = vm.uiState.value.sections.single()
        assertEquals((1..12).filterNot { it == 2 }, section.rows.map { it.id })
        assertEquals(11, section.total)
        assertEquals(SectionMore.ShowFewer, section.more)
    }

    @Test
    fun a_failed_show_more_keeps_the_rows_and_offers_to_try_again() = runTest {
        val tasks = FakeTasks().apply {
            all =
                (1..8).map { FakeTasks.summary(it, "Late $it", due = LocalDate.of(2026, 10, 1).plusDays(it.toLong())) }
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.Overdue)
        runCurrent()
        tasks.failList = WaiveraError.Network()
        vm.showMore(DueGroup.Overdue)
        runCurrent()
        val section = vm.uiState.value.sections.single()
        assertEquals(5, section.rows.size)
        assertEquals(SectionMore.ShowMore(hidden = 3, failed = true), section.more)

        tasks.failList = null
        vm.showMore(DueGroup.Overdue)
        runCurrent()
        assertEquals(SectionMore.ShowFewer, vm.uiState.value.sections.single().more)
    }

    @Test
    fun one_failed_section_keeps_the_others_and_offers_to_try_again() = runTest {
        val tasks = FakeTasks().apply {
            all = listOf(
                FakeTasks.summary(1, "Late", due = LocalDate.of(2026, 10, 20)),
                FakeTasks.summary(2, "Soon", due = LocalDate.of(2026, 11, 3)),
            )
            failWhen = { it.slice?.dueFrom != null && it.slice?.dueTo == null }
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.AllOpen)
        runCurrent()
        val state = vm.uiState.value
        assertEquals(TasksPhase.Content, state.phase)
        assertEquals(listOf(DueGroup.Overdue, DueGroup.ThisWeek), state.sections.map { it.group })
        assertTrue(state.refreshFailed)

        tasks.failWhen = null
        vm.refresh()
        runCurrent()
        assertFalse(vm.uiState.value.refreshFailed)
    }

    @Test
    fun a_refresh_while_show_more_waits_never_leaves_it_loading() = runTest {
        val tasks = FakeTasks().apply {
            all = (1..9).map { FakeTasks.summary(it, "Late $it", due = LocalDate.of(2026, 10, it)) }
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.Overdue)
        runCurrent()
        tasks.listGate = CompletableDeferred()
        vm.showMore(DueGroup.Overdue)
        runCurrent()
        tasks.failList = WaiveraError.Network()
        vm.refresh()
        tasks.listGate?.complete(Unit)
        runCurrent()
        assertEquals(SectionMore.ShowMore(hidden = 4), vm.uiState.value.sections.single().more)
    }

    @Test
    fun a_refresh_keeps_an_open_section_open_and_reloads_it_whole() = runTest {
        val tasks = FakeTasks().apply {
            all =
                (1..30).map {
                    FakeTasks.summary(it, "Late $it", due = LocalDate.of(2026, 10, 1).plusDays(it.toLong() / 2))
                }
        }
        val vm = viewModel(tasks)
        runCurrent()
        vm.setWindow(TaskWindow.Overdue)
        runCurrent()
        vm.showMore(DueGroup.Overdue)
        runCurrent()

        tasks.all = tasks.all.drop(1)
        val before = tasks.listCalls.size
        vm.refresh()
        runCurrent()
        // Overdue comes back as open as it was; the closed Delayed section asks for its first five.
        assertEquals(listOf(15, 5), tasks.pageSizes.drop(before))
        val section = vm.uiState.value.sections.single()
        assertEquals((2..16).toList(), section.rows.map { it.id })
        assertEquals(SectionMore.ShowMore(hidden = 14), section.more)

        // A new list starts every section closed.
        vm.setWindow(TaskWindow.AllOpen)
        runCurrent()
        assertEquals(5, vm.uiState.value.sections.first().rows.size)
    }

    @Test
    fun search_waits_for_typing_to_pause_then_starts_over() = runTest {
        val tasks = FakeTasks().apply {
            all =
                listOf(FakeTasks.summary(1, "Plan review"), FakeTasks.summary(2, "Intake"))
        }
        val vm = viewModel(tasks)
        runCurrent()

        vm.onQueryChange("in")
        vm.onQueryChange("inta")
        advanceTimeBy(SEARCH_PAUSE)
        runCurrent()
        assertEquals(listOf("", "inta"), tasks.listCalls.map { it.second }.distinct())
        assertEquals(listOf("HEA-2"), vm.uiState.value.rows().map { it.key })
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
        assertEquals(DueText.Today, vm.uiState.value.rows().single().due)
    }

    @Test
    fun casework_is_the_default_list_and_assigned_to_me_narrows_it() = runTest {
        val tasks = FakeTasks()
        val vm = viewModel(tasks)
        runCurrent()
        assertEquals(TaskScope.Casework, tasks.queries.last().scope)
        assertFalse(tasks.queries.last().assignedToMe)

        vm.setAssignedToMe(true)
        runCurrent()
        assertTrue(tasks.queries.last().assignedToMe)
        assertEquals(TaskScope.Casework to true, tasks.countCalls.last())
    }

    @Test
    fun my_work_asks_for_the_persons_own_tasks_without_the_casework_chip() = runTest {
        val tasks = FakeTasks()
        val vm = viewModel(tasks)
        runCurrent()
        vm.setAssignedToMe(true)
        vm.setSegment(TasksSegment.MyWork)
        runCurrent()
        assertEquals(TaskScope.Mine, tasks.queries.last().scope)
        assertFalse(tasks.queries.last().assignedToMe)
    }

    @Test
    fun the_requests_segment_shows_only_with_access_and_counts_what_waits_here() = runTest {
        val requests = FakeRequests(RequestAccess(showsRequests = false, loaded = true))
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        assertEquals(listOf(TasksSegment.Casework, TasksSegment.MyWork), vm.uiState.value.segments)

        requests.accessFlow.value = RequestAccess(showsRequests = true, loaded = true)
        requests.serverWaiting = listOf(
            FakeRequests.request(1),
            FakeRequests.request(2, RequestKind.Closure),
            FakeRequests.request(3, RequestKind.Other),
        )
        requests.refreshWaiting()
        runCurrent()
        assertEquals(TasksSegment.entries, vm.uiState.value.segments)
        // Time off and the rest are reviewed on the web, so they don't count here.
        assertEquals(2, vm.uiState.value.waitingCount)
    }

    @Test
    fun losing_requests_access_while_on_requests_falls_back_to_casework() = runTest {
        val requests = FakeRequests()
        val vm = viewModel(FakeTasks(), requests = requests)
        runCurrent()
        vm.setSegment(TasksSegment.Requests)
        runCurrent()
        assertEquals(TasksSegment.Requests, vm.uiState.value.segment)

        requests.accessFlow.value = RequestAccess(loaded = true)
        runCurrent()
        assertEquals(TasksSegment.Casework, vm.uiState.value.segment)
    }

    @Test
    fun going_to_requests_and_back_keeps_the_list_without_reloading() = runTest {
        val tasks = FakeTasks().apply { all = listOf(FakeTasks.summary(1, "Plan review")) }
        val vm = viewModel(tasks)
        runCurrent()
        val calls = tasks.queries.size
        vm.setSegment(TasksSegment.Requests)
        vm.setSegment(TasksSegment.Casework)
        runCurrent()
        assertEquals(calls, tasks.queries.size)
        assertEquals(listOf("HEA-1"), vm.uiState.value.rows().map { it.key })
    }

    @Test
    fun cards_group_by_due_week_and_carry_lateness_owner_and_one_flag() = runTest {
        val clock = TestClock(Instant.parse("2026-11-04T15:00:00Z"))
        val tasks = FakeTasks().apply {
            today = LocalDate.of(2026, 11, 4)
            all = listOf(
                FakeTasks.summary(1, "Late", due = LocalDate.of(2026, 10, 30)),
                FakeTasks.summary(2, "Friday", due = LocalDate.of(2026, 11, 6))
                    .copy(assigneeId = "usr_priya", assigneeName = "Priya Raman"),
                FakeTasks.summary(3, "Next week", due = LocalDate.of(2026, 11, 10)).copy(pendingClosure = true),
                FakeTasks.summary(4, "Later", due = LocalDate.of(2026, 11, 30)),
            )
        }
        val vm = viewModel(tasks, clock)
        runCurrent()
        vm.setWindow(TaskWindow.AllOpen)
        runCurrent()
        val sections = vm.uiState.value.sections
        assertEquals(
            listOf(DueGroup.Overdue, DueGroup.ThisWeek, DueGroup.NextWeek, DueGroup.Later),
            sections.map { it.group },
        )
        val late = sections.first().rows.single()
        assertEquals(5, late.daysLate)
        assertFalse(late.delayed)
        assertNull(late.flag)
        assertEquals("Priya Raman", sections[1].rows.single().assignee)
        assertEquals(RowFlag.ClosureRequested, sections[2].rows.single().flag)
        assertEquals(listOf(4), sections[3].rows.map { it.id })
    }

    @Test
    fun a_delayed_task_is_not_overdue_and_sits_in_its_own_group() = runTest {
        // Sunday Nov 1 2026 is the US fall-back day; the delay's last day is a literal date.
        val clock = TestClock(Instant.parse("2026-11-01T15:00:00Z"))
        val tasks = FakeTasks().apply {
            all = listOf(
                FakeTasks.summary(1, "Really late", due = LocalDate.of(2026, 10, 28)),
                FakeTasks.summary(2, "Delayed and past due", due = LocalDate.of(2026, 10, 25))
                    .copy(delayed = true, delayedThrough = LocalDate.of(2026, 11, 1)),
                FakeTasks.summary(3, "Delayed, due later", due = LocalDate.of(2026, 11, 20))
                    .copy(delayed = true),
                FakeTasks.summary(4, "This week", due = LocalDate.of(2026, 11, 4)),
            )
        }
        val vm = viewModel(tasks, clock)
        runCurrent()
        vm.setWindow(TaskWindow.AllOpen)
        runCurrent()
        val sections = vm.uiState.value.sections
        assertEquals(listOf(DueGroup.Overdue, DueGroup.Delayed, DueGroup.ThisWeek), sections.map { it.group })
        assertEquals(listOf(1), sections[0].rows.map { it.id })
        val delayed = sections[1].rows
        assertEquals(listOf(2, 3), delayed.map { it.id })
        assertNull(delayed[0].daysLate)
        assertTrue(delayed[0].delayed)
        assertEquals("Nov 1", delayed[0].delayedThrough)
        assertNull(delayed[1].delayedThrough)
    }

    @Test
    fun the_delays_choice_goes_to_the_server_for_casework_and_is_saved() = runTest {
        val graph = DemoGraph(backgroundScope)
        val tasks = FakeTasks().apply { all = listOf(FakeTasks.summary(1, "Plan review")) }
        val vm = viewModel(tasks, graph = graph)
        runCurrent()
        assertEquals(DelayFilter.Show, tasks.queries.last().delays)

        vm.setDelays(DelayFilter.Hide)
        runCurrent()
        assertEquals(DelayFilter.Hide, vm.uiState.value.delays)
        assertEquals(DelayFilter.Hide, tasks.queries.last().delays)
        assertEquals(DelayFilter.Hide, graph.settings.settings.first().taskDelays)

        // My work is employee tasks, which have no delays: the choice is kept but not sent.
        vm.setSegment(TasksSegment.MyWork)
        runCurrent()
        assertEquals(TaskScope.Mine, tasks.queries.last().scope)
        assertEquals(DelayFilter.Show, tasks.queries.last().delays)
        assertFalse(vm.uiState.value.showsDelays)
    }

    @Test
    fun a_pick_made_before_the_saved_choice_arrives_wins() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.settings.setTaskDelays(DelayFilter.Only)
        val tasks = FakeTasks().apply { all = listOf(FakeTasks.summary(1, "Plan review")) }
        val vm =
            TasksViewModel(tasks, FakeRequests(), TestSessionRepository(), graph.calendar, graph.settings, TestClock())
        vm.setDelays(DelayFilter.Hide)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertEquals(DelayFilter.Hide, vm.uiState.value.delays)
        assertEquals(DelayFilter.Hide, tasks.queries.last().delays)
        assertEquals(DelayFilter.Hide, graph.settings.settings.first().taskDelays)
    }

    @Test
    fun the_first_page_waits_for_the_saved_delays_choice() = runTest {
        val graph = DemoGraph(backgroundScope)
        graph.settings.setTaskDelays(DelayFilter.Only)
        val tasks = FakeTasks().apply { all = listOf(FakeTasks.summary(1, "Plan review")) }
        val vm = viewModel(tasks, graph = graph)
        runCurrent()
        assertEquals(listOf(DelayFilter.Only), tasks.queries.map { it.delays })
        assertEquals(DelayFilter.Only, vm.uiState.value.delays)
    }

    @Test
    fun the_persons_own_tasks_never_say_who_they_are_assigned_to() = runTest {
        val session = TestSessionRepository()
        val tasks = FakeTasks().apply {
            all = listOf(
                FakeTasks.summary(1, "Mine").copy(assigneeId = "usr_alex_rivera", assigneeName = "Alex Rivera"),
            )
        }
        val graph = DemoGraph(backgroundScope)
        val vm = TasksViewModel(tasks, FakeRequests(), session, graph.calendar, graph.settings, TestClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        runCurrent()
        assertNull(vm.uiState.value.rows().single().assignee)
    }

    @Test
    fun weeks_follow_the_locale_and_hold_across_the_fall_back_change() {
        // Sunday Nov 1 2026 is the US fall-back day. In the US the week starts that Sunday.
        val wednesday = LocalDate.of(2026, 11, 4)
        assertEquals(DueGroup.ThisWeek, TasksViewModel.group(LocalDate.of(2026, 11, 7), wednesday, Locale.US))
        assertEquals(DueGroup.NextWeek, TasksViewModel.group(LocalDate.of(2026, 11, 8), wednesday, Locale.US))
        assertEquals(DueGroup.Later, TasksViewModel.group(LocalDate.of(2026, 11, 15), wednesday, Locale.US))
        // Weeks start on Monday in the UK, so that Sunday still belongs to this week.
        assertEquals(DueGroup.ThisWeek, TasksViewModel.group(LocalDate.of(2026, 11, 8), wednesday, Locale.UK))
        assertEquals(DueGroup.Overdue, TasksViewModel.group(LocalDate.of(2026, 11, 1), wednesday, Locale.US))
    }

    @Test
    fun sections_cut_weeks_by_the_locale_across_the_fall_back_change() {
        // Wednesday Nov 4 2026, the week of the US fall-back day; US weeks start Sunday Nov 1.
        val wednesday = LocalDate.of(2026, 11, 4)
        val allOpen = TaskQuery(TaskScope.Casework, TaskWindow.AllOpen)
        val plan = TasksViewModel.sectionPlan(allOpen, wednesday, Locale.US).toMap()
        assertEquals(
            listOf(DueGroup.Overdue, DueGroup.Delayed, DueGroup.ThisWeek, DueGroup.NextWeek, DueGroup.Later),
            plan.keys.toList(),
        )
        // Overdue ends the day before This week starts, so no task can sit in both.
        assertEquals(TaskSlice(dueTo = LocalDate.of(2026, 11, 3)), plan[DueGroup.Overdue])
        assertEquals(TaskSlice(dueFrom = wednesday, dueTo = LocalDate.of(2026, 11, 7)), plan[DueGroup.ThisWeek])
        assertEquals(
            TaskSlice(dueFrom = LocalDate.of(2026, 11, 8), dueTo = LocalDate.of(2026, 11, 14)),
            plan[DueGroup.NextWeek],
        )
        assertEquals(TaskSlice(dueFrom = LocalDate.of(2026, 11, 15)), plan[DueGroup.Later])
        // Monday weeks in the UK keep that Sunday in this week.
        val uk = TasksViewModel.sectionPlan(allOpen, wednesday, Locale.UK).toMap()
        assertEquals(LocalDate.of(2026, 11, 8), uk[DueGroup.ThisWeek]?.dueTo)
    }

    @Test
    fun sections_follow_the_window_and_the_delays_choice() {
        val day = LocalDate.of(2026, 11, 4)
        fun groups(query: TaskQuery) = TasksViewModel.sectionPlan(query, day, Locale.US).map { it.first }
        val casework = TaskQuery(TaskScope.Casework, TaskWindow.DueSoon)
        assertEquals(
            listOf(DueGroup.Delayed, DueGroup.ThisWeek, DueGroup.NextWeek, DueGroup.Later),
            groups(casework),
        )
        assertEquals(
            listOf(DueGroup.Overdue, DueGroup.Delayed),
            groups(casework.copy(window = TaskWindow.Overdue)),
        )
        assertEquals(
            listOf(DueGroup.Overdue),
            groups(casework.copy(window = TaskWindow.Overdue, delays = DelayFilter.Hide)),
        )
        assertEquals(listOf(DueGroup.Delayed), groups(casework.copy(delays = DelayFilter.Only)))
        // My work has no delays, so it never asks for a delayed section.
        assertEquals(listOf(DueGroup.Overdue), groups(TaskQuery(TaskScope.Mine, TaskWindow.Overdue)))
    }

    private fun TasksUiState.rows(): List<TaskRow> = sections.flatMap { it.rows }

    private companion object {
        const val SEARCH_PAUSE = 301L
    }
}
