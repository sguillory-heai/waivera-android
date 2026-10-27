package com.waivera.waivera.core.designsystem.theme

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
}
