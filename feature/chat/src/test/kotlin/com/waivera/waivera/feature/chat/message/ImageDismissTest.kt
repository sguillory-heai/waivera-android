package com.waivera.waivera.feature.chat.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The image preview's swipe-to-close rules: the backdrop's fade, when a release closes, and when a drag is allowed. */
class ImageDismissTest {
    @Test
    fun theBackdropFadesInProportionToTheDragEitherWay() {
        assertEquals(0f, ImageDismiss.progress(0f, FADE), 0f)
        assertEquals(0.5f, ImageDismiss.progress(FADE / 2, FADE), DELTA)
        assertEquals(0.5f, ImageDismiss.progress(-FADE / 2, FADE), DELTA)
        assertEquals(1f, ImageDismiss.progress(FADE * 3, FADE), 0f)
        val rest = ImageDismiss.scrimAlpha(0f)
        assertEquals(rest / 2, ImageDismiss.scrimAlpha(0.5f), DELTA)
        assertEquals(0f, ImageDismiss.scrimAlpha(1f), 0f)
    }

    @Test
    fun releasingPastTheDistanceCloses() {
        assertTrue(ImageDismiss.shouldClose(DISTANCE, 0f, DISTANCE, FLING))
        assertTrue(ImageDismiss.shouldClose(-DISTANCE * 2, 0f, DISTANCE, FLING))
    }

    @Test
    fun releasingShortOfTheDistanceSpringsBack() {
        assertFalse(ImageDismiss.shouldClose(DISTANCE - 1f, FLING / 2, DISTANCE, FLING))
    }

    @Test
    fun aFlingTheWayTheImageIsGoingClosesFromAShortDrag() {
        assertTrue(ImageDismiss.shouldClose(DISTANCE / 4, FLING, DISTANCE, FLING))
        assertTrue(ImageDismiss.shouldClose(-DISTANCE / 4, -FLING * 2, DISTANCE, FLING))
    }

    @Test
    fun aFlickBackTowardRestKeepsItOpenEvenPastTheDistance() {
        assertFalse(ImageDismiss.shouldClose(DISTANCE * 2, -FLING * 2, DISTANCE, FLING))
    }

    @Test
    fun onlyAnUnzoomedImageCanBeSwipedAway() {
        assertTrue(ImageDismiss.allowsDrag(1f))
        assertFalse(ImageDismiss.allowsDrag(1.01f))
    }

    private companion object {
        const val FADE = 640f
        const val DISTANCE = 240f
        const val FLING = 2_000f
        const val DELTA = 0.0001f
    }
}
