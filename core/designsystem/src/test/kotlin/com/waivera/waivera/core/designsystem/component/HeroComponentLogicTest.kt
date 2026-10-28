package com.waivera.waivera.core.designsystem.component

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** The pure parts of concept A's hero and sheet components. */
class HeroComponentLogicTest {
    @Test
    fun `the ring fills the handled share and never past full`() {
        assertEquals(0f, ringFraction(done = 0, total = 9))
        assertEquals(5f / 9f, ringFraction(done = 5, total = 9))
        assertEquals(1f, ringFraction(done = 9, total = 9))
        assertEquals(1f, ringFraction(done = 12, total = 9))
        assertEquals(0f, ringFraction(done = 3, total = 0), "nothing to do is an empty ring, not a division by zero")
        assertEquals(0f, ringFraction(done = -1, total = 4))
    }

    @Test
    fun `the ring's count stops growing past its cap`() {
        assertTrue(RING_MAX_FONT_SCALE in 1f..1.5f, "the ring's count may grow a little, never to 200%")
    }

    @Test
    fun `axis labels sit centred on their hour and inside the track`() {
        val xs = placeAxisLabels(centers = listOf(0f, 0.5f, 1f), widths = listOf(40, 40, 40), total = 400, gap = 8)
        assertEquals(listOf<Int?>(0, 180, 360), xs, "the end labels clamp inside; the middle one centres")
    }

    @Test
    fun `axis labels that would collide are dropped, never overlapped`() {
        // At 200% font each label is wide: the second would overlap the first.
        val xs = placeAxisLabels(
            centers = listOf(0f, 0.2f, 0.4f, 1f),
            widths = listOf(90, 90, 90, 90),
            total = 300,
            gap = 8,
        )
        assertEquals(listOf(0, null, null, 210), xs, "the two that collide with the first are dropped")
        val kept = xs.indices.filter { xs[it] != null }
        kept.zipWithNext().forEach { (a, b) ->
            assertTrue(checkNotNull(xs[a]) + 90 + 8 <= checkNotNull(xs[b]), "labels $a and $b overlap")
        }
    }

    @Test
    fun `a label wider than the track still starts at its edge`() {
        assertEquals(
            listOf<Int?>(0),
            placeAxisLabels(centers = listOf(0.5f), widths = listOf(500), total = 300, gap = 8),
        )
    }

    @Test
    fun `trails follow the concept placements`() {
        assertNull(trailSpec(WaiveraHeroTrail.None))
        val signIn = checkNotNull(trailSpec(WaiveraHeroTrail.SignIn))
        assertTrue(signIn.strand, "sign-in draws the second strand")
        assertEquals(0.55f, signIn.alpha)
        val tab = checkNotNull(trailSpec(WaiveraHeroTrail.Tab))
        assertTrue(!tab.strand && tab.alpha < signIn.alpha, "a tab hero's trail is one faint loop")
        val reward = checkNotNull(trailSpec(WaiveraHeroTrail.Reward))
        assertTrue(reward.alpha < signIn.alpha)
    }
}
