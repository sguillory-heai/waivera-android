package com.waivera.waivera.feature.tasks

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class RequestsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val web = NetworkConfig(apiBaseUrl = "https://api.example.com", webBaseUrl = "https://app.example.com")

    private fun TestScope.viewModel(requests: FakeRequests, clock: TestClock = TestClock()): RequestsViewModel {
        val vm = RequestsViewModel(requests, web, DemoGraph(backgroundScope).calendar, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun loads_what_waits_on_the_person_and_their_own_requests() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(1))
            mineResult = Result.success(listOf(FakeRequests.request(9, RequestKind.Closure, RequestStatus.Approved)))
        }
        val vm = viewModel(requests)
        runCurrent()
        val state = vm.uiState.value
        val waiting = assertIs<Loadable.Ready<List<RequestItem>>>(state.waiting).value.single()
        assertEquals(RequestChange.DueMove(from = "Thu, Nov 5", to = "Fri, Nov 20", daysLater = 15), waiting.change)
        assertEquals(listOf(9), assertIs<Loadable.Ready<List<RequestItem>>>(state.mine).value.map { it.id })
    }

    @Test
    fun loading_shows_until_the_server_answers() = runTest {
        val requests = FakeRequests().apply { waitingGate = CompletableDeferred() }
        val vm = viewModel(requests)
        runCurrent()
        assertEquals(Loadable.Loading, vm.uiState.value.waiting)
        requests.waitingGate?.complete(Unit)
        runCurrent()
        assertEquals(Loadable.Ready(emptyList()), vm.uiState.value.waiting)
    }

    @Test
    fun nothing_waiting_is_an_empty_list_not_an_error() = runTest {
        val vm = viewModel(FakeRequests())
        runCurrent()
        assertEquals(Loadable.Ready(emptyList()), vm.uiState.value.waiting)
        assertEquals(Loadable.Ready(emptyList()), vm.uiState.value.mine)
    }

    @Test
    fun a_failed_load_shows_the_error_and_retry_recovers() = runTest {
        val requests = FakeRequests().apply {
            waitingResult = Result.failure(WaiveraError.Network())
            mineResult = Result.failure(WaiveraError.Network())
        }
        val vm = viewModel(requests)
        runCurrent()
        assertEquals(Loadable.Failed, vm.uiState.value.waiting)
        assertEquals(Loadable.Failed, vm.uiState.value.mine)

        requests.waitingResult = Result.success(Unit)
        requests.mineResult = Result.success(emptyList())
        vm.retryWaiting()
        runCurrent()
        assertEquals(Loadable.Ready(emptyList()), vm.uiState.value.waiting)
        assertEquals(Loadable.Ready(emptyList()), vm.uiState.value.mine)
    }

    @Test
    fun approving_asks_first_then_removes_the_card_at_once_and_confirms() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(1), FakeRequests.request(2, RequestKind.Delay))
            decideGate = CompletableDeferred()
        }
        val vm = viewModel(requests)
        runCurrent()
        val item = assertIs<Loadable.Ready<List<RequestItem>>>(vm.uiState.value.waiting).value.first()
        vm.review(item)
        vm.ask(approve = true)
        assertEquals(Decision(item, approve = true), vm.uiState.value.confirming)

        vm.effects.test {
            vm.confirm()
            runCurrent()
            // Optimistic: the card and both sheets are gone before the server answers.
            assertNull(vm.uiState.value.reviewing)
            assertNull(vm.uiState.value.confirming)
            assertEquals(listOf(2), vm.waitingIds())
            requests.decideGate?.complete(Unit)
            assertEquals(RequestsEffect.Decided(approve = true, requester = "Priya Raman"), awaitItem())
        }
        assertEquals(listOf(1 to true), requests.decisions)
    }

    @Test
    fun a_failed_decision_puts_the_card_back_and_says_so() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(1))
            decideResult = Result.failure(WaiveraError.Network())
        }
        val vm = viewModel(requests)
        runCurrent()
        val item = assertIs<Loadable.Ready<List<RequestItem>>>(vm.uiState.value.waiting).value.single()
        vm.review(item)
        vm.ask(approve = false)
        vm.effects.test {
            vm.confirm()
            assertEquals(RequestsEffect.DecideFailed(approve = false), awaitItem())
        }
        assertEquals(listOf(1), vm.waitingIds())
    }

    @Test
    fun someone_else_deciding_first_is_not_an_error() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(1))
            decideResult = Result.failure(WaiveraError.NotFound())
        }
        val vm = viewModel(requests)
        runCurrent()
        val item = assertIs<Loadable.Ready<List<RequestItem>>>(vm.uiState.value.waiting).value.single()
        vm.review(item)
        vm.ask(approve = true)
        vm.effects.test {
            vm.confirm()
            assertEquals(RequestsEffect.AlreadyDecided, awaitItem())
        }
    }

    @Test
    fun requests_the_phone_does_not_review_open_on_the_web() = runTest {
        val requests = FakeRequests().apply { serverWaiting = listOf(FakeRequests.request(3, RequestKind.Other)) }
        val vm = viewModel(requests)
        runCurrent()
        val item = assertIs<Loadable.Ready<List<RequestItem>>>(vm.uiState.value.waiting).value.single()
        vm.effects.test {
            vm.review(item)
            assertEquals(RequestsEffect.OpenWeb("https://app.example.com/admin-requests"), awaitItem())
        }
        assertNull(vm.uiState.value.reviewing)
    }

    @Test
    fun a_refresh_after_a_reconnect_brings_in_new_requests() = runTest {
        val requests = FakeRequests()
        val vm = viewModel(requests)
        runCurrent()
        requests.serverWaiting = listOf(FakeRequests.request(4))
        vm.refresh()
        runCurrent()
        assertEquals(listOf(4), vm.waitingIds())
        assertEquals(false, vm.uiState.value.isRefreshing)
    }

    @Test
    fun when_it_was_asked_reads_in_the_saved_zone_across_the_fall_back_change() = runTest {
        // 05:30 UTC on Nov 2 2026 is 11:30 PM CST on Nov 1 in Chicago, after clocks fell back; a
        // fixed -5 offset would call it 12:30 AM on Nov 2.
        val clock = TestClock(Instant.parse("2026-11-02T05:45:00Z"))
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(1).copy(requestedAt = Instant.parse("2026-11-02T05:30:00Z")))
        }
        val vm = viewModel(requests, clock)
        runCurrent()
        val asked = assertIs<Loadable.Ready<List<RequestItem>>>(vm.uiState.value.waiting).value.single().asked
        assertEquals(TaskFormat.RelativeDay.Today, asked?.relativeDay)
        assertEquals("11:30 PM", asked?.time)
    }

    private fun RequestsViewModel.waitingIds() =
        assertIs<Loadable.Ready<List<RequestItem>>>(uiState.value.waiting).value.map { it.id }
}
