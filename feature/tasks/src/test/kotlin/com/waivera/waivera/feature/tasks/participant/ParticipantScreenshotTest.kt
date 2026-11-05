package com.waivera.waivera.feature.tasks.participant

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.tasks.Loadable
import com.waivera.waivera.feature.tasks.taskRow
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The read-only participant profile (mockups F1 to F3). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class ParticipantScreenshotTest {
    private val today = ParticipantPreviewData.today
    private val rows = listOf(
        FakeParticipants.summary(11, LocalDate.of(2026, 10, 28)).copy(title = "Quarterly home visit"),
        FakeParticipants.summary(12, LocalDate.of(2026, 11, 9)).copy(title = "Annual plan review"),
        FakeParticipants.summary(13, null).copy(title = "Update emergency contacts"),
    ).map { taskRow(it, today, null) }

    @Test
    fun profile() = shot("participant_profile", ParticipantPreviewData.state(tasks = Loadable.Ready(rows)))

    @Test
    fun attention() = shot(
        "participant_attention",
        ParticipantPreviewData.state(
            profile = ParticipantPreviewData.profile.copy(
                planEnd = LocalDate.of(2026, 10, 31),
                compliant = false,
                changePending = true,
                deactivated = true,
                email = null,
                region = null,
            ),
        ),
    )

    @Test
    fun tasksFailed() =
        shot("participant_tasks_failed", ParticipantPreviewData.state(tasks = Loadable.Failed), heightDp = 1200)

    @Test
    fun forbidden() =
        shot("participant_forbidden", ParticipantUiState(phase = ParticipantPhase.Forbidden), heightDp = 700)

    @Test
    fun error() = shot("participant_error", ParticipantUiState(phase = ParticipantPhase.Error), heightDp = 700)

    private fun shot(name: String, state: ParticipantUiState, heightDp: Int = 1500) =
        captureScreenshots(name, heightDp = heightDp) {
            Content(state)
        }

    @Composable
    private fun Content(state: ParticipantUiState) {
        ParticipantContent(state = state, snackbar = remember { SnackbarHostState() }, actions = ParticipantActions())
    }
}
