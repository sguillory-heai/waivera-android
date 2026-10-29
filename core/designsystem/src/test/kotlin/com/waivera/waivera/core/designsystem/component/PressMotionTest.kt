package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import com.waivera.waivera.core.designsystem.theme.PressDepth
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
 * The motion pass's press rules on the compose clock (M1-02 to M1-05, M1-12): each control sinks
 * to its own depth, a lift springs back past rest, a press a scroll takes lets go at once, a held
 * long press keeps sinking and pops back as it fires, and with animations off a press answers at
 * once with a tint instead of a scale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class PressMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun showPresses(
        motion: WaiveraMotionScheme,
        depths: List<PressDepth>,
        armsLongPress: Boolean = false,
    ): List<PressFeedbackState> {
        val states = arrayOfNulls<PressFeedbackState>(depths.size)
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                depths.forEachIndexed { index, depth ->
                    states[index] = rememberPressFeedback(
                        interactionSource = remember { MutableInteractionSource() },
                        depth = depth,
                        armsLongPress = armsLongPress,
                    )
                }
            }
        }
        compose.waitForIdle()
        return states.map { requireNotNull(it) }
    }

    private fun showPress(
        motion: WaiveraMotionScheme = WaiveraMotionScheme.Standard,
        depth: PressDepth = PressDepth.Button,
        armsLongPress: Boolean = false,
    ): PressFeedbackState = showPresses(motion, listOf(depth), armsLongPress).single()

    /** The lowest and highest values over [frames] frames. */
    private fun watch(press: PressFeedbackState, frames: Int): ClosedFloatingPointRange<Float> {
        var low = press.value
        var high = press.value
        repeat(frames) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            low = minOf(low, press.value)
            high = maxOf(high, press.value)
        }
        return low..high
    }

    @Test
    fun eachControlSinksToItsOwnDepth() {
        compose.mainClock.autoAdvance = false
        val presses = showPresses(WaiveraMotionScheme.Standard, PressDepth.entries)
        compose.runOnUiThread { presses.forEach { it.onPointer(down = true) } }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        PressDepth.entries.zip(presses).forEach { (depth, press) ->
            assertEquals("$depth", depth.scale, press.value, DEPTH_TOLERANCE)
        }
    }

    @Test
    fun aLiftSpringsBackPastRestThenSettles() {
        compose.mainClock.autoAdvance = false
        val press = showPress()
        compose.runOnUiThread { press.onPointer(down = true) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.runOnUiThread { press.onPointer(down = false) }
        val range = watch(press, RELEASE_FRAMES)
        assertTrue(
            "the release has no spring-back: peak ${range.endInclusive}",
            range.endInclusive > 1f + MIN_OVERSHOOT,
        )
        assertTrue("the release overshoots too far: ${range.endInclusive}", range.endInclusive < 1f + MAX_OVERSHOOT)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, press.value, SETTLED)
    }

    @Test
    fun aPressAScrollTakesLetsGoAtOnceWithoutBouncing() {
        compose.mainClock.autoAdvance = false
        val press = showPress()
        compose.runOnUiThread { press.onPointer(down = true) }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * 2)
        assertTrue("the press never started: ${press.value}", press.value < 1f)
        compose.runOnUiThread { press.onPointer(down = false, cancelled = true) }
        val range = watch(press, CANCEL_FRAMES)
        val dip = 1f - WaiveraMotionScheme.PRESS_SCALE
        assertTrue("still dipped ${CANCEL_FRAMES} frames into the scroll: ${press.value}", press.value > 1f - dip / 4)
        assertTrue("a taken press bounced: ${range.endInclusive}", range.endInclusive <= 1f + SETTLED)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * CANCEL_FRAMES * 2)
        assertEquals(1f, press.value, SETTLED)
    }

    @Test
    fun aQuickLiftStillHoldsItsDipWhereATakenPressHasLetGo() {
        compose.mainClock.autoAdvance = false
        val (lifted, taken) = showPresses(WaiveraMotionScheme.Standard, listOf(PressDepth.Button, PressDepth.Button))
        compose.runOnUiThread {
            lifted.onPointer(down = true)
            taken.onPointer(down = true)
            lifted.onPointer(down = false)
            taken.onPointer(down = false, cancelled = true)
        }
        compose.mainClock.advanceTimeBy(FRAME_MILLIS * CANCEL_FRAMES)
        assertTrue("the lift lost its minimum hold: ${lifted.value}", lifted.value < taken.value)
    }

    @Test
    fun withAnimationsOnAPressDrawsNoTint() {
        compose.mainClock.autoAdvance = false
        val press = showPress()
        compose.runOnUiThread { press.onPointer(down = true) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(0f, press.tintAlpha, 0f)
    }

    @Test
    fun reducedMotionAnswersAPressAtOnceWithATintAndNoScale() {
        compose.mainClock.autoAdvance = false
        val press = showPress(WaiveraMotionScheme.Reduced)
        compose.runOnUiThread { press.onPointer(down = true) }
        assertEquals("no tint on touch-down", WaiveraMotionScheme.PRESSED_TINT_ALPHA, press.tintAlpha, 0f)
        compose.runOnUiThread { press.onPointer(down = false) }
        compose.mainClock.advanceTimeBy(PressTiming.MIN_VISIBLE_PRESS_MILLIS / 2)
        assertEquals("a quick tap's tint left early", WaiveraMotionScheme.PRESSED_TINT_ALPHA, press.tintAlpha, 0f)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(0f, press.tintAlpha, 0f)
        assertEquals(1f, press.value, 0f)
    }

    @Test
    fun aHeldLongPressSinksThenPopsBackAsItFires() {
        compose.mainClock.autoAdvance = false
        val press = showPress(armsLongPress = true)
        compose.runOnUiThread { press.onPointer(down = true) }
        compose.mainClock.advanceTimeBy(WaiveraMotionScheme.LONG_PRESS_ARM_DELAY_MILLIS - FRAME_MILLIS)
        assertTrue(
            "sank before the arm delay: ${press.value}",
            press.value >= PressDepth.Button.scale - DEPTH_TOLERANCE,
        )
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals("the hold did not sink", PressDepth.Button.longPressScale, press.value, DEPTH_TOLERANCE)
        compose.runOnUiThread { press.onLongPress() }
        val range = watch(press, RELEASE_FRAMES)
        assertTrue("the long press did not pop back: ${range.endInclusive}", range.endInclusive > 1f + MIN_OVERSHOOT)
        compose.runOnUiThread { press.onPointer(down = false) }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(1f, press.value, SETTLED)
    }

    @Test
    fun aQuickTapOnALongPressControlNeverSinksPastItsPress() {
        compose.mainClock.autoAdvance = false
        val press = showPress(armsLongPress = true)
        compose.runOnUiThread {
            press.onPointer(down = true)
            press.onPointer(down = false)
        }
        val range = watch(press, (SETTLE_MILLIS / FRAME_MILLIS).toInt())
        assertTrue(
            "a tap sank like a long press: ${range.start}",
            range.start >= PressDepth.Button.scale - DEPTH_TOLERANCE,
        )
        assertEquals(1f, press.value, SETTLED)
    }

    @Test
    fun reducedMotionArmsALongPressWithADeeperTintAndNoScale() {
        compose.mainClock.autoAdvance = false
        val press = showPress(WaiveraMotionScheme.Reduced, armsLongPress = true)
        compose.runOnUiThread { press.onPointer(down = true) }
        compose.mainClock.advanceTimeBy(WaiveraMotionScheme.LONG_PRESS_ARM_DELAY_MILLIS + FRAME_MILLIS * 2)
        assertEquals(WaiveraMotionScheme.LONG_PRESS_TINT_ALPHA, press.tintAlpha, 0f)
        assertEquals(1f, press.value, 0f)
        compose.runOnUiThread {
            press.onLongPress()
            press.onPointer(down = false)
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(0f, press.tintAlpha, 0f)
        assertEquals(1f, press.value, 0f)
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val RELEASE_FRAMES = 30
        const val CANCEL_FRAMES = 4
        const val DEPTH_TOLERANCE = 0.005f
        const val SETTLED = 0.001f

        /** The release peaks near 1.004 from a 0.04 dip, and the long press's pop near 1.011. */
        const val MIN_OVERSHOOT = 0.001f
        const val MAX_OVERSHOOT = 0.02f
    }
}
