package com.waivera.waivera.feature.today.screenshot

import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.today.notices.NoticeFilter
import com.waivera.waivera.feature.today.notices.NoticesContent
import com.waivera.waivera.feature.today.notices.NoticesPhase
import com.waivera.waivera.feature.today.notices.NoticesPreviewStates
import com.waivera.waivera.feature.today.notices.NoticesUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The notifications inbox across the shared matrix, named `notices_<state>`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class NoticesScreenshotTest {
    private fun capture(name: String, state: NoticesUiState) = captureScreenshots("notices_$name") {
        NoticesContent(state = state, callbacks = NoticesPreviewStates.callbacks)
    }

    @Test
    fun populated() = capture("populated", NoticesPreviewStates.populated())

    @Test
    fun caughtUp() = capture("caught_up", NoticesUiState(phase = NoticesPhase.Empty))

    @Test
    fun filteredEmpty() = capture(
        "filtered_empty",
        NoticesUiState(phase = NoticesPhase.Empty, filter = NoticeFilter.Meetings, totalCount = 4),
    )

    @Test
    fun error() = capture("error", NoticesUiState(phase = NoticesPhase.Error))

    @Test
    fun offlineWithRefreshFailed() = capture(
        "offline",
        NoticesPreviewStates.populated().copy(connection = ConnectionBannerState.Offline, refreshFailed = true),
    )
}
