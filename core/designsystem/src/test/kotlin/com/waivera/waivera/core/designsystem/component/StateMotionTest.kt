package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared press, pop and reject-shake responses on the compose clock: a quick tap still dips
 * visibly, a pop or a shake runs only on a change, a press answers in the innermost control only,
 * and with animations off none of them moves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class StateMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showPress(motion: WaiveraMotionScheme): PressFeedbackState {
        var state: PressFeedbackState? = null
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                state = rememberPressFeedback(remember { MutableInteractionSource() })
            }
        }
        compose.waitForIdle()
        return requireNotNull(state)
    }

    @Test
    fun aTapThatEndsAtOnceStillDipsThenSettles() {
        compose.mainClock.autoAdvance = false
        val press = showPress(WaiveraMotionScheme.Standard)
        compose.runOnUiThread {
            press.onPointer(down = true)
            press.onPointer(down = false)
        }
        compose.mainClock.advanceTimeBy(PressTiming.MIN_VISIBLE_PRESS_MILLIS / 2)
        assertTrue("the tap moved nothing: ${press.value}", press.value < 1f)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, press.value, 0.001f)
    }

    @Test
    fun aHeldPressReachesThePressScale() {
        compose.mainClock.autoAdvance = false
        val press = showPress(WaiveraMotionScheme.Standard)
        compose.runOnUiThread { press.onPointer(down = true) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(WaiveraMotionScheme.PRESS_SCALE, press.value, 0.005f)
    }

    @Test
    fun reducedMotionNeverScalesAPress() {
        compose.mainClock.autoAdvance = false
        val press = showPress(WaiveraMotionScheme.Reduced)
        compose.runOnUiThread { press.onPointer(down = true) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, press.value, 0f)
    }

    /** A row with a control inside it, each with its own press response, as a chat row's Join call. */
    private fun showNested(): Pair<PressFeedbackState, PressFeedbackState> {
        var outer: PressFeedbackState? = null
        var inner: PressFeedbackState? = null
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                val row = rememberPressFeedback(remember { MutableInteractionSource() }).also { outer = it }
                val button = rememberPressFeedback(remember { MutableInteractionSource() }).also { inner = it }
                Box(Modifier.size(ROW_DP.dp).testTag("row").pressFeedback(row)) {
                    Box(Modifier.size(BUTTON_DP.dp).testTag("button").pressFeedback(button))
                }
            }
        }
        compose.waitForIdle()
        return requireNotNull(outer) to requireNotNull(inner)
    }

    @Test
    fun aPressOnANestedControlScalesOnlyThatControl() {
        compose.mainClock.autoAdvance = false
        val (row, button) = showNested()
        compose.onNodeWithTag("button").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(WaiveraMotionScheme.PRESS_SCALE, button.value, 0.005f)
        assertEquals("the row shrank with its button", 1f, row.value, 0f)
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, button.value, 0.001f)
    }

    @Test
    fun aPressBesideTheNestedControlScalesTheRow() {
        compose.mainClock.autoAdvance = false
        val (row, button) = showNested()
        compose.onNodeWithTag("row").performTouchInput { down(bottomRight - Offset(1f, 1f)) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(WaiveraMotionScheme.PRESS_SCALE, row.value, 0.005f)
        assertEquals(1f, button.value, 0f)
        compose.onNodeWithTag("row").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, row.value, 0.001f)
    }

    private fun showPop(motion: WaiveraMotionScheme): Pair<State<Float>, (Int) -> Unit> {
        var scale: State<Float>? = null
        var setKey: ((Int) -> Unit)? = null
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                var key by remember { mutableIntStateOf(0) }
                setKey = { key = it }
                scale = rememberPopScale(key = key)
            }
        }
        compose.waitForIdle()
        return requireNotNull(scale) to requireNotNull(setKey)
    }

    @Test
    fun aPopRunsOnlyWhenTheKeyChanges() {
        compose.mainClock.autoAdvance = false
        val (scale, setKey) = showPop(WaiveraMotionScheme.Standard)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        assertEquals("nothing pops on first show", 1f, scale.value, 0f)
        compose.runOnUiThread { changeKey(setKey) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        assertTrue("the change did not pop: ${scale.value}", scale.value < 1f)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, scale.value, 0.001f)
    }

    @Test
    fun reducedMotionNeverPops() {
        compose.mainClock.autoAdvance = false
        val (scale, setKey) = showPop(WaiveraMotionScheme.Reduced)
        compose.runOnUiThread { changeKey(setKey) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        assertEquals(1f, scale.value, 0f)
    }

    private fun showShake(motion: WaiveraMotionScheme): Pair<State<Float>, (Int) -> Unit> {
        var offset: State<Float>? = null
        var setKey: ((Int) -> Unit)? = null
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                var key by remember { mutableIntStateOf(0) }
                setKey = { key = it }
                offset = rememberRejectShake(key = key)
            }
        }
        compose.waitForIdle()
        return requireNotNull(offset) to requireNotNull(setKey)
    }

    @Test
    fun aRejectShakeRunsOnceOnAChangeAndSettlesInAboutThreeHundredMillis() {
        compose.mainClock.autoAdvance = false
        val (offset, setKey) = showShake(WaiveraMotionScheme.Standard)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        assertEquals("nothing shakes on first show", 0f, offset.value, 0f)
        compose.runOnUiThread { changeKey(setKey) }
        var widest = 0f
        repeat((WaiveraMotionScheme.REJECT_SHAKE_MILLIS / FRAME_MILLIS).toInt()) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            widest = maxOf(widest, abs(offset.value))
        }
        assertTrue("the rejection did not shake: $widest", widest > 0.5f)
        assertTrue("the shake overshot its amplitude: $widest", widest <= 1f)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 4)
        assertEquals("the shake comes to rest", 0f, offset.value, 0.001f)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals("it runs once, not on a loop", 0f, offset.value, 0f)
    }

    @Test
    fun reducedMotionNeverShakes() {
        compose.mainClock.autoAdvance = false
        val (offset, setKey) = showShake(WaiveraMotionScheme.Reduced)
        compose.runOnUiThread { changeKey(setKey) }
        repeat(FRAMES_TO_WATCH) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            assertEquals(0f, offset.value, 0f)
        }
    }

    /**
     * A state write outside composition reaches the recomposer through the global snapshot's
     * apply notification, which Compose posts to the main looper. With the clock paused nothing
     * idles that looper, so the key change would never recompose; send the notification here.
     */
    private fun changeKey(setKey: (Int) -> Unit) {
        setKey(1)
        Snapshot.sendApplyNotifications()
    }

    private companion object {
        const val FRAMES_TO_WATCH = 20
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val ROW_DP = 200
        const val BUTTON_DP = 48
    }
}
