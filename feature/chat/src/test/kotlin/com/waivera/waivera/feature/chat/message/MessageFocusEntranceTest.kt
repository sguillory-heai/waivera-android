package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.waiveraColors
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The focus overlay's entrance and scrim, frame by frame on the virtual clock (wave 30).
 *
 * O1: the pop spring carried the lifted bubble past its place and under the reaction pill for
 * about 100 ms. Now, on every frame from the press to rest, the pill's drawn bottom stays at
 * least the gap above the bubble's drawn top, and the menu's top the gap below its bottom.
 *
 * O3: the chat's text read through the scrim beside the bubble. The scrim drawn is the focus
 * scrim token, in light and in plum dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class MessageFocusEntranceTest {
    @get:Rule
    val compose = createComposeRule()

    private val metrics get() = RuntimeEnvironment.getApplication().resources.displayMetrics
    private val dp get() = metrics.density

    private fun show(text: String, anchor: Rect?, motion: WaiveraMotionScheme, dark: Boolean = false) {
        val model = ChatActions.model(FocusOverlayFixtures.mine(text), DemoData.ME, canWrite = true, allowReply = true)
        compose.setContent {
            WaiveraTheme(darkTheme = dark, motion = motion, hapticsEnabled = false) {
                Box(Modifier.fillMaxSize().background(WaiveraTheme.colors.background))
                MessageFocusLayer(model = model, mine = true, actions = MessageFocusActions.None, anchor = anchor)
            }
        }
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** A short bubble of mine pressed just above the composer, so it rises to make room for the menu. */
    private fun nearComposer(): Rect = Rect(
        left = metrics.widthPixels * 0.3f,
        top = metrics.heightPixels - 140 * dp,
        right = metrics.widthPixels - 16 * dp,
        bottom = metrics.heightPixels - 100 * dp,
    )

    /** A long bubble of mine with its start scrolled far above the screen, so it drops into place. */
    private fun tallScrolled(): Rect = Rect(
        left = metrics.widthPixels * 0.18f - 16 * dp,
        top = -metrics.heightPixels.toFloat(),
        right = metrics.widthPixels - 16 * dp,
        bottom = metrics.heightPixels - 90 * dp,
    )

    private fun assertThePillNeverCoversTheBubble(text: String, anchor: Rect, label: String) {
        compose.mainClock.autoAdvance = false
        show(text, anchor, WaiveraMotionScheme.Standard)
        val gap = ChatDimens.FocusGap.value * dp
        val tops = mutableListOf<Float>()
        repeat(ENTRANCE_FRAMES) { frame ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            val bubble = bounds(FOCUS_BUBBLE_TAG)
            val pill = bounds(FOCUS_PILL_TAG)
            val menuRow = compose.onNode(
                hasText("Reply in thread") and hasClickAction(),
            ).fetchSemanticsNode().boundsInRoot
            tops += bubble.top
            assertTrue(
                "$label, frame $frame: the pill (bottom ${pill.bottom}) is within ${gap}px of the bubble " +
                    "(top ${bubble.top})",
                bubble.top - pill.bottom >= gap - TOLERANCE_PX,
            )
            assertFalse("$label, frame $frame: the pill overlaps the bubble", pill.overlaps(bubble))
            assertTrue(
                "$label, frame $frame: the menu (first row ${menuRow.top}) is within ${gap}px of the bubble " +
                    "(bottom ${bubble.bottom})",
                menuRow.top - bubble.bottom >= gap - TOLERANCE_PX,
            )
        }
        val travelled = tops.max() - tops.min()
        assertTrue(
            "$label: the bubble did not travel ($travelled px), so this checks nothing",
            travelled > MIN_TRAVEL_PX,
        )
        assertEquals("$label: the bubble has not settled", tops.last(), tops[tops.size - 2], TOLERANCE_PX)
    }

    @Test
    fun risingFromTheComposerThePillNeverCoversTheBubble() =
        assertThePillNeverCoversTheBubble(FocusOverlayFixtures.SHORT, nearComposer(), "short, near the composer")

    @Test
    fun droppingFromUnderTheTopBarThePillNeverCoversTheBubble() =
        assertThePillNeverCoversTheBubble(FocusOverlayFixtures.PARAGRAPHS_3000, tallScrolled(), "3,000 characters")

    @Test
    fun theScrimIsTheFocusTokenInLight() = assertScrim(dark = false)

    @Test
    fun theScrimIsTheFocusTokenInPlumDark() = assertScrim(dark = true)

    /** A corner the bubble, pill and menu never reach shows the page under the focus scrim at rest. */
    private fun assertScrim(dark: Boolean) {
        show(FocusOverlayFixtures.SHORT, nearComposer(), WaiveraMotionScheme.Reduced, dark = dark)
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
        val colors = waiveraColors(darkTheme = dark)
        val expected = colors.focusScrim.compositeOver(colors.background)
        val corner = compose.onRoot().captureToImage().toPixelMap()[(8 * dp).toInt(), (8 * dp).toInt()]
        val theme = if (dark) "dark" else "light"
        listOf(
            "red" to (corner.red to expected.red),
            "green" to (corner.green to expected.green),
            "blue" to (corner.blue to expected.blue),
        ).forEach { (channel, pair) ->
            val (drawn, want) = pair
            assertTrue(
                "$theme: the scrim's $channel is $drawn, the focus scrim over the page is $want",
                abs(drawn - want) <= CHANNEL_TOLERANCE,
            )
        }
        val sheetScrim = colors.scrim.compositeOver(colors.background)
        assertTrue(
            "$theme: the scrim is no darker than a sheet's",
            corner.red + corner.green + corner.blue < sheetScrim.red + sheetScrim.green + sheetScrim.blue - DARKER_MIN,
        )
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val ENTRANCE_FRAMES = 60
        const val SETTLE_MILLIS = 1_000L
        const val TOLERANCE_PX = 0.5f
        const val MIN_TRAVEL_PX = 40f
        const val CHANNEL_TOLERANCE = 2f / 255f
        const val DARKER_MIN = 0.05f
    }
}
