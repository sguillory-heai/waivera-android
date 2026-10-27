package com.waivera.waivera.feature.calendar.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import com.waivera.waivera.core.testing.TestClock
import com.waivera.waivera.core.testing.TestSessionRepository
import com.waivera.waivera.feature.calendar.FakeCalendarRepository
import com.waivera.waivera.feature.calendar.FakeConversationRepository
import com.waivera.waivera.feature.calendar.FakePeopleRepository
import com.waivera.waivera.feature.calendar.data.DeviceZone
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class CalendarEventViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val me = "usr_alex_rivera" // TestSessionRepository's signed-in user
    private val clock = TestClock(Instant.parse("2026-11-01T16:30:00Z"))
    private val calendar = FakeCalendarRepository(clock)
    private val conversations = FakeConversationRepository()
    private val session = TestSessionRepository()
    private val window = CalendarEventViewModel.UNDO_WINDOW_MILLIS

    private val invite = ScheduleEvent(
        id = "invite",
        title = "Care plan review",
        startAt = Instant.parse("2026-11-02T15:00:00Z"),
        endAt = Instant.parse("2026-11-02T16:00:00Z"),
        ownerUserId = "usr_priya",
        ownerName = "Priya Patel",
        online = true,
        meetingDetails = "https://meet.example.test/care-plan",
        myInvitation = MyInvitation.PENDING,
    )

    private class Harness(
        val vm: CalendarEventViewModel,
        val effects: List<EventEffect>,
        val viewModels: ViewModelStore,
    )

    private fun TestScope.open(
        event: ScheduleEvent? = invite,
        id: String = event?.id ?: "missing",
        appScope: CoroutineScope = backgroundScope,
    ): Harness {
        calendar.events = listOfNotNull(event)
        val vm = CalendarEventViewModel(
            eventId = id,
            calendar = calendar,
            conversations = conversations,
            people = FakePeopleRepository(),
            session = session,
            clock = clock,
            deviceZone = DeviceZone { ZoneId.of("America/Chicago") },
            appScope = appScope,
        )
        val effects = mutableListOf<EventEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        // Owning the ViewModel through a store lets a test clear it the way navigation does.
        val viewModels = ViewModelStore()
        ViewModelProvider(
            viewModels,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            },
        )[CalendarEventViewModel::class.java]
        return Harness(vm, effects, viewModels)
    }

    private fun Harness.details(): EventDetails = assertIs<EventUiState.Loaded>(vm.uiState.value).details

    private fun Harness.invitation(): InvitationUi = assertNotNull(details().invitation)

    @Test
    fun loadingUntilTheFeedArrivesThenLoaded() = runTest {
        val gate = CompletableDeferred<Unit>()
        calendar.refreshGate = gate
        val h = open()
        assertEquals(EventUiState.Loading, h.vm.uiState.value)
        gate.complete(Unit)
        assertEquals("Care plan review", h.details().event.title)
    }

    @Test
    fun unknownIdIsNotFoundOnceTheServerAnswers() = runTest {
        val h = open(event = null, id = "gone")
        assertEquals(EventUiState.NotFound, h.vm.uiState.value)
    }

    @Test
    fun coldFailureIsAnErrorAndRetryRecovers() = runTest {
        calendar.refreshError = WaiveraError.Network()
        val h = open()
        assertIs<EventUiState.Error>(h.vm.uiState.value)
        calendar.refreshError = null
        h.vm.retry()
        assertEquals(MyInvitation.PENDING, h.invitation().response)
    }

    @Test
    fun refreshAfterReconnectFlowsIntoTheOpenEventAndAFailedOneKeepsTheCopy() = runTest {
        val h = open()
        // A later refresh (retry, or another screen) flows into the open event.
        calendar.events = listOf(invite.copy(title = "Care plan review (moved)"))
        h.vm.retry()
        assertEquals("Care plan review (moved)", h.details().event.title)

        calendar.refreshError = WaiveraError.Server(503)
        h.vm.retry()
        assertEquals("Care plan review (moved)", h.details().event.title)
        assertNotNull(h.details().refreshError)
    }

    @Test
    fun coldIdIsFetchedByIdWithoutWatchingAnyFeedRange() = runTest {
        val h = open(id = "invite")
        assertEquals("Care plan review", h.details().event.title)
        assertEquals(listOf("invite"), calendar.refreshedEvents)
        assertTrue(calendar.observedRanges.isEmpty(), "no month grids are searched any more")
    }

    @Test
    fun aCachedEventShowsAtOnceWithoutAFetch() = runTest {
        calendar.event("invite").value = Synced(invite, lastSyncedAt = clock.instant())
        val h = open()
        assertEquals("Care plan review", h.details().event.title)
        assertTrue(calendar.refreshedEvents.isEmpty())
    }

    @Test
    fun acceptShowsAtOnceAndIsSentOnlyWhenTheUndoWindowEnds() = runTest {
        val h = open()
        h.vm.respond(accept = true)
        assertEquals(MyInvitation.ACCEPTED, h.invitation().response)
        assertEquals(ResponsePhase.UNDO_WINDOW, h.invitation().phase)
        assertEquals(EventEffect.OfferUndo(accept = true, token = 1), h.effects.last())
        assertTrue(calendar.responses.isEmpty())

        advanceTimeBy(window - 1)
        assertTrue(calendar.responses.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("invite" to true), calendar.responses)
        assertEquals(ResponsePhase.SAVED, h.invitation().phase)
        assertEquals(EventEffect.ResponseSaved(accept = true), h.effects.last())
        // The repository patches every cached copy, so nothing is refetched.
        assertEquals(MyInvitation.ACCEPTED, calendar.event("invite").value.data?.myInvitation)
        assertTrue(calendar.refreshedRanges.isEmpty())
    }

    @Test
    fun undoInsideTheWindowSendsNothing() = runTest {
        val h = open()
        h.vm.respond(accept = false)
        assertEquals(MyInvitation.DECLINED, h.invitation().response)
        h.vm.undo(token = 1)
        assertEquals(MyInvitation.PENDING, h.invitation().response)
        advanceTimeBy(window * 2)
        assertTrue(calendar.responses.isEmpty())
    }

    @Test
    fun aReplacedSnackbarCannotUndoOrCommitTheNewerChoice() = runTest {
        val h = open()
        h.vm.respond(accept = true)
        h.vm.respond(accept = false)
        h.vm.undo(token = 1)
        h.vm.onUndoWindowClosed(token = 1)
        assertEquals(MyInvitation.DECLINED, h.invitation().response)
        assertTrue(calendar.responses.isEmpty())

        h.vm.onUndoWindowClosed(token = 2)
        runCurrent()
        assertEquals(listOf("invite" to false), calendar.responses)
    }

    @Test
    fun failedSaveRollsBackAndRetrySendsTheSameAnswer() = runTest {
        calendar.respondResult = Result.failure(WaiveraError.Network())
        val h = open()
        h.vm.respond(accept = true)
        advanceTimeBy(window + 1)
        runCurrent()
        assertEquals(MyInvitation.PENDING, h.invitation().response)
        assertEquals(true, h.invitation().failedChoiceAccept)
        assertNotNull(h.invitation().failure)
        assertEquals(EventEffect.ResponseFailed, h.effects.last())

        calendar.respondResult = Result.success(Unit)
        h.vm.retryResponse()
        runCurrent()
        assertEquals(listOf("invite" to true, "invite" to true), calendar.responses)
        assertNull(h.invitation().failure)
        assertEquals(MyInvitation.ACCEPTED, h.invitation().response)
    }

    @Test
    fun leavingDuringTheUndoWindowStillSendsTheAnswer() = runTest {
        val h = open()
        h.vm.respond(accept = true)
        h.viewModels.clear()
        runCurrent()
        assertEquals(listOf("invite" to true), calendar.responses)
    }

    @Test
    fun leavingWhileTheAnswerIsSavingLetsTheSaveFinish() = runTest {
        val gate = CompletableDeferred<Unit>()
        calendar.respondGate = gate
        val h = open()
        h.vm.respond(accept = true)
        advanceTimeBy(window + 1)
        runCurrent()
        assertEquals(ResponsePhase.SAVING, h.invitation().phase)
        h.viewModels.clear()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("invite" to true), calendar.responses, "the answer is sent once")
        assertEquals(listOf("invite" to true), calendar.savedResponses, "closing the screen did not cancel it")
    }

    @Test
    fun organizerHasNoAnswerButtons() = runTest {
        val owner = open(invite.copy(ownerUserId = me, myInvitation = MyInvitation.ORGANIZER))
        assertNull(owner.details().invitation)
        assertTrue(owner.details().isOrganizer)
        assertNull(owner.details().chat)
        owner.vm.respond(accept = true)
        assertTrue(owner.effects.isEmpty())
    }

    @Test
    fun startedEventCannotBeAnswered() = runTest {
        clock.now = Instant.parse("2026-11-02T15:05:00Z")
        val started = open()
        started.vm.tick()
        assertEquals(false, started.invitation().canChange)
        started.vm.respond(accept = true)
        assertTrue(calendar.responses.isEmpty())
    }

    @Test
    fun joinOpensTheValidatedLinkOnlyInsideTheWindow() = runTest {
        val h = open()
        assertEquals(JoinState.TOO_EARLY, h.details().join?.state)
        h.vm.join()
        assertTrue(h.effects.none { it is EventEffect.OpenUrl })

        clock.now = Instant.parse("2026-11-02T14:31:00Z") // 29 minutes before the start
        h.vm.tick()
        h.vm.join()
        assertEquals(EventEffect.OpenUrl("https://meet.example.test/care-plan"), h.effects.last())
    }

    @Test
    fun onlineMeetingWithoutALinkSaysSo() = runTest {
        clock.now = Instant.parse("2026-11-02T14:45:00Z")
        val h = open(invite.copy(meetingDetails = "Dial in from the office phone"))
        h.vm.join()
        assertEquals(JoinError.NoLink, h.details().join?.error)
        h.vm.dismissJoinError()
        assertNull(h.details().join?.error)
    }

    @Test
    fun sproutMeetingAsksTheApiForTheLobbyAndRejectsNonWebUrls() = runTest {
        clock.now = Instant.parse("2026-11-02T14:45:00Z")
        val h = open(invite.copy(isSprout = true, meetingDetails = null))
        h.vm.join()
        assertEquals(listOf("invite"), calendar.lobbyRequests)
        assertEquals(EventEffect.OpenUrl("https://meet.example.test/lobby/1"), h.effects.last())

        calendar.lobbyResult = Result.success("javascript:alert(1)")
        h.vm.join()
        assertIs<JoinError.Failed>(h.details().join?.error)
        assertEquals(1, h.effects.count { it is EventEffect.OpenUrl })
    }

    @Test
    fun messageOrganizerOpensTheDirectConversation() = runTest {
        val h = open()
        assertEquals(ChatActionKind.MESSAGE_ORGANIZER, h.details().chat?.kind)
        h.vm.openChat()
        assertEquals(listOf(listOf("usr_priya")), conversations.opened)
        assertEquals(EventEffect.OpenConversation(FakeConversationRepository.OPENED_CONVERSATION_ID), h.effects.last())
    }

    @Test
    fun milestoneLoadedLaterBadgesTheOrganizer() = runTest {
        val h = open()
        assertNull(h.details().organizer?.celebration)
        calendar.milestoneState.value = Synced(
            listOf(
                com.waivera.waivera.core.data.model.Milestone(
                    com.waivera.waivera.core.network.dto.MilestoneType.BIRTHDAY,
                    "usr_priya",
                    "Priya Patel",
                    month = 11,
                    day = 1,
                ),
            ),
            lastSyncedAt = clock.instant(),
        )
        assertNotNull(h.details().organizer?.celebration)
    }
}
