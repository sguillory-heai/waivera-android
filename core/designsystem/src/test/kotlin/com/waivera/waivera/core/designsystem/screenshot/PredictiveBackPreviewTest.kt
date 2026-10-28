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
 * (UI5-02): the leaving Detail screen shrunk to a card inset from the screen's edges, on top of
 * the revealed List, which sits a little to the left and is dimmed around the card.
 *
 * The card is measured with bounds in root, which include the scale and slide AnimatedContent
 * applies on a parent layer; unclipped bounds report the untransformed 360dp layout. The List's
 * start lies off screen, so only its unclipped bounds can show it.
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
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Standard, hapticsEnabled = false) {
                BackPreviewSample(motion = WaiveraMotionScheme.Standard, onTap = { tapped = it })
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theLeavingScreenIsAShrunkenCardInsetFromTheEdges() {
        val screen = compose.onRoot().getBoundsInRoot()
        val card = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        assertTrue("card is ${card.width} wide on a ${screen.width} screen", card.width < screen.width - MIN_INSET)
        assertTrue("card is ${card.height} tall on a ${screen.height} screen", card.height < screen.height - MIN_INSET)
        assertTrue("card starts at ${card.left}, on the left edge", card.left > screen.left + MIN_INSET)
        assertTrue("card starts at ${card.top}, on the top edge", card.top > screen.top + MIN_INSET / 2)
    }

    @Test
    fun theRevealedScreenSlidesInFromTheLeft() {
        val revealed = compose.onNodeWithTag(BackPreviewScreen.List.name).getUnclippedBoundsInRoot()
        assertTrue("revealed screen starts at ${revealed.left}", revealed.left < 0.dp)
    }

    /** Hit testing follows the same z-order as drawing, so a tap on the card lands on Detail. */
    @Test
    fun theLeavingCardIsOnTopOfTheRevealedScreen() {
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
        val card = compose.onNodeWithTag(BackPreviewScreen.Detail.name).getBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val centerY = with(compose.density) { ((card.top + card.bottom) / 2).roundToPx() }
        val outside = with(compose.density) { PROBE_FROM_EDGE.roundToPx() }
        val inside = with(compose.density) { (card.left + PROBE_FROM_EDGE).roundToPx() }
        val dimmed = pixels[outside, centerY].luminance()
        val onCard = pixels[inside, centerY].luminance()
        assertTrue("revealed screen at $dimmed is not darker than the card at $onCard", dimmed < onCard)
    }

    private companion object {
        val MIN_INSET: Dp = 8.dp

        // Inside the screens' left padding, so the probes land on background, not text.
        val PROBE_FROM_EDGE: Dp = 3.dp
    }
}
