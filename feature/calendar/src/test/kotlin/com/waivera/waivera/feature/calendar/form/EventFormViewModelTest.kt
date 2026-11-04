package com.waivera.waivera.feature.calendar.form

import com.waivera.waivera.core.data.model.Attendee
import com.waivera.waivera.core.data.model.AttendeeOption
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.feature.calendar.FakeCalendarRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class EventFormViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    // 10:10 AM CST on Wed Nov 4 2026.
    private val clock = TestClock(Instant.parse("2026-11-04T16:10:00Z"))
    private val calendar = FakeCalendarRepository(clock).apply {
        attendeeOptions = Result.success(
            listOf(AttendeeOption("usr_priya", "Priya Raman", "priya@example.com"), AttendeeOption("usr_jordan", "Jordan Lee", null)),
        )
    }
    private val config = NetworkConfig(apiBaseUrl = "https://api.example.com", webBaseUrl = "https://app.example.com")

    private fun TestScope.form(request: EventFormRequest): Pair<EventFormViewModel, List<EventFormEffect>> {
        val vm = EventFormViewModel(request, calendar, config, clock)
        val effects = mutableListOf<EventFormEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        runCurrent()
        return vm to effects
    }

    @Test
    fun a_new_event_today_starts_at_the_next_half_hour_in_the_saved_zone() = runTest {
        val (vm, _) = form(EventFormRequest(date = LocalDate.of(2026, 11, 4)))
        val state = vm.uiState.value
        assertEquals(FormPhase.Ready, state.phase)
        assertEquals(LocalTime.of(10, 30), state.start)
        assertEquals(LocalTime.of(11, 0), state.end)
    }

    @Test
    fun a_new_event_on_another_day_starts_at_nine() = runTest {
        val (vm, _) = form(EventFormRequest(date = LocalDate.of(2026, 11, 9)))
        assertEquals(LocalTime.of(9, 0), vm.uiState.value.start)
    }

    @Test
    fun moving_the_start_keeps_the_length() = runTest {
        val (vm, _) = form(EventFormRequest(date = LocalDate.of(2026, 11, 9)))
        vm.setEnd(LocalTime.of(10, 0))
        vm.setStart(LocalTime.of(13, 0))
        assertEquals(LocalTime.of(14, 0), vm.uiState.value.end)
    }

    @Test
    fun a_title_and_an_end_after_the_start_are_needed() = runTest {
        val (vm, _) = form(EventFormRequest(date = LocalDate.of(2026, 11, 9)))
        vm.save()
        assertTrue(vm.uiState.value.showErrors)
        vm.setTitle("Huddle")
        vm.setEnd(LocalTime.of(8, 0))
        vm.save()
        assertTrue(vm.uiState.value.endBeforeStart)
        assertTrue(calendar.created.isEmpty())
    }

    @Test
    fun search_finds_coworkers_and_an_invite_clears_the_search() = runTest {
        val (vm, _) = form(EventFormRequest(date = LocalDate.of(2026, 11, 9)))
        vm.setQuery("pri")
        assertEquals(listOf("usr_priya"), vm.uiState.value.matches.map { it.id })
        vm.invite(vm.uiState.value.matches.single())
        assertEquals("", vm.uiState.value.query)
        vm.setQuery("pri")
        assertTrue(vm.uiState.value.matches.isEmpty())
    }

    @Test
    fun saving_sends_the_draft_and_reports_it() = runTest {
        val (vm, effects) = form(EventFormRequest(date = LocalDate.of(2026, 11, 9)))
        vm.setTitle("Huddle")
        vm.invite(AttendeeOption("usr_jordan", "Jordan Lee", null))
        vm.save()
        runCurrent()
        val draft = calendar.created.single()
        assertEquals(listOf("usr_jordan"), draft.inviteeIds)
        assertTrue(draft.waiveraVideo)
        assertEquals(EventFormEffect.Saved(created = true), effects.last())
    }

    @Test
    fun a_failed_save_keeps_the_form_and_shows_why() = runTest {
        calendar.saveResult = Result.failure(WaiveraError.Network())
        val gate = CompletableDeferred<Unit>()
        calendar.saveGate = gate
        val (vm, effects) = form(EventFormRequest(date = LocalDate.of(2026, 11, 9)))
        vm.setTitle("Huddle")
        vm.save()
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.saveFailed)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals("Huddle", vm.uiState.value.title)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun an_edit_shows_the_event_in_the_saved_zone_after_the_fall_back_change() = runTest {
        calendar.events = listOf(
            ScheduleEvent(
                id = "e1",
                title = "Huddle",
                // 9 AM CST on Nov 2 (UTC-6, after clocks fell back on Nov 1).
                startAt = Instant.parse("2026-11-02T15:00:00Z"),
                endAt = Instant.parse("2026-11-02T15:30:00Z"),
                ownerUserId = "usr_alex_rivera",
                attendees = listOf(Attendee("a1", AttendeeType.EMPLOYEE, userId = "usr_priya", displayName = "Priya Raman")),
                isSprout = true,
                location = "Room 2",
                myInvitation = MyInvitation.ORGANIZER,
            ),
        )
        val (vm, effects) = form(EventFormRequest(eventId = "e1"))
        val state = vm.uiState.value
        assertTrue(state.isEdit)
        assertEquals(LocalDate.of(2026, 11, 2), state.date)
        assertEquals(LocalTime.of(9, 0), state.start)
        assertFalse(state.waiveraVideo)
        assertEquals("Room 2", state.location)
        assertEquals(listOf("usr_priya"), state.invitees.map { it.id })
        vm.save()
        runCurrent()
        assertEquals("e1", calendar.updated.single().first)
        assertEquals(EventFormEffect.Saved(created = false), effects.last())
    }
}
