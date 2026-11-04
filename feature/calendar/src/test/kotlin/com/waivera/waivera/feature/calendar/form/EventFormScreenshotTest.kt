package com.waivera.waivera.feature.calendar.form

import com.waivera.waivera.core.data.model.AttendeeOption
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The New event sheet's fields across the shared matrix. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class EventFormScreenshotTest {
    private val ready = EventFormUiState(
        phase = FormPhase.Ready,
        title = "Team huddle",
        date = LocalDate.of(2026, 11, 4),
        start = LocalTime.of(15, 0),
        end = LocalTime.of(15, 30),
        zone = ZoneId.of("America/Chicago"),
        invitees = listOf(AttendeeOption("u1", "Priya Raman", null), AttendeeOption("u2", "Jordan Lee", null)),
    )

    @Test
    fun newEvent() = captureScreenshots("event_form_new", heightDp = 1100) {
        WaiveraSheetFrame(title = "New event") { EventFormFields(ready, EventFormActions()) }
    }

    @Test
    fun newEventErrorsAndPlace() = captureScreenshots("event_form_errors", heightDp = 1100) {
        WaiveraSheetFrame(title = "New event") {
            EventFormFields(
                ready.copy(
                    title = "",
                    end = LocalTime.of(14, 0),
                    showErrors = true,
                    waiveraVideo = false,
                    saveFailed = true,
                ),
                EventFormActions(),
            )
        }
    }
}
