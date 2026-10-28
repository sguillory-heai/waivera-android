package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Email addresses on the small phone (UI6-02, UI6-03): whole on one line when they fit,
 * otherwise wrapped only after the "@" (and the local part's or domain's own separators when
 * that part alone is wider than the line), never ellipsized and never starting a line with "."
 * or "-".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class WaiveraAddressTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun layoutOf(address: String, fontScale: Float): TextLayoutResult {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    WaiveraAddressText(
                        address = address,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(HEADER_WIDTH),
                    )
                }
            }
        }
        // Found by the plain address: the break characters never reach semantics.
        return compose.onNodeWithText(address).fetchTextLayoutResult()
    }

    @Test
    fun anAddressThatFitsStaysOnOneLine() {
        val layout = layoutOf(address = SHORT, fontScale = 1f)
        assertEquals(1, layout.lineCount)
        assertWhole(layout)
    }

    @Test
    fun atLargeFontAnAddressWrapsAfterItsAtSign() {
        val layout = layoutOf(address = SHORT, fontScale = LARGE_FONT)
        assertWhole(layout)
        assertEquals(2, layout.lineCount)
        assertEquals("alex.rivera@", layout.visibleLine(0))
        assertEquals("example.com", layout.visibleLine(1))
    }

    /** The person_long address, whose local part alone is wider than the header at 200%. */
    @Test
    fun aLongAddressAtLargeFontNeverStartsALineWithADotOrHyphen() {
        val layout = layoutOf(address = LONG, fontScale = LARGE_FONT)
        assertWhole(layout)
        assertTrue("\"$LONG\" fits on ${layout.lineCount} line at 200%", layout.lineCount > 1)
        for (line in 0 until layout.lineCount) {
            val text = layout.visibleLine(line)
            assertFalse("line $line starts with \"$text\"", text.startsWith(".") || text.startsWith("-"))
            if (line < layout.lineCount - 1) {
                assertTrue("line $line ends mid-part: \"$text\"", text.last() in ".-@")
            }
        }
    }

    /** Every character is laid out, nothing is ellipsized, and no line runs past the width. */
    private fun assertWhole(layout: TextLayoutResult) {
        val text = layout.layoutInput.text.text
        assertFalse("cut vertically", layout.didOverflowHeight)
        assertEquals("not fully laid out", text.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertFalse("line $line is ellipsized", layout.isLineEllipsized(line))
            assertTrue(
                "line $line ends at ${layout.getLineRight(line)} past ${layout.size.width}",
                layout.getLineRight(line) <= layout.size.width,
            )
        }
    }

    private fun TextLayoutResult.visibleLine(line: Int): String = layoutInput.text.text
        .substring(getLineStart(line), getLineEnd(line))
        .filterNot { it == WORD_JOINER || it == ZERO_WIDTH_SPACE }

    private fun SemanticsNodeInteraction.fetchTextLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val WORD_JOINER = '⁠'
        const val ZERO_WIDTH_SPACE = '​'

        // The profile header's text column on the small phone: 360dp less gutters and card padding.
        val HEADER_WIDTH = 296.dp
        const val SHORT = "alex.rivera@example.com"
        const val LONG = "alexandria.rivera-montgomery@example.com"
    }
}
