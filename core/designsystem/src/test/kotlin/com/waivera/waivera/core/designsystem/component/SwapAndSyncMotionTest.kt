package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A swap draws what arrives whole while its slot grows (M1-01), and a sync line that goes
 * through says "Synced" for one beat before it folds away (M1-07), with animations on or off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class SwapAndSyncMotionTest {
    @get:Rule
    val compose = createComposeRule()

    /** A write from the test reaches the paused recomposer only with the apply notification sent. */
    private fun change(block: () -> Unit) {
        compose.runOnUiThread {
            block()
            Snapshot.sendApplyNotifications()
        }
    }

    @Test
    fun aSwapDrawsTheArrivingContentWholeWhileItsSlotGrows() {
        compose.mainClock.autoAdvance = false
        var setKey: (Int) -> Unit = {}
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                var key by remember { mutableIntStateOf(0) }
                setKey = { key = it }
                Box(Modifier.width(SLOT_DP.dp)) {
                    WaiveraSwap(targetState = key) { shown ->
                        Text(text = if (shown == 0) SHORT else LONG, modifier = Modifier.testTag("line$shown"))
                    }
                }
            }
        }
        compose.waitForIdle()
        change { setKey(1) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        val arriving = compose.onNodeWithTag("line1", useUnmergedTree = true).fetchSemanticsNode()
        val laidOut = arriving.size.width.toFloat()
        val drawn = arriving.boundsInRoot.width
        // The arriving side starts a little small (the swap's pop), never cut to the old width.
        assertTrue(
            "the arriving line is clipped to the growing slot: drawn $drawn of $laidOut px",
            drawn >= laidOut * MIN_DRAWN_SHARE,
        )
    }

    private fun showSync(motion: WaiveraMotionScheme, initial: SyncState): (SyncState) -> Unit {
        var setState: (SyncState) -> Unit = {}
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                var state by remember { mutableStateOf(initial) }
                setState = { state = it }
                WaiveraSyncIndicator(state = state, onRetry = {})
            }
        }
        compose.waitForIdle()
        return setState
    }

    private fun assertShowing(text: String, count: Int) =
        compose.onAllNodesWithText(text, useUnmergedTree = true).assertCountEquals(count)

    private fun aSendThatGoesThroughSaysSyncedForABeatThenFolds(motion: WaiveraMotionScheme) {
        compose.mainClock.autoAdvance = false
        val setState = showSync(motion, SyncState.Pending)
        assertShowing(PENDING, 1)
        change { setState(SyncState.Synced) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        assertShowing(SYNCED, 1)
        compose.mainClock.advanceTimeBy(WaiveraMotionScheme.SYNCED_BEAT_MILLIS - FRAME_MILLIS * 4)
        assertShowing(SYNCED, 1)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertShowing(SYNCED, 0)
        assertShowing(PENDING, 0)
    }

    @Test
    fun aSendThatGoesThroughSaysSyncedForABeatThenFolds() =
        aSendThatGoesThroughSaysSyncedForABeatThenFolds(WaiveraMotionScheme.Standard)

    @Test
    fun withAnimationsOffTheSyncedBeatStillShowsAndFolds() =
        aSendThatGoesThroughSaysSyncedForABeatThenFolds(WaiveraMotionScheme.Reduced)

    @Test
    fun anItemSyncedFromTheStartShowsNoBeat() {
        compose.mainClock.autoAdvance = false
        showSync(WaiveraMotionScheme.Standard, SyncState.Synced)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        assertShowing(SYNCED, 0)
        assertShowing(PENDING, 0)
    }

    @Test
    fun aRetryDuringTheBeatGoesBackToSending() {
        compose.mainClock.autoAdvance = false
        val setState = showSync(WaiveraMotionScheme.Standard, SyncState.Pending)
        change { setState(SyncState.Synced) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        change { setState(SyncState.Pending) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertShowing(PENDING, 1)
        assertShowing(SYNCED, 0)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val SLOT_DP = 320
        const val SHORT = "Sent"
        const val LONG = "Not sent · Tap to retry, or discard it"
        const val PENDING = "Sending…"
        const val SYNCED = "Synced"

        /** The swap's pop starts the arriving side at 0.92 scale; a clipped side shows far less. */
        const val MIN_DRAWN_SHARE = 0.9f
    }
}
