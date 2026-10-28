package com.waivera.waivera.core.designsystem.component

import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** The corner and scrim follow the card's own scale and slide, so they can't settle early (UI6-01). */
class BackPreviewProgressTest {
    @Test
    fun shrinkProgressFollowsTheScale() {
        assertEquals(0f, backPreviewShrinkProgress(scale = 1f), TOLERANCE)
        assertEquals(1f, backPreviewShrinkProgress(scale = WaiveraMotionScheme.BACK_PREVIEW_SCALE), TOLERANCE)
        // The halfway capture: the card is at 0.915, so the corner is 85% of the way round.
        assertEquals(0.85f, backPreviewShrinkProgress(scale = 0.915f), TOLERANCE)
    }

    @Test
    fun shrinkProgressIsClampedWhenASpringOvershoots() {
        assertEquals(0f, backPreviewShrinkProgress(scale = 1.01f), TOLERANCE)
        assertEquals(1f, backPreviewShrinkProgress(scale = 0.89f), TOLERANCE)
    }

    @Test
    fun revealProgressFollowsTheSlide() {
        assertEquals(0f, backPreviewRevealProgress(offsetX = -72, extentX = 72), TOLERANCE)
        assertEquals(1f, backPreviewRevealProgress(offsetX = 0, extentX = 72), TOLERANCE)
        assertEquals(0.75f, backPreviewRevealProgress(offsetX = 18, extentX = 72), TOLERANCE)
    }

    @Test
    fun noSlideMeansNothingToDim() {
        assertEquals(1f, backPreviewRevealProgress(offsetX = 0, extentX = 0), TOLERANCE)
        assertEquals(0f, backPreviewRevealProgress(offsetX = -90, extentX = 72), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
