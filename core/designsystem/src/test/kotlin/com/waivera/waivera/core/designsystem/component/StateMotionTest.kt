package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared press and pop responses on the compose clock: a quick tap still dips visibly, a pop
 * runs only on a change, and with animations off neither moves anything.
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
        compose.runOnUiThread { setKey(1) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        assertTrue("the change did not pop: ${scale.value}", scale.value < 1f)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, scale.value, 0.001f)
    }

    @Test
    fun reducedMotionNeverPops() {
        compose.mainClock.autoAdvance = false
        val (scale, setKey) = showPop(WaiveraMotionScheme.Reduced)
        compose.runOnUiThread { setKey(1) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        assertEquals(1f, scale.value, 0f)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
    }
}
