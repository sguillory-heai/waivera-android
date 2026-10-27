package com.waivera.waivera.feature.calendar.screenshot

import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.calendar.EventContent
import com.waivera.waivera.feature.calendar.event.EventActions
import com.waivera.waivera.feature.calendar.event.EventInteraction
import com.waivera.waivera.feature.calendar.event.EventRules
import com.waivera.waivera.feature.calendar.event.EventUiState
import com.waivera.waivera.feature.calendar.event.JoinError
import com.waivera.waivera.feature.calendar.event.OptimisticResponse
import com.waivera.waivera.feature.calendar.event.ResponseFailure
import com.waivera.waivera.feature.calendar.event.ResponsePhase
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The event screen on the demo events, with the states the ViewModel produces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class CalendarEventScreenshotTest {
    private val events = DemoData.events().associateBy { it.id }
    private val milestones = MilestoneIndex(DemoData.milestones())

    private fun loaded(
        id: String,
        now: Instant = DemoData.NOW,
        interaction: EventInteraction = EventInteraction(),
        event: ScheduleEvent = events.getValue(id),
    ) = EventUiState.Loaded(
        EventRules.details(
            event = event,
            zone = DemoData.ZONE,
            milestones = milestones,
            now = now,
            myUserId = DemoData.ME,
            savedZoneId = DemoData.ZONE.id,
            deviceZone = DemoData.ZONE,
            interaction = interaction,
        ),
    )

    private fun capture(name: String, state: EventUiState) =
        captureScreenshots(name) { EventContent(state = state, actions = EventActions()) }

    @Test
    fun invited() = capture("calendarEvent_invited", loaded("demo_invite"))

    @Test
    fun acceptedWithUndo() = capture(
        "calendarEvent_accepted",
        loaded(
            "demo_invite",
            interaction = EventInteraction(optimistic = OptimisticResponse(true, ResponsePhase.UNDO_WINDOW)),
        ),
    )

    @Test
    fun responseFailed() = capture(
        "calendarEvent_responseFailed",
        loaded("demo_invite", interaction = EventInteraction(failure = ResponseFailure(false, WaiveraError.Network()))),
    )

    @Test
    fun organizer() = capture("calendarEvent_organizer", loaded("demo_visit"))

    @Test
    fun sproutMeetingOpen() = capture(
        "calendarEvent_joinOpen",
        loaded("demo_supervision", now = Instant.parse("2026-11-01T19:45:00Z")),
    )

    @Test
    fun joinTooEarly() = capture("calendarEvent_joinTooEarly", loaded("demo_supervision"))

    @Test
    fun joinError() = capture(
        "calendarEvent_joinError",
        loaded(
            "demo_supervision",
            now = Instant.parse("2026-11-01T19:45:00Z"),
            interaction = EventInteraction(joinError = JoinError.Failed(WaiveraError.Network())),
        ),
    )

    @Test
    fun overnightAcrossFallBack() = capture("calendarEvent_overnight", loaded("demo_overnight"))

    @Test
    fun loading() = capture("calendarEvent_loading", EventUiState.Loading)

    @Test
    fun notFound() = capture("calendarEvent_notFound", EventUiState.NotFound)

    @Test
    fun error() = capture("calendarEvent_error", EventUiState.Error(WaiveraError.Server(SERVER_ERROR)))

    @Test
    fun errorOffline() = capture("calendarEvent_errorOffline", EventUiState.Error(WaiveraError.Network()))

    private companion object {
        const val SERVER_ERROR = 503
    }
}
