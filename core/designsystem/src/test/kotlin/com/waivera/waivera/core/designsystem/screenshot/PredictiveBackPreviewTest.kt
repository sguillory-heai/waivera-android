package com.waivera.waivera.core.designsystem.screenshot

import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What [PredictiveBackScreenshotTest]'s standard capture has to show halfway through the swipe
 * (UI5-02, UI7-01): the leaving Detail screen shrunk to a card inset from the screen's edges,
 * with rounded corners, on top of the revealed List, which sits a little to the left and is
 * dimmed around the card.
 *
 * The sample holds the gesture progress at 0.5, the input the frames draw from, so the expected
 * geometry is exact: through the linear gesture curve the card is at scale 0.95 and 5% of the
 * scale gap's shift (9dp at 360dp), and the List starts 18dp off the left edge. The frame's layer
 * wraps the test tag, so bounds in root include its scale and shift. The List's start lies off
 * screen, so only its unclipped bounds can show it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class PredictiveBackPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    private var tapped: BackPreviewScreen? = null

    @Before
    fun setUp() {
        // Animations on, as in the standard capture; with them off the seek snaps to the end.
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }

    private fun showHalfway(motion: WaiveraMotionScheme = WaiveraMotionScheme.Standard) {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                BackPreviewSample(motion = motion, onTap = { tapped = it })
            }
        }
        compose.waitForIdle()
    }

    /** Halfway through the gesture is halfway through the preview: the curve is linear. */
    @Test
    fun theCardIsHalfwayToItsShrinkAtHalfTheSwipe() {
        showHalfway()
        val screen = compose.onRoot().getBoundsInRoot()
        val card = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        assertEquals(screen.width.value * HALFWAY_SCALE, card.width.value, GEOMETRY_TOLERANCE)
        assertEquals(screen.height.value * HALFWAY_SCALE, card.height.value, GEOMETRY_TOLERANCE)
        // Centred, then moved right by half the scale gap: its right side stays on the screen edge.
        assertEquals(screen.right.value, card.right.value, GEOMETRY_TOLERANCE)
        val revealed = compose.onNodeWithTag(BackPreviewScreen.List.name).getUnclippedBoundsInRoot()
        assertEquals(-screen.width.value * HALFWAY_PARALLAX, revealed.left.value, GEOMETRY_TOLERANCE)
    }

    /** "Remove animations": nothing shrinks or moves, so there is nothing to frame. */
    @Test
    fun reducedMotionLeavesTheScreenFullSize() {
        showHalfway(WaiveraMotionScheme.Reduced)
        val screen = compose.onRoot().getBoundsInRoot()
        val detail = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        assertEquals(screen, detail)
    }

    @Test
    fun theLeavingScreenIsAShrunkenCardInsetFromTheEdges() {
        showHalfway()
        val screen = compose.onRoot().getBoundsInRoot()
        val card = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        assertTrue("card is ${card.width} wide on a ${screen.width} screen", card.width < screen.width - MIN_INSET)
        assertTrue("card is ${card.height} tall on a ${screen.height} screen", card.height < screen.height - MIN_INSET)
        assertTrue("card starts at ${card.left}, on the left edge", card.left > screen.left + MIN_INSET)
        assertTrue("card starts at ${card.top}, on the top edge", card.top > screen.top + MIN_INSET / 2)
    }

    @Test
    fun theRevealedScreenSlidesInFromTheLeft() {
        showHalfway()
        val revealed = compose.onNodeWithTag(BackPreviewScreen.List.name).getUnclippedBoundsInRoot()
        assertTrue("revealed screen starts at ${revealed.left}", revealed.left < 0.dp)
    }

    /** Hit testing follows the same z-order as drawing, so a tap on the card lands on Detail. */
    @Test
    fun theLeavingCardIsOnTopOfTheRevealedScreen() {
        showHalfway()
        val card = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        val center = with(compose.density) {
            Offset(x = ((card.left + card.right) / 2).toPx(), y = ((card.top + card.bottom) / 2).toPx())
        }
        compose.onRoot().performTouchInput { click(position = center) }
        compose.waitForIdle()
        assertEquals(BackPreviewScreen.Detail, tapped)
    }

    /**
     * The List shows dimmed beside the card, and the card itself is not dimmed: both points are
     * plain screen background, one on each side of the card's left edge.
     */
    @Test
    fun theRevealedScreenIsDimmedAroundTheCard() {
        showHalfway()
        val card = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val centerY = with(compose.density) { ((card.top + card.bottom) / 2).roundToPx() }
        val outside = with(compose.density) { PROBE_FROM_EDGE.roundToPx() }
        val inside = with(compose.density) { (card.left + PROBE_FROM_EDGE).roundToPx() }
        val dimmed = pixels[outside, centerY].luminance()
        val onCard = pixels[inside, centerY].luminance()
        assertTrue("revealed screen at $dimmed is not darker than the card at $onCard", dimmed < onCard)
    }

    /**
     * The card's corners are rounded halfway through, so just inside its top-left corner the
     * dimmed List shows instead of the card's top bar. The probe on the card is on the same top
     * bar row, above the title's glyphs.
     */
    @Test
    fun theLeavingCardHasRoundedCornersHalfway() {
        showHalfway()
        val card = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val y = with(compose.density) { (card.top + CORNER_PROBE).roundToPx() }
        val inCorner = with(compose.density) { (card.left + CORNER_PROBE).roundToPx() }
        val onCard = with(compose.density) { ((card.left + card.right) / 2).roundToPx() }
        val corner = pixels[inCorner, y].luminance()
        val bar = pixels[onCard, y].luminance()
        assertTrue("card corner at $corner is not darker than its top bar at $bar", corner < bar)
    }

    private companion object {
        val MIN_INSET: Dp = 8.dp

        // Inside the screens' left padding, so the probes land on background, not text.
        val PROBE_FROM_EDGE: Dp = 3.dp

        // Well outside a corner of about 11dp (12dp halfway, scaled by the card's 0.95), and
        // inside the 1dp outline.
        val CORNER_PROBE: Dp = 1.5.dp

        const val HALFWAY_SCALE = 0.95f
        const val HALFWAY_PARALLAX = 0.05f
        const val GEOMETRY_TOLERANCE = 0.5f
    }
}
