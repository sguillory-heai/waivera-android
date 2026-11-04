package com.waivera.waivera.feature.tasks

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.LeaveBalance
import com.waivera.waivera.core.data.model.ProfileField
import com.waivera.waivera.core.data.model.RequestKind
import com.waivera.waivera.core.data.model.RequestStatus
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.model.TimeOffReview
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.data.model.TrainingDecision
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.feature.tasks.files.TaskFileExport
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
        val vm = RequestsViewModel(requests, web, DemoGraph(backgroundScope).calendar, clock, NoFiles, NoExport)
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

    @Test
    fun time_off_shows_the_balance_and_partial_days_in_the_leave_zone_across_the_clock_change() = runTest {
        // 06:30 UTC on Nov 1 2026 is 1:30 AM CDT; 12:00 UTC is 6:00 AM CST, after clocks fell back.
        val timeOff = timeOff(partial = true, minutes = 330)
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(1, RequestKind.TimeOff).copy(timeOff = timeOff))
            leaveZone = ZoneId.of("America/Chicago")
            balance = Result.success(LeaveBalance(leaveTypeId = 7, tracksBalance = true, availableMinutes = 960))
        }
        val vm = viewModel(requests)
        runCurrent()
        // The card already uses the leave zone, before any review opens.
        assertEquals(
            TaskFormat.Span("1:30 AM", "6:00 AM"),
            assertIs<RequestChange.TimeOff>(vm.waitingItem().change).times,
        )
        vm.review(vm.waitingItem())
        runCurrent()
        val change = assertIs<RequestChange.TimeOff>(vm.uiState.value.reviewing?.change)
        assertEquals(TaskFormat.Span("1:30 AM", "6:00 AM"), change.times)
        assertEquals("Vacation", change.leaveType)
        // A pending request already holds its minutes, so "before" adds them back.
        assertEquals(Loadable.Ready(BalanceChange(before = 1290, after = 960)), vm.uiState.value.balance)
    }

    @Test
    fun a_cancellation_gives_the_hours_back_and_a_hidden_balance_is_left_out() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(
                FakeRequests.request(1, RequestKind.TimeOffCancellation).copy(timeOff = timeOff(partial = false, 480)),
                FakeRequests.request(2, RequestKind.TimeOff).copy(timeOff = timeOff(partial = false, 480)),
            )
            balance = Result.success(LeaveBalance(leaveTypeId = 7, tracksBalance = true, availableMinutes = 120))
        }
        val vm = viewModel(requests)
        runCurrent()
        val (cancel, ask) = assertIs<Loadable.Ready<List<RequestItem>>>(vm.uiState.value.waiting).value
        vm.review(cancel)
        runCurrent()
        assertEquals(Loadable.Ready(BalanceChange(before = 120, after = 600)), vm.uiState.value.balance)
        requests.balance = Result.success(null)
        vm.review(ask)
        runCurrent()
        assertEquals(Loadable.Ready(null), vm.uiState.value.balance)
    }

    @Test
    fun rejecting_training_needs_a_reason_and_sends_it_with_the_revision() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(6, RequestKind.TrainingCredit))
            training = Result.success(credit(needsApproveReason = false))
        }
        val vm = viewModel(requests)
        runCurrent()
        vm.review(vm.waitingItem())
        runCurrent()
        vm.ask(approve = false)
        assertEquals(false, vm.canConfirm())
        vm.confirm()
        runCurrent()
        assertEquals(emptyList<Pair<Int, Boolean>>(), requests.decisions)
        vm.setReason("  The completion date is cut off. ")
        assertEquals(true, vm.canConfirm())
        vm.confirm()
        runCurrent()
        assertEquals(listOf(6 to false), requests.decisions)
        assertEquals(
            listOf<TrainingDecision?>(TrainingDecision(4, "The completion date is cut off.")),
            requests.trainingDecisions,
        )
    }

    @Test
    fun approving_training_you_uploaded_yourself_needs_a_reason_too() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(6, RequestKind.TrainingCredit))
            training = Result.success(credit(needsApproveReason = true))
        }
        val vm = viewModel(requests)
        runCurrent()
        vm.review(vm.waitingItem())
        runCurrent()
        vm.ask(approve = true)
        assertEquals(false, vm.canConfirm())
        vm.setReason("Checked the original with the trainer.")
        assertEquals(true, vm.canConfirm())
    }

    @Test
    fun a_training_credit_that_cannot_be_decided_here_never_confirms() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(6, RequestKind.TrainingCredit))
            training = Result.success(credit(needsApproveReason = false).copy(canApprove = false))
        }
        val vm = viewModel(requests)
        runCurrent()
        vm.review(vm.waitingItem())
        runCurrent()
        vm.ask(approve = true)
        assertEquals(false, vm.canConfirm())
    }

    @Test
    fun a_profile_change_lists_changed_fields_first_with_dates_worded() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(
                FakeRequests.request(3, RequestKind.ProfileChange).copy(
                    profile = listOf(
                        ProfileField(ProfileField.Field.FirstName, "Luis", "Luis", changed = false),
                        ProfileField(ProfileField.Field.CpocEnd, "2027-01-31", "2027-03-31", changed = true),
                    ),
                ),
            )
        }
        val vm = viewModel(requests)
        runCurrent()
        val change = assertIs<RequestChange.Profile>(vm.waitingItem().change)
        assertEquals(listOf(ProfileRow(ProfileField.Field.CpocEnd, "Jan 31, 2027", "Mar 31, 2027")), change.changed)
        assertEquals(listOf(ProfileRow(ProfileField.Field.FirstName, "Luis", "Luis")), change.unchanged)
    }

    @Test
    fun a_balance_that_fails_to_load_offers_a_retry() = runTest {
        val requests = FakeRequests().apply {
            serverWaiting = listOf(FakeRequests.request(1, RequestKind.TimeOff).copy(timeOff = timeOff(false, 480)))
            balance = Result.failure(WaiveraError.Network())
        }
        val vm = viewModel(requests)
        runCurrent()
        vm.review(vm.waitingItem())
        runCurrent()
        assertEquals(Loadable.Failed, vm.uiState.value.balance)

        requests.balance = Result.success(LeaveBalance(leaveTypeId = 7, tracksBalance = true, availableMinutes = 600))
        vm.retryDetails()
        runCurrent()
        assertEquals(Loadable.Ready(BalanceChange(before = 1080, after = 600)), vm.uiState.value.balance)
    }

    private fun timeOff(partial: Boolean, minutes: Int) = TimeOffReview(
        leaveType = "Vacation",
        leaveTypeId = 7,
        partialDay = partial,
        startDate = LocalDate.of(2026, 11, 1),
        endDate = LocalDate.of(2026, 11, 1),
        start = Instant.parse("2026-11-01T06:30:00Z"),
        end = Instant.parse("2026-11-01T12:00:00Z"),
        minutes = minutes,
        note = null,
        impact = null,
        impactChanged = false,
        acknowledged = null,
    )

    private fun credit(needsApproveReason: Boolean) = TrainingCredit(
        requestId = 6,
        course = "CPR recertification",
        employeeName = "Jordan Lee",
        trainer = null,
        provider = null,
        completed = null,
        hours = 2.0,
        pending = true,
        revision = 4,
        canApprove = true,
        needsApproveReason = needsApproveReason,
        uploadedBy = null,
        blocked = null,
        reviewOnWeb = false,
    )

    private fun RequestsViewModel.waitingItem() =
        assertIs<Loadable.Ready<List<RequestItem>>>(uiState.value.waiting).value.single()

    private object NoFiles : TaskFileRepository {
        override suspend fun open(taskId: Int, file: TaskAttachment): Result<File> =
            Result.failure(IllegalStateException())

        override suspend fun export(taskId: Int, file: TaskAttachment): Result<File> =
            Result.failure(IllegalStateException())

        override suspend fun certificate(requestId: Int): Result<File> = Result.failure(IllegalStateException())

        override fun close(file: File) = Unit
    }

    private object NoExport : TaskFileExport {
        override suspend fun saveTo(file: File, destinationUri: String): Result<Unit> = Result.success(Unit)

        override suspend fun shareableUri(file: File, fileName: String): Result<String> = Result.success("")

        override fun mimeType(fileName: String): String = "application/pdf"
    }
}
