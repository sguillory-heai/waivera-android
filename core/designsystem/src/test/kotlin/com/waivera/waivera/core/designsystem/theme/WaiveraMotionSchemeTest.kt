package com.waivera.waivera.core.designsystem.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
}
