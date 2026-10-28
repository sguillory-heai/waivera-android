package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.core.SnapSpec
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class BrandMotionTest {
    @Test
    fun confetti_isDeterministicForASeed() {
        assertEquals(Confetti.pieces(seed = 3), Confetti.pieces(seed = 3))
        assertEquals(Confetti.PIECES, Confetti.pieces(seed = 0).size)
    }

    @Test
    fun confetti_drawsNothingWithReducedMotionOrOutsideTheBurst() {
        assertFalse(Confetti.isVisible(reduced = true, progress = 0.5f))
        assertFalse(Confetti.isVisible(reduced = false, progress = 0f))
        assertFalse(Confetti.isVisible(reduced = false, progress = 1f))
        assertTrue(Confetti.isVisible(reduced = false, progress = 0.5f))
    }

    @Test
    fun confetti_risesThenFallsAndFadesOut() {
        val piece = ConfettiPiece(angle = -1.5707964f, reach = 1f, spin = 1f, colorIndex = 0, delay = 0f, round = false)
        val early = Confetti.frame(piece, 0.2f)
        val late = Confetti.frame(piece, 0.95f)
        assertTrue(early.y < 0f, "a piece launched upward starts above the origin")
        assertTrue(late.y > early.y, "gravity brings it back down")
        assertEquals(1f, early.alpha)
        assertTrue(late.alpha < 1f && late.alpha > 0f, "it fades over the last third")
        assertEquals(0f, Confetti.frame(piece, 1f).alpha)
    }

    @Test
    fun confetti_waitsForItsDelay() {
        val piece = ConfettiPiece(angle = 0f, reach = 1f, spin = 0f, colorIndex = 0, delay = 0.1f, round = true)
        assertEquals(0f, Confetti.frame(piece, 0.05f).alpha)
    }

    @Test
    fun reducedMotion_snapsTheBurstAndTheSendPop() {
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.celebrateBurst)
        assertIs<SnapSpec<*>>(WaiveraMotionScheme.Reduced.sendPop)
    }

    @Test
    fun bubbleArrival_ownSendPopsFromSmallerAndSettlesAtRest() {
        val start = BubbleArrival.frame(progress = 0f, mine = true)
        val rest = BubbleArrival.frame(progress = 1f, mine = true)
        assertEquals(BubbleArrival.SEND_START_SCALE, start.scale)
        assertEquals(1f, start.offsetFraction)
        assertEquals(BubbleArrivalFrame(scale = 1f, offsetFraction = 0f, alpha = 1f), rest)
    }

    @Test
    fun bubbleArrival_othersGlideWithoutScaling() {
        val start = BubbleArrival.frame(progress = 0f, mine = false)
        assertEquals(1f, start.scale)
        assertEquals(BubbleArrival.MIN_ALPHA, start.alpha)
    }

    @Test
    fun bubbleArrival_overshootStaysSubtleAndOpaque() {
        val overshoot = BubbleArrival.frame(progress = 1.08f, mine = true)
        assertTrue(overshoot.scale > 1f && overshoot.scale < 1.03f)
        assertEquals(1f, overshoot.alpha)
    }
}
