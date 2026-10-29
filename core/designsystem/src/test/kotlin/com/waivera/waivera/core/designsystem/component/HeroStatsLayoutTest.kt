package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * The hero's stats and ring at the sizes the critic checks: no stat label ever breaks inside a
 * word ("handle / d", UI10-04), at 150% and up a phone's stats stack as rows on one card
 * (UI10-08), and the ring's count keeps clear of the arc (UI10-06, option A).
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

    /**
     * Option A: only the count sits in the ring. At every fill, for one- and two-digit totals, in
     * the regular and the compact ring and at 100% and 200% font, the count's box stays inside the
     * opening with [CLEARANCE] to the stroke, centred, and "handled" is not drawn in the ring.
     */
    @Test
    fun theCountClearsTheArcAtEveryFillAndFontSize() {
        var case by mutableStateOf(RingCase(done = 0, total = 9, compact = false))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    val c = case
                    WaiveraProgressRing(
                        done = c.done,
                        total = c.total,
                        contentDescription = "${c.done} of ${c.total} handled today",
                        size = if (c.compact) WaiveraSizes.RingCompact else WaiveraSizes.Ring,
                        stroke = if (c.compact) WaiveraSizes.RingStrokeCompact else WaiveraSizes.RingStroke,
                        compact = c.compact,
                    )
                }
            }
        }
        val fills = listOf(0 to 9, 5 to 9, 8 to 9, 9 to 9, 12 to 15)
        for (scale in listOf(1f, 2f)) {
            for (compact in listOf(false, true)) {
                for ((done, total) in fills) {
                    fontScale = scale
                    case = RingCase(done, total, compact)
                    compose.waitForIdle()
                    assertCountClearsTheArc(case, scale)
                }
            }
        }
    }

    private fun assertCountClearsTheArc(case: RingCase, scale: Float) {
        val label = "${case.done}/${case.total} compact=${case.compact} font=$scale"
        val ring = compose.onNodeWithContentDescription("${case.done} of ${case.total} handled today")
            .fetchSemanticsNode().boundsInRoot
        val numeral = compose.onNodeWithText(case.done.toString(), useUnmergedTree = true).fetchSemanticsNode()
        val total = compose.onNodeWithText("/${case.total}", useUnmergedTree = true).fetchSemanticsNode()
        val box = Rect(
            left = numeral.boundsInRoot.left,
            top = minOf(numeral.boundsInRoot.top, total.boundsInRoot.top),
            right = total.boundsInRoot.right,
            bottom = maxOf(numeral.boundsInRoot.bottom, total.boundsInRoot.bottom),
        )
        val stroke = if (case.compact) WaiveraSizes.RingStrokeCompact else WaiveraSizes.RingStroke
        val innerRadius = with(compose.density) { (ring.width / 2f) - stroke.toPx() }
        val corners = listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight)
        val farthest = corners.maxOf { (it - ring.center).getDistance() }
        val clearance = with(compose.density) { (innerRadius - farthest).toDp() }
        assertTrue("$label: the count is $clearance from the stroke, under $CLEARANCE", clearance >= CLEARANCE)
        assertTrue("$label: the count is off centre", abs(box.center.x - ring.center.x) <= 1f)
        assertFalse(
            "$label: the ring still draws a caption",
            compose.onAllNodesWithText("handled", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private data class RingCase(val done: Int, val total: Int, val compact: Boolean)

    private companion object {
        /** The least room between the count and the stroke (option A's board measures 15 to 19dp). */
        val CLEARANCE: Dp = 4.dp

        /** The stats' width on each phone: the window less the hero's 16dp gutters. */
        val SMALL_PHONE_GROUP: Dp = 328.dp
        val LARGE_PHONE_GROUP: Dp = 380.dp
    }
}
