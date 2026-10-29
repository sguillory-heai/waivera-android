package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import org.robolectric.annotation.GraphicsMode

/**
 * A swap changed again while its last side is still arriving (M3-02): Pending to Failed and back,
 * two quick calendar steps, two quick bookmark removals. The side that was arriving leaves on the
 * quick exit, not on Compose's default spring, so two sides never show together. Each side is a
 * solid square of its own colour in its own place, so the drawn pixels give each side's opacity.
 *
 * A side that fully arrived is not at risk and must keep its own exit fade (M4-01): the guard
 * against interruptions used to square every exit, so a settled swap blanked for a frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class SwapInterruptionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(motion: WaiveraMotionScheme): (Int) -> Unit {
        var setSide: (Int) -> Unit = {}
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                var side by remember { mutableIntStateOf(0) }
                setSide = { side = it }
                Box(Modifier.background(Color.White)) {
                    WaiveraSwap(targetState = side) { shown ->
                        Box(Modifier.size(CELL_DP.dp * SIDES.size, CELL_DP.dp)) {
                            Box(
                                Modifier
                                    .offset(x = CELL_DP.dp * shown)
                                    .size(CELL_DP.dp)
                                    .background(SIDES[shown]),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return { next ->
            compose.runOnUiThread {
                setSide(next)
                Snapshot.sendApplyNotifications()
            }
        }
    }

    /** Each side's opacity, read at the middle of its square over the white background. */
    private fun opacities(): List<Float> {
        val pixels: PixelMap = compose.onRoot().captureToImage().toPixelMap()
        val cell = with(compose.density) { CELL_DP.dp.toPx() }
        return SIDES.indices.map { side ->
            val color = pixels[(cell * side + cell / 2).toInt(), (cell / 2).toInt()]
            // Red on white keeps its red channel and loses green; green and blue lose red.
            1f - if (side == 0) color.green else color.red
        }
    }

    private fun aReTargetedSwapNeverShowsTwoSides(motion: WaiveraMotionScheme, afterMillis: Long) {
        compose.mainClock.autoAdvance = false
        val setSide = show(motion)
        setSide(1)
        compose.mainClock.advanceTimeBy(afterMillis)
        setSide(2)
        repeat(WATCHED_FRAMES) { frame ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            val shown = opacities()
            val label = "reduced=${motion.reduced}, re-targeted after $afterMillis ms, frame ${frame + 1}: $shown"
            assertTrue("two sides show together: $label", shown.sum() <= MAX_COMBINED_ALPHA)
            // The frame that composes the change starts the exit; 48 ms on, it is nearly gone.
            if (frame * FRAME_MILLIS >= GONE_BY_MILLIS) {
                assertTrue("the interrupted side is still showing: $label", shown[1] < MOSTLY_GONE)
            }
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val settled = opacities()
        assertTrue("the last side never arrived: $settled", settled[2] > ARRIVED)
        assertTrue("an old side stayed: $settled", settled[0] < MOSTLY_GONE && settled[1] < MOSTLY_GONE)
    }

    @Test
    fun aSettledSideFadesOutOnItsOwnExit() {
        compose.mainClock.autoAdvance = false
        val setSide = show(WaiveraMotionScheme.Standard)
        setSide(1)
        compose.mainClock.advanceTimeBy(SETTLED_AFTER_MILLIS)
        setSide(2)
        val leaving = mutableListOf<Float>()
        repeat(SETTLED_WATCHED_FRAMES) { frame ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            val shown = opacities()
            val label = "settled swap, frame ${frame + 1}: $shown"
            assertTrue("two sides show together: $label", shown.sum() <= MAX_COMBINED_ALPHA)
            leaving += shown[1]
        }
        val trace = leaving.joinToString()
        // The exit starts a frame or two after the change composes; its first faded frame is
        // 16 ms in, about 0.4 on the exit's own fade and about 0.16 on the squared one.
        val firstFaded = leaving.firstOrNull { it < STILL_FULL }
        assertTrue("the settled side never started to leave: $trace", firstFaded != null)
        assertTrue(
            "the settled side cut instead of fading: $firstFaded on its first faded frame ($trace)",
            requireNotNull(firstFaded) >= SETTLED_FIRST_FRAME_MIN,
        )
        leaving.zipWithNext().forEachIndexed { frame, (before, after) ->
            assertTrue(
                "the settled side came back at frame ${frame + 2}: $after after $before ($trace)",
                after <= before + FADE_TOLERANCE,
            )
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val settled = opacities()
        assertTrue("the last side never arrived: $settled", settled[2] > ARRIVED)
        assertTrue("an old side stayed: $settled", settled[0] < MOSTLY_GONE && settled[1] < MOSTLY_GONE)
    }

    @Test
    fun aSwapReTargetedMidArrivalNeverShowsTwoSides() =
        aReTargetedSwapNeverShowsTwoSides(WaiveraMotionScheme.Standard, afterMillis = MID_ARRIVAL_MILLIS)

    @Test
    fun aSwapReTargetedAsItSettlesNeverShowsTwoSides() =
        aReTargetedSwapNeverShowsTwoSides(WaiveraMotionScheme.Standard, afterMillis = LATE_ARRIVAL_MILLIS)

    @Test
    fun withAnimationsOffAReTargetedSwapNeverShowsTwoSides() =
        aReTargetedSwapNeverShowsTwoSides(WaiveraMotionScheme.Reduced, afterMillis = MID_ARRIVAL_MILLIS)

    private companion object {
        const val CELL_DP = 24
        val SIDES = listOf(Color.Red, Color.Green, Color.Blue)
        const val FRAME_MILLIS = 16L
        const val WATCHED_FRAMES = 20
        const val SETTLE_MILLIS = 1_000L
        const val MID_ARRIVAL_MILLIS = 120L

        // Late in the arrival but before it finishes (drawn at about 0.98). From about 210 ms the
        // side is latched as settled and leaves on its own exit, which aSettledSideFadesOutOnItsOwnExit
        // covers, so 250 ms no longer re-targeted an arriving side.
        const val LATE_ARRIVAL_MILLIS = 176L

        const val GONE_BY_MILLIS = 48L
        const val MAX_COMBINED_ALPHA = 1.05f
        const val MOSTLY_GONE = 0.1f
        const val ARRIVED = 0.95f

        /** Well past the arrival's pop and fade, so the side has settled. */
        const val SETTLED_AFTER_MILLIS = 600L
        const val SETTLED_WATCHED_FRAMES = 8

        /** Above this the leaving side has not started its fade yet. */
        const val STILL_FULL = 0.98f

        /** The exit's own 60 ms fade leaves about 0.4 after one frame; the squared fade left about 0.16. */
        const val SETTLED_FIRST_FRAME_MIN = 0.3f

        /** A fading side never gets brighter; this only absorbs 8-bit rounding. */
        const val FADE_TOLERANCE = 0.02f
    }
}
