package com.waivera.waivera.feature.tasks.participant

import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.core.testing.TestSessionRepository
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.TaskRow
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class ParticipantViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val web = NetworkConfig(apiBaseUrl = "https://api.example.com", webBaseUrl = "https://app.example.com")

    private fun TestScope.viewModel(
        participants: FakeParticipants,
        clock: TestClock = TestClock(),
        config: NetworkConfig = web,
    ): ParticipantViewModel {
        val vm =
            ParticipantViewModel(
                501,
                participants,
                TestSessionRepository(),
                config,
                DemoGraph(backgroundScope).calendar,
                clock,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun starts_loading_then_shows_the_profile_and_open_tasks() = runTest {
        val participants = FakeParticipants().apply {
            tasks =
                Result.success(
                    listOf(FakeParticipants.summary(1, LocalDate.of(2026, 10, 20)), FakeParticipants.summary(2, null)),
                )
            gate = CompletableDeferred()
        }
        val vm = viewModel(participants)
        runCurrent()
        assertEquals(ParticipantPhase.Loading, vm.uiState.value.phase)
        participants.gate?.complete(Unit)
        runCurrent()
        val state = vm.uiState.value
        assertEquals(ParticipantPhase.Content, state.phase)
        assertEquals("Jordan Ellis", state.profile?.name)
        assertEquals(listOf(501), participants.asked)
        assertEquals(2, assertIs<Loadable.Ready<List<TaskRow>>>(state.tasks).value.size)
        assertEquals(1, state.overdue)
    }

    @Test
    fun no_open_tasks_shows_an_empty_list() = runTest {
        val vm = viewModel(FakeParticipants())
        runCurrent()
        assertEquals(emptyList(), assertIs<Loadable.Ready<List<TaskRow>>>(vm.uiState.value.tasks).value)
        assertEquals(0, vm.uiState.value.overdue)
    }

    @Test
    fun a_failed_load_shows_the_error_and_retry_recovers() = runTest {
        val participants = FakeParticipants().apply { profile = Result.failure(WaiveraError.Network()) }
        val vm = viewModel(participants)
        runCurrent()
        assertEquals(ParticipantPhase.Error, vm.uiState.value.phase)
        assertEquals(0, participants.taskLoads)
        participants.profile = Result.success(FakeParticipants.profile())
        vm.load()
        runCurrent()
        assertEquals(ParticipantPhase.Content, vm.uiState.value.phase)
        assertEquals(1, participants.taskLoads)
    }

    @Test
    fun a_participant_outside_the_role_shows_the_forbidden_state() = runTest {
        val participants = FakeParticipants().apply { profile = Result.failure(WaiveraError.Forbidden()) }
        val vm = viewModel(participants)
        runCurrent()
        assertEquals(ParticipantPhase.Forbidden, vm.uiState.value.phase)
        assertNull(vm.uiState.value.profile)
    }

    @Test
    fun a_refresh_after_reconnecting_that_fails_keeps_what_is_on_screen() = runTest {
        val participants = FakeParticipants().apply {
            tasks = Result.success(listOf(FakeParticipants.summary(1, null)))
        }
        val vm = viewModel(participants)
        runCurrent()
        participants.profile = Result.failure(WaiveraError.Network())
        participants.tasks = Result.failure(WaiveraError.Network())
        vm.load()
        vm.loadTasks()
        runCurrent()
        val state = vm.uiState.value
        assertEquals(ParticipantPhase.Content, state.phase)
        assertEquals("Jordan Ellis", state.profile?.name)
        assertEquals(1, assertIs<Loadable.Ready<List<TaskRow>>>(state.tasks).value.size)
    }

    @Test
    fun failed_tasks_show_their_own_error_and_retry() = runTest {
        val participants = FakeParticipants().apply { tasks = Result.failure(WaiveraError.Network()) }
        val vm = viewModel(participants)
        runCurrent()
        assertEquals(ParticipantPhase.Content, vm.uiState.value.phase)
        assertEquals(Loadable.Failed, vm.uiState.value.tasks)
        participants.tasks = Result.success(emptyList())
        vm.loadTasks()
        runCurrent()
        assertIs<Loadable.Ready<List<TaskRow>>>(vm.uiState.value.tasks)
    }

    @Test
    fun opens_the_participant_snapshot_on_the_web() = runTest {
        val vm = viewModel(FakeParticipants())
        runCurrent()
        vm.openOnWeb()
        val effect = vm.effects.first()
        assertEquals(ParticipantEffect.OpenWeb("https://app.example.com/participant/501/participant-snapshot"), effect)
    }

    @Test
    fun without_a_web_address_says_the_web_is_unavailable() = runTest {
        val vm =
            viewModel(
                FakeParticipants(),
                config = NetworkConfig(apiBaseUrl = "https://api.example.com", webBaseUrl = ""),
            )
        runCurrent()
        vm.openOnWeb()
        assertEquals(ParticipantEffect.WebUnavailable, vm.effects.first())
    }

    @Test
    fun plan_days_count_calendar_days_in_the_saved_zone_across_a_dst_change() = runTest {
        // 23:30 in Chicago on Mar 7 (05:30 UTC Mar 8); the clocks jump forward that night.
        val clock = TestClock(Instant.parse("2026-03-08T05:30:00Z"))
        val participants = FakeParticipants().apply {
            profile =
                Result.success(FakeParticipants.profile(LocalDate.of(2026, 3, 9)))
        }
        val vm = viewModel(participants, clock)
        runCurrent()
        assertEquals(LocalDate.of(2026, 3, 7), vm.uiState.value.today)
        assertEquals(PlanStatus.EndsIn(2), vm.uiState.value.plan)
    }

    @Test
    fun plan_status_reads_ends_today_and_ended() {
        val today = LocalDate.of(2026, 11, 1)
        assertEquals(PlanStatus.EndsToday, ParticipantViewModel.planStatus(today, today))
        assertEquals(PlanStatus.EndedAgo(3), ParticipantViewModel.planStatus(today.minusDays(3), today))
        assertEquals(PlanStatus.EndsIn(1), ParticipantViewModel.planStatus(today.plusDays(1), today))
    }
}
