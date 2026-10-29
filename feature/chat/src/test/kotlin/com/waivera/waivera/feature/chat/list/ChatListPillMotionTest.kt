package com.waivera.waivera.feature.chat.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Connecting" pill grows in after its wait and folds away when the connection is up, and
 * the search field and list below move with it instead of jumping its 32dp in one frame (M3-03).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class ChatListPillMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(initial: ConnectionUi, pillDelayMillis: Long): (ConnectionUi) -> Unit {
        var setConnection: (ConnectionUi) -> Unit = {}
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                var connection by remember { mutableStateOf(initial) }
                setConnection = { connection = it }
                ChatListContent(
                    state = ChatListPreviewData.state.copy(connection = connection),
                    avatars = emptyMap(),
                    actions = ChatListActions(),
                    skeletonDelayMillis = 0,
                    connectingPillDelayMillis = pillDelayMillis,
                )
            }
        }
        compose.waitForIdle()
        return { next ->
            compose.runOnUiThread {
                setConnection(next)
                Snapshot.sendApplyNotifications()
            }
        }
    }

    /** The top of the filter control, which sits under the search field. */
    private fun filterTop(): Float =
        compose.onAllNodesWithText(FILTER, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.top

    /** Watches the filter for [frames] frames and checks it moved, and never by much of that in one frame. */
    private fun assertMovesSmoothly(what: String, frames: Int = WATCHED_FRAMES) {
        val tops = mutableListOf(filterTop())
        repeat(frames) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            tops += filterTop()
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        tops += filterTop()
        val travel = abs(tops.last() - tops.first())
        assertTrue("$what moved nothing: $tops", travel > MIN_TRAVEL_PX)
        val steps = tops.zipWithNext { a, b -> abs(b - a) }
        assertTrue(
            "$what jumped ${steps.max()} of $travel px in one frame: $tops",
            steps.max() <= travel * MAX_STEP_SHARE,
        )
    }

    @Test
    fun theConnectingPillFoldsAwaySmoothlyWhenTheConnectionIsUp() {
        compose.mainClock.autoAdvance = false
        val setConnection = show(ConnectionUi.Connecting, pillDelayMillis = 0)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        setConnection(ConnectionUi.Online)
        assertMovesSmoothly("the pill leaving")
    }

    @Test
    fun theConnectingPillGrowsInSmoothlyAfterItsWait() {
        compose.mainClock.autoAdvance = false
        show(ConnectionUi.Connecting, pillDelayMillis = PILL_DELAY_MILLIS)
        compose.mainClock.advanceTimeBy(PILL_DELAY_MILLIS - FRAME_MILLIS * 2)
        assertMovesSmoothly("the pill arriving", frames = WATCHED_FRAMES + 2)
    }

    private companion object {
        const val FILTER = "Channels"
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val WATCHED_FRAMES = 24
        const val PILL_DELAY_MILLIS = 400L

        /** The pill and its gap are about 32dp, 64px at xhdpi. */
        const val MIN_TRAVEL_PX = 40f

        /** The layout spring moves about a quarter of the way in its biggest frame; a cut moves all of it. */
        const val MAX_STEP_SHARE = 0.4f
    }
}
