package com.waivera.waivera.core.designsystem.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WaiveraMotionSchemeTest {
    @Test
    fun `animator scale zero selects the reduced scheme`() {
        assertSame(WaiveraMotionScheme.Reduced, WaiveraMotionScheme.forAnimatorScale(0f))
        assertSame(WaiveraMotionScheme.Standard, WaiveraMotionScheme.forAnimatorScale(1f))
        assertSame(WaiveraMotionScheme.Standard, WaiveraMotionScheme.forAnimatorScale(0.5f))
        assertSame(WaiveraMotionScheme.Standard, WaiveraMotionScheme.forAnimatorScale(10f))
    }

    @Test
    fun `standard press is a fast spatial spring to 96 percent`() {
        val motion = WaiveraMotionScheme.Standard
        val press = assertIs<SpringSpec<*>>(motion.press<Float>())
        assertEquals(0.9f, press.dampingRatio)
        assertEquals(1400f, press.stiffness)
        assertEquals(0.96f, motion.pressScale)
        assertTrue(motion.allowsLoopingMotion)
    }

    @Test
    fun `standard tokens are springs`() {
        val motion = WaiveraMotionScheme.Standard
        MotionToken.entries.forEach { token -> assertIs<SpringSpec<*>>(motion.spec<Float>(token), "$token") }
        assertEquals(1f, assertIs<SpringSpec<*>>(motion.state<Float>()).dampingRatio, "state never overshoots")
        assertEquals(700f, assertIs<SpringSpec<*>>(motion.layout<Float>()).stiffness)
        assertEquals(0.8f, assertIs<SpringSpec<*>>(motion.celebrate<Float>()).dampingRatio)
    }

    @Test
    fun `reduced motion snaps spatial tokens and fades effects`() {
        val motion = WaiveraMotionScheme.Reduced
        assertIs<SnapSpec<*>>(motion.press<Float>())
        assertIs<SnapSpec<*>>(motion.layout<Float>())
        listOf(MotionToken.State, MotionToken.Navigate, MotionToken.Celebrate).forEach { token ->
            val fade = assertIs<TweenSpec<*>>(motion.spec<Float>(token), "$token")
            assertEquals(WaiveraMotionScheme.REDUCED_FADE_MILLIS, fade.durationMillis)
        }
        assertEquals(1f, motion.pressScale, "no press scale when animations are off")
        assertFalse(motion.allowsLoopingMotion, "no skeleton pulse when animations are off")
    }

    @Test
    fun `the reject shake is a 300 ms keyframe run, and snaps when animations are off`() {
        val shake = assertIs<KeyframesSpec<Float>>(WaiveraMotionScheme.Standard.rejectShake)
        assertEquals(WaiveraMotionScheme.REJECT_SHAKE_MILLIS, shake.config.durationMillis)
        assertEquals(300, WaiveraMotionScheme.REJECT_SHAKE_MILLIS)
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.rejectShake)
    }

    @Test
    fun `the pop is a quick bouncy spring, and snaps when animations are off`() {
        val pop = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.pop)
        assertTrue(pop.dampingRatio < 1f, "a pop overshoots a little")
        assertTrue(pop.stiffness >= WaiveraMotionScheme.SEND_POP_STIFFNESS, "a pop is at least as quick as a send")
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.pop)
    }

    @Test
    fun `a swap slides the way the change went and only fades when animations are off`() {
        val forward = WaiveraMotionScheme.Standard.swapTransition(direction = 1)
        assertTrue("Slide(" in forward.targetContentEnter.toString(), "forward slides in")
        val inPlace = WaiveraMotionScheme.Standard.swapTransition(direction = 0)
        assertFalse("Slide(" in inPlace.targetContentEnter.toString(), "an in-place swap does not slide")
        listOf(-1, 0, 1).forEach { direction ->
            val reduced = WaiveraMotionScheme.Reduced.swapTransition(direction)
            listOf(reduced.targetContentEnter.toString(), reduced.initialContentExit.toString()).forEach { part ->
                assertFalse("Slide(" in part, "nothing slides with animations off ($direction)")
                assertFalse("Scale(" in part, "nothing scales with animations off ($direction)")
            }
        }
    }

    @Test
    fun `press depths follow control size, and none moves when animations are off`() {
        assertEquals(0.985f, PressDepth.Row.scale)
        assertEquals(0.97f, PressDepth.Card.scale)
        assertEquals(WaiveraMotionScheme.PRESS_SCALE, PressDepth.Button.scale, "buttons keep the 0.96 press")
        assertEquals(0.90f, PressDepth.Compact.scale)
        val depths = PressDepth.entries.map { it.scale }
        assertEquals(depths.sortedDescending(), depths, "the larger the control, the shallower the press")
        PressDepth.entries.forEach { depth ->
            assertTrue(depth.longPressScale < depth.scale, "a long press sinks past the press: $depth")
            assertEquals(depth.scale, WaiveraMotionScheme.Standard.pressScaleFor(depth))
            assertEquals(depth.longPressScale, WaiveraMotionScheme.Standard.longPressScaleFor(depth))
            assertEquals(1f, WaiveraMotionScheme.Reduced.pressScaleFor(depth), "reduced press: $depth")
            assertEquals(1f, WaiveraMotionScheme.Reduced.longPressScaleFor(depth), "reduced long press: $depth")
        }
        assertEquals(0.93f, PressDepth.Button.longPressScale)
    }

    @Test
    fun `the release springs back past rest, softer than the press, and snaps when animations are off`() {
        val press = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.press<Float>())
        val release = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.pressRelease<Float>())
        assertEquals(0.6f, release.dampingRatio)
        assertEquals(800f, release.stiffness)
        assertTrue(release.dampingRatio < press.dampingRatio, "the release bounces more than the press")
        assertTrue(release.stiffness < press.stiffness, "the release is softer than the press")
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.pressRelease<Float>())
    }

    @Test
    fun `a taken press lets go quickly without a bounce, and snaps when animations are off`() {
        val cancel = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.pressCancel<Float>())
        assertEquals(1f, cancel.dampingRatio, "no overshoot while the list scrolls")
        assertEquals(2400f, cancel.stiffness)
        val press = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.press<Float>())
        assertTrue(cancel.stiffness > press.stiffness, "letting go is quicker than pressing")
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.pressCancel<Float>())
    }

    @Test
    fun `the long-press sink keeps an even pace over the time it is given, and snaps when animations are off`() {
        val sink = assertIs<TweenSpec<*>>(WaiveraMotionScheme.Standard.longPressSink(durationMillis = 250))
        assertEquals(250, sink.durationMillis)
        // M2-06: an ease-out sat still for its last third, so the bubble stalled before the pop.
        assertSame(LinearEasing, sink.easing)
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.longPressSink(durationMillis = 250))
    }

    @Test
    fun `leaving content drops off in 60 ms, and waiting content starts as it clears`() {
        val exit = assertIs<TweenSpec<*>>(WaiveraMotionScheme.Standard.exit<Float>())
        assertEquals(WaiveraMotionScheme.EXIT_MILLIS, exit.durationMillis)
        assertEquals(60, WaiveraMotionScheme.EXIT_MILLIS)
        assertSame(LinearOutSlowInEasing, exit.easing, "the leaving side drops off at once (M2-02)")
        val after = assertIs<TweenSpec<*>>(WaiveraMotionScheme.Standard.enterAfterExit<Float>())
        assertTrue(after.delay in 1 until WaiveraMotionScheme.EXIT_MILLIS, "starts once the exit is mostly done")
        val leftAtStart = 1f - WaiveraMotionScheme.Standard.exit<Float>().valueAt(after.delay.toLong(), 0f, 1f)
        assertTrue(leftAtStart < MOSTLY_GONE, "the leaving side still shows $leftAtStart when the next one starts")
        listOf(
            WaiveraMotionScheme.Reduced.exit<Float>(),
            WaiveraMotionScheme.Reduced.enterAfterExit<Float>(),
        ).forEach { reduced ->
            val fade = assertIs<TweenSpec<*>>(reduced)
            assertEquals(WaiveraMotionScheme.REDUCED_FADE_MILLIS, fade.durationMillis)
            assertEquals(0, fade.delay, "nothing waits with animations off")
        }
    }

    @Test
    fun `an in-place swap never clips what arrives, and a slide is masked by its slot`() {
        listOf(WaiveraMotionScheme.Standard, WaiveraMotionScheme.Reduced).forEach { motion ->
            val inPlace = assertNotNull(motion.swapTransition(direction = 0).sizeTransform)
            assertFalse(inPlace.clip, "an in-place swap clips its content (reduced=${motion.reduced})")
            listOf(-1, 1).forEach { direction ->
                SwapAxis.entries.forEach { axis ->
                    val size = assertNotNull(motion.swapTransition(direction, axis).sizeTransform)
                    // M2-04: an unclipped month slid past its card. With animations off nothing
                    // slides, and the size snaps, so there is nothing to mask.
                    assertEquals(
                        !motion.reduced,
                        size.clip,
                        "a slide's mask (reduced=${motion.reduced}, direction $direction, $axis)",
                    )
                }
            }
            val load = motion.loadTransition()
            assertFalse(assertNotNull(load.sizeTransform).clip, "the load swap clips (reduced=${motion.reduced})")
            listOf(load.targetContentEnter.toString(), load.initialContentExit.toString()).forEach { part ->
                assertFalse("Slide(" in part, "skeleton to content never slides")
                assertFalse("Scale(" in part, "skeleton to content never scales")
            }
        }
    }

    // Neither spec class overrides toString, so the fade's spec shows as its class name.
    @Test
    fun `both sides of a swap fade on the fade-through tweens, not the state spring`() {
        val tweenFade = Regex("""Fade\(alpha=[0-9.]+, animationSpec=[\w.]*TweenSpec@""")
        val snapFade = Regex("""Fade\(alpha=[0-9.]+, animationSpec=[\w.]*SnapSpec""")
        val standard = listOf(-1, 0, 1).flatMap { direction ->
            SwapAxis.entries.map { axis -> WaiveraMotionScheme.Standard.swapTransition(direction, axis) }
        } + WaiveraMotionScheme.Standard.loadTransition()
        standard.forEach { swap ->
            val exit = swap.initialContentExit.toString()
            val enter = swap.targetContentEnter.toString()
            assertTrue(tweenFade.containsMatchIn(exit), "the leaving side fades on the exit tween: $exit")
            assertTrue(tweenFade.containsMatchIn(enter), "the arriving side fades on the delayed tween: $enter")
        }
        val reduced = listOf(-1, 0, 1).map { WaiveraMotionScheme.Reduced.swapTransition(it) } +
            WaiveraMotionScheme.Reduced.loadTransition()
        reduced.forEach { swap ->
            val exit = swap.initialContentExit.toString()
            assertTrue(snapFade.containsMatchIn(exit), "with animations off the leaving side goes at once: $exit")
        }
    }

    /**
     * M2-02. The busy button's label and progress use the same pair ([swapExitFade] out,
     * [swapEnterFade] in), so this covers the swap, the load swap and the button, with animations
     * on and off.
     */
    @Test
    fun `the old side of a swap is gone before the new side is halfway, and never shows with it`() {
        listOf(WaiveraMotionScheme.Standard, WaiveraMotionScheme.Reduced).forEach { motion ->
            val enter = motion.swapEnterFade<Float>()
            val exit = motion.swapExitFade<Float>()
            for (millis in 0L..FADE_WINDOW_MILLIS) {
                val arriving = enter.valueAt(millis, from = 0f, to = 1f)
                val leaving = 1f - exit.valueAt(millis, from = 0f, to = 1f)
                val label = "reduced=${motion.reduced} at $millis ms: arriving $arriving, leaving $leaving"
                assertTrue(arriving + leaving <= MAX_COMBINED_ALPHA, "both sides show: $label")
                if (arriving >= HALF) {
                    assertEquals(0f, leaving, ALPHA_TOLERANCE, "the old side is still there: $label")
                }
            }
        }
        val reducedExit = WaiveraMotionScheme.Reduced.swapExitFade<Float>()
        assertEquals(1f, reducedExit.valueAt(0L, from = 0f, to = 1f), 0f, "with animations off it goes at once")
    }

    @Test
    fun `the pop and the press end on fine thresholds, so their small overshoots play out`() {
        val pop = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.pop)
        assertEquals<Any?>(WaiveraMotionScheme.POP_VISIBILITY_THRESHOLD, pop.visibilityThreshold)
        assertEquals(0.001f, WaiveraMotionScheme.POP_VISIBILITY_THRESHOLD)
        val press = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.press<Float>())
        val pressIn = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.pressIn(FINE_THRESHOLD))
        assertEquals(press.dampingRatio, pressIn.dampingRatio, "the way in is the press spring")
        assertEquals(press.stiffness, pressIn.stiffness, "the way in is the press spring")
        assertEquals<Any?>(FINE_THRESHOLD, pressIn.visibilityThreshold)
        val cancel = assertIs<SpringSpec<*>>(WaiveraMotionScheme.Standard.pressCancel(FINE_THRESHOLD))
        assertEquals<Any?>(FINE_THRESHOLD, cancel.visibilityThreshold)
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.pressIn(FINE_THRESHOLD))
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.pressCancel(FINE_THRESHOLD))
    }

    private fun FiniteAnimationSpec<Float>.valueAt(millis: Long, from: Float, to: Float): Float =
        vectorize(Float.VectorConverter).getValueFromNanos(
            millis * NANOS_PER_MILLI,
            AnimationVector1D(from),
            AnimationVector1D(to),
            AnimationVector1D(0f),
        ).value

    @Test
    fun `the reject shake eases into each turn and swings 8dp`() {
        assertEquals(8.dp, WaiveraSpacing.ShakeAmplitude)
        val shake = WaiveraMotionScheme.Standard.rejectShake.vectorize(Float.VectorConverter)
        val rest = AnimationVector1D(0f)
        fun at(millis: Long): Float = shake.getValueFromNanos(millis * NANOS_PER_MILLI, rest, rest, rest).value
        // Halfway to the first turn: a linear segment would be at -0.5; eased it is most of the way.
        assertTrue(at(HALFWAY_TO_FIRST_TURN_MILLIS) < EASED_HALFWAY, "the first swing is linear: ${at(20)}")
        assertEquals(-1f, at(FIRST_TURN_MILLIS), 0.001f)
        assertEquals(0f, at(WaiveraMotionScheme.REJECT_SHAKE_MILLIS.toLong()), 0.001f)
    }

    @Test
    fun `the skeleton breathes on an eased 900 ms pulse`() {
        val pulse = WaiveraMotionScheme.Standard.skeletonPulse
        val breath = assertIs<TweenSpec<Float>>(pulse.animation)
        assertEquals(WaiveraMotionScheme.SKELETON_PULSE_MILLIS, breath.durationMillis)
        assertEquals(900, WaiveraMotionScheme.SKELETON_PULSE_MILLIS)
        assertSame(FastOutSlowInEasing, breath.easing, "a linear pulse reads mechanical")
        assertEquals(RepeatMode.Reverse, pulse.repeatMode)
        assertFalse(WaiveraMotionScheme.Reduced.allowsLoopingMotion, "and it holds still with animations off")
    }

    // Transition data is internal to Compose; the public toString() names each part (Fade(...), Scale(...)).
    @Test
    fun `predictive back moves nothing itself and keeps the leaving screen on top`() {
        listOf(WaiveraMotionScheme.Standard, WaiveraMotionScheme.Reduced).forEach { motion ->
            val transform = motion.predictiveBackTransition()
            val label = "reduced=${motion.reduced}"
            listOf(transform.targetContentEnter.toString(), transform.initialContentExit.toString()).forEach { part ->
                assertFalse("Scale(" in part, "nothing shrinks in the transition: $label")
                assertFalse("Slide(" in part, "nothing slides in the transition: $label")
                assertFalse(
                    Regex("""Fade\(alpha=(?!1\.0,)""").containsMatchIn(part),
                    "both screens stay opaque: $label",
                )
            }
            assertTrue(transform.targetContentZIndex < 0f, "leaving screen draws on top: $label")
        }
    }

    @Test
    fun `predictive back holds both screens for the settle only when animations are on`() {
        val standard = WaiveraMotionScheme.Standard.predictiveBackTransition()
        assertTrue("Fade(alpha=1.0," in standard.initialContentExit.toString(), "an opaque hold keeps the card")
        assertTrue("Fade(alpha=1.0," in standard.targetContentEnter.toString(), "an opaque hold keeps the List")
        val reduced = WaiveraMotionScheme.Reduced.predictiveBackTransition()
        assertEquals(EnterTransition.None, reduced.targetContentEnter, "nothing moves underneath")
        // UI3-10: no shrink either, so no unframed sliver of the screen underneath shows.
        assertEquals(ExitTransition.None, reduced.initialContentExit, "the leaving screen holds until commit")
    }

    @Test
    fun `the gesture curve is linear, and flat when animations are off`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            assertEquals(progress, WaiveraMotionScheme.Standard.gesture.progress.transform(progress), "at $progress")
            assertEquals(0f, WaiveraMotionScheme.Reduced.gesture.progress.transform(progress), "reduced at $progress")
        }
    }

    @Test
    fun `the settle takes the share of the gesture time the swipe had left`() {
        val half = assertIs<TweenSpec<*>>(WaiveraMotionScheme.Standard.gesture.settle(remainingFraction = 0.5f))
        assertEquals(WaiveraMotionScheme.BACK_GESTURE_MILLIS / 2, half.durationMillis)
        val over = assertIs<TweenSpec<*>>(WaiveraMotionScheme.Standard.gesture.settle(remainingFraction = 1.5f))
        assertEquals(WaiveraMotionScheme.BACK_GESTURE_MILLIS, over.durationMillis)
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.gesture.settle(remainingFraction = 0.5f))
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val FADE_WINDOW_MILLIS = 300L
        const val MAX_COMBINED_ALPHA = 1.05f
        const val HALF = 0.5f
        const val ALPHA_TOLERANCE = 0.001f
        const val MOSTLY_GONE = 0.1f
        const val FINE_THRESHOLD = 0.0005f
        const val FIRST_TURN_MILLIS = 40L
        const val HALFWAY_TO_FIRST_TURN_MILLIS = 20L

        /** A linear first swing is at -0.5 halfway; the eased one is near -0.78. */
        const val EASED_HALFWAY = -0.65f
    }
}
