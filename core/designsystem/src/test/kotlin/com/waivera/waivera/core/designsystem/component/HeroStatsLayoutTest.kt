package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The hero's stats and compact ring at the sizes the critic checks: no stat label ever breaks
 * inside a word ("handle / d", UI10-04), at 150% and up a phone's stats stack as rows on one
 * card (UI10-08), and the compact ring's caption keeps clear of the arc (UI10-06).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class HeroStatsLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var fontScale by mutableFloatStateOf(1f)
    private var groupWidth by mutableStateOf(SMALL_PHONE_GROUP)

    private val stats = listOf(
        WaiveraStat(value = "5/5", label = "handled", contentDescription = "5 of 5 handled today"),
        WaiveraStat(value = "4", label = "unread chats", contentDescription = "4 unread chats"),
        WaiveraStat(value = "14", label = "events this week", contentDescription = "14 events this week"),
    )

    private fun showStats() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.width(groupWidth)) { WaiveraStatGroup(stats = stats) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private fun assertNoMidWordBreak(label: String) {
        val layout = layoutOf(label)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            val before = label.getOrNull(end - 1)
            val after = label.getOrNull(end)
            assertFalse(
                "\"$label\" breaks inside a word at $end (font $fontScale, width $groupWidth)",
                before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true,
            )
        }
    }

    @Test
    fun noStatLabelBreaksInsideAWordOnEitherPhone() {
        showStats()
        for (width in listOf(SMALL_PHONE_GROUP, LARGE_PHONE_GROUP)) {
            for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                groupWidth = width
                fontScale = scale
                compose.waitForIdle()
                stats.forEach { assertNoMidWordBreak(it.label) }
            }
        }
    }

    @Test
    fun statsStackAsRowsAtLargeFontAndSitSideBySideAtRegularFont() {
        showStats()
        fontScale = 2f
        compose.waitForIdle()
        val stacked = stats.map { compose.onNodeWithContentDescription(it.contentDescription).fetchSemanticsNode() }
        for (i in 1 until stacked.size) {
            assertTrue(
                "row $i does not sit below row ${i - 1} at 200%",
                stacked[i].boundsInRoot.top >= stacked[i - 1].boundsInRoot.bottom - 1f,
            )
        }
        fontScale = 1f
        compose.waitForIdle()
        val tiles = stats.map { compose.onNodeWithContentDescription(it.contentDescription).fetchSemanticsNode() }
        tiles.drop(1).forEach { tile ->
            assertEquals("the tiles are not side by side at 100%", tiles[0].boundsInRoot.top, tile.boundsInRoot.top, 1f)
        }
    }

    @Test
    fun theCompactRingCaptionClearsTheArc() {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                WaiveraProgressRing(
                    done = 5,
                    total = 9,
                    caption = CAPTION,
                    contentDescription = "5 of 9 handled today",
                    size = WaiveraSizes.RingCompact,
                    stroke = WaiveraSizes.RingStrokeCompact,
                    compact = true,
                )
            }
        }
        compose.waitForIdle()
        val inner = WaiveraSizes.RingCompact - WaiveraSizes.RingStrokeCompact * 2
        val caption = compose.onNodeWithText(CAPTION, useUnmergedTree = true).fetchSemanticsNode()
        val width = with(compose.density) { caption.size.width.toDp() }
        assertTrue(
            "the caption is $width wide, more than ${inner - CLEARANCE} inside the arc",
            width <= inner - CLEARANCE,
        )
        val layout = layoutOf(CAPTION)
        assertEquals("the caption wraps", 1, layout.lineCount)
        assertFalse("the caption is cut", layout.hasVisualOverflow)
        val ring = compose.onNodeWithContentDescription("5 of 9 handled today").fetchSemanticsNode().boundsInRoot
        val centre = (caption.positionInRoot.x + caption.size.width / 2f)
        assertTrue("the caption is off centre", abs(centre - ring.center.x) <= 1f)
    }

    private companion object {
        const val CAPTION = "handled"
        val CLEARANCE: Dp = 8.dp

        /** The stats' width on each phone: the window less the hero's 16dp gutters. */
        val SMALL_PHONE_GROUP: Dp = 328.dp
        val LARGE_PHONE_GROUP: Dp = 380.dp
    }
}
