package com.waivera.waivera.feature.profile.timeoff

import app.cash.turbine.test
import com.waivera.waivera.core.data.model.LeaveBlock
import com.waivera.waivera.core.data.model.LeaveDraft
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveSendException
import com.waivera.waivera.core.data.model.LeaveSendProblem
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.demo.DemoRequestRepository
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.feature.profile.TestClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** The Request time off form follows sprout_web's AdminRequestModal: checks, preview, send. */
class TimeOffFormViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeLeave(demo: RequestRepository) : RequestRepository by demo {
        var types: Result<List<LeaveType>> = Result.success(listOf(VACATION, JURY))
        var preview: Result<LeavePreview> = Result.success(READY)
        var send: Result<Unit> = Result.success(Unit)
        val previews = mutableListOf<LeaveDraft>()
        val sent = mutableListOf<LeaveDraft>()

        override suspend fun leaveTypes() = types

        override suspend fun previewLeave(draft: LeaveDraft): Result<LeavePreview> {
            previews += draft
            return preview
        }

        override suspend fun requestTimeOff(draft: LeaveDraft): Result<Unit> {
            sent += draft
            return send
        }
    }

    private fun TestScope.setUp(): Pair<FakeLeave, TimeOffFormViewModel> {
        val graph = DemoGraph(backgroundScope)
        val leave = FakeLeave(DemoRequestRepository(graph.backend))
        val vm = TimeOffFormViewModel(leave, graph.calendar, TestClock)
        runCurrent()
        return leave to vm
    }

    @Test
    fun leave_types_load_and_a_failure_can_be_retried() = runTest {
        val graph = DemoGraph(backgroundScope)
        val leave = FakeLeave(DemoRequestRepository(graph.backend)).apply {
            types =
                Result.failure(WaiveraError.Network())
        }
        val vm = TimeOffFormViewModel(leave, graph.calendar, TestClock)
        runCurrent()
        assertEquals(LeaveTypes.Failed, vm.state.value.types)

        leave.types = Result.success(listOf(VACATION))
        vm.loadTypes()
        runCurrent()
        assertEquals(LeaveTypes.Ready(listOf(VACATION)), vm.state.value.types)
    }

    @Test
    fun full_days_preview_with_the_webs_utc_bounds_after_a_pause() = runTest {
        val (leave, vm) = setUp()
        vm.pickType(VACATION.id)
        vm.pickFirstDay(LocalDate.of(2026, 11, 24))
        vm.pickLastDay(LocalDate.of(2026, 11, 26))
        runCurrent()
        assertEquals(LeaveCheck.Checking, vm.state.value.check)
        assertTrue(leave.previews.isEmpty(), "waits 250 ms like the web")

        advanceTimeBy(PAUSE)
        runCurrent()
        val draft = leave.previews.single()
        assertEquals(Instant.parse("2026-11-24T00:00:00Z"), draft.start)
        assertEquals(Instant.parse("2026-11-26T23:59:59.999Z"), draft.end)
        assertEquals(LeaveCheck.Ready(READY), vm.state.value.check)
        assertTrue(vm.state.value.canSend)
    }

    @Test
    fun quick_changes_ask_the_server_once() = runTest {
        val (leave, vm) = setUp()
        vm.pickType(VACATION.id)
        vm.pickFirstDay(LocalDate.of(2026, 11, 24))
        vm.pickLastDay(LocalDate.of(2026, 11, 25))
        vm.pickLastDay(LocalDate.of(2026, 11, 26))
        advanceTimeBy(PAUSE)
        runCurrent()
        assertEquals(1, leave.previews.size)
    }

    @Test
    fun a_partial_day_uses_the_saved_zone_across_the_dst_change() = runTest {
        val (leave, vm) = setUp()
        vm.pickType(VACATION.id)
        vm.setPartialDay(true)
        // Nov 1 2026 in Chicago: clocks fall back at 2:00, so 1:00 to 3:00 is three real hours.
        vm.pickDay(LocalDate.of(2026, 11, 1))
        vm.pickFrom(LocalTime.of(1, 0))
        vm.pickTo(LocalTime.of(3, 0))
        advanceTimeBy(PAUSE)
        runCurrent()
        val draft = leave.previews.single()
        assertTrue(draft.partialDay)
        assertEquals(Instant.parse("2026-11-01T06:00:00Z"), draft.start)
        assertEquals(Instant.parse("2026-11-01T09:00:00Z"), draft.end)
    }

    @Test
    fun local_issues_stop_the_check_and_the_send() = runTest {
        val (leave, vm) = setUp()
        vm.pickType(VACATION.id)
        vm.setPartialDay(true)
        vm.pickDay(LocalDate.of(2026, 11, 20))
        vm.pickFrom(LocalTime.of(9, 0))
        vm.pickTo(LocalTime.of(9, 15))
        assertEquals(LeaveIssue.TooShort, vm.state.value.issue)
        vm.pickTo(LocalTime.of(17, 30))
        assertEquals(LeaveIssue.TooLong, vm.state.value.issue)
        advanceTimeBy(PAUSE)
        runCurrent()
        assertTrue(leave.previews.isEmpty())
        assertFalse(vm.state.value.canSend)
    }

    @Test
    fun a_first_day_after_the_last_moves_the_last_day() = runTest {
        val (_, vm) = setUp()
        vm.pickLastDay(LocalDate.of(2026, 11, 20))
        vm.pickFirstDay(LocalDate.of(2026, 11, 24))
        assertEquals(LocalDate.of(2026, 11, 24), vm.state.value.lastDay)
        assertNull(vm.state.value.issue)
    }

    @Test
    fun past_dates_are_allowed_with_a_note() = runTest {
        val (_, vm) = setUp()
        vm.pickFirstDay(LocalDate.of(2026, 10, 30))
        assertTrue(vm.state.value.past)
        vm.pickFirstDay(LocalDate.of(2026, 11, 1))
        assertFalse(vm.state.value.past, "today is not in the past")
    }

    @Test
    fun blocked_or_over_balance_dates_cannot_be_sent() = runTest {
        val (leave, vm) = setUp()
        leave.preview = Result.success(READY.copy(requestedMinutes = 0, block = LeaveBlock.Holiday))
        fillFullDays(vm)
        assertFalse(vm.state.value.canSend)

        leave.preview = Result.success(READY.copy(afterMinutes = -480))
        vm.retryCheck()
        advanceTimeBy(PAUSE)
        runCurrent()
        assertTrue(vm.state.value.overBalance)
        assertFalse(vm.state.value.canSend)
    }

    @Test
    fun a_failed_check_can_be_retried() = runTest {
        val (leave, vm) = setUp()
        leave.preview = Result.failure(WaiveraError.Network())
        fillFullDays(vm)
        assertEquals(LeaveCheck.Failed, vm.state.value.check)
        leave.preview = Result.success(READY)
        vm.retryCheck()
        advanceTimeBy(PAUSE)
        runCurrent()
        assertIs<LeaveCheck.Ready>(vm.state.value.check)
    }

    @Test
    fun sending_hands_the_profile_a_pending_request() = runTest {
        val (leave, vm) = setUp()
        fillFullDays(vm)
        vm.setNote("  Family trip ")
        vm.effects.test {
            vm.send()
            val sent = assertIs<TimeOffFormEffect.Sent>(awaitItem()).request
            assertEquals(LocalDate.of(2026, 11, 24), sent.start)
            assertEquals("Vacation", sent.leaveType)
            assertEquals("Family trip", sent.comment)
        }
        assertEquals("Family trip", leave.sent.single().note)
    }

    @Test
    fun a_refused_send_keeps_the_form_and_says_why() = runTest {
        val (leave, vm) = setUp()
        leave.send = Result.failure(LeaveSendException(LeaveSendProblem.Probation))
        fillFullDays(vm)
        vm.effects.test {
            vm.send()
            assertEquals(TimeOffFormEffect.SendFailed(LeaveSendProblem.Probation), awaitItem())
        }
        assertFalse(vm.state.value.sending)
        assertTrue(vm.state.value.canSend)
    }

    private fun TestScope.fillFullDays(vm: TimeOffFormViewModel) {
        vm.pickType(VACATION.id)
        vm.pickFirstDay(LocalDate.of(2026, 11, 24))
        vm.pickLastDay(LocalDate.of(2026, 11, 26))
        advanceTimeBy(PAUSE)
        runCurrent()
    }

    private companion object {
        const val PAUSE = 251L
        val VACATION = LeaveType(7, "Vacation", tracksBalance = true, availableMinutes = 2400)
        val JURY = LeaveType(8, "Jury duty", tracksBalance = false, availableMinutes = null)
        val READY = LeavePreview(
            requestedMinutes = 1440,
            tracksBalance = true,
            beforeMinutes = 2400,
            afterMinutes = 960,
            block = null,
            impact = null,
        )
    }
}
