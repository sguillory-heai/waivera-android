package com.waivera.waivera.core.designsystem.component

import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The preview follows the gesture evenly (UI7-01): through the linear gesture curve, a quarter,
 * a half and three quarters of the swipe put the card at about 0.975, 0.95 and 0.925, with the
 * corner, the outline and the dim moving in step.
 */
class BackPreviewProgressTest {
    private val curve = WaiveraMotionScheme.Standard.gesture.progress

    @Test
    fun theCardShrinksEvenlyWithTheSwipe() {
        listOf(0.25f to 0.975f, 0.5f to 0.95f, 0.75f to 0.925f).forEach { (progress, scale) ->
            val leaving =
                backPreviewGeometry(BackPreviewRole.Leaving, curve.transform(progress), swipeFromRight = false)
            assertEquals(scale, leaving.scale, TOLERANCE, "scale at $progress")
            assertEquals(progress, leaving.corner, TOLERANCE, "corner at $progress")
            assertTrue(leaving.outlined, "outlined at $progress")
            assertEquals(0f, leaving.scrim, TOLERANCE, "the card itself is never dimmed")
        }
    }

    @Test
    fun theCardKeepsItsFarSideOnTheEdgeAwayFromTheSwipe() {
        val fromLeft = backPreviewGeometry(BackPreviewRole.Leaving, fraction = 1f, swipeFromRight = false)
        assertEquals(WaiveraMotionScheme.BACK_PREVIEW_SCALE, fromLeft.scale, TOLERANCE)
        assertEquals((1f - WaiveraMotionScheme.BACK_PREVIEW_SCALE) / 2f, fromLeft.shift, TOLERANCE)
        val fromRight = backPreviewGeometry(BackPreviewRole.Leaving, fraction = 1f, swipeFromRight = true)
        assertEquals(-fromLeft.shift, fromRight.shift, TOLERANCE)
    }

    @Test
    fun theRevealedScreenSlidesToRestAndTheDimLifts() {
        val start = backPreviewGeometry(BackPreviewRole.Revealed, fraction = 0f, swipeFromRight = false)
        assertEquals(-0.1f, start.shift, TOLERANCE)
        assertEquals(1f, start.scrim, TOLERANCE)
        val halfway = backPreviewGeometry(BackPreviewRole.Revealed, curve.transform(0.5f), swipeFromRight = false)
        assertEquals(-0.05f, halfway.shift, TOLERANCE)
        assertEquals(0.5f, halfway.scrim, TOLERANCE)
        assertEquals(BackPreviewGeometry.Rest, backPreviewGeometry(BackPreviewRole.Revealed, 1f, false))
        val fromRight = backPreviewGeometry(BackPreviewRole.Revealed, fraction = 0f, swipeFromRight = true)
        assertEquals(0.1f, fromRight.shift, TOLERANCE)
    }

    @Test
    fun atRestNothingIsDrawn() {
        val leaving = backPreviewGeometry(BackPreviewRole.Leaving, fraction = 0f, swipeFromRight = false)
        assertEquals(1f, leaving.scale, TOLERANCE)
        assertFalse(leaving.outlined, "no outline before the card moves")
        assertEquals(BackPreviewGeometry.Rest, backPreviewGeometry(BackPreviewRole.None, 0.5f, false))
    }

    @Test
    fun progressOutsideTheSwipeIsClamped() {
        val over = backPreviewGeometry(BackPreviewRole.Leaving, fraction = 1.2f, swipeFromRight = false)
        assertEquals(WaiveraMotionScheme.BACK_PREVIEW_SCALE, over.scale, TOLERANCE)
        val under = backPreviewGeometry(BackPreviewRole.Revealed, fraction = -0.2f, swipeFromRight = false)
        assertEquals(1f, under.scrim, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
