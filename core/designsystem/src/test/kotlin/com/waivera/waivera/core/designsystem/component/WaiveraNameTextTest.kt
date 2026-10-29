package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
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
 * Long names keep to their lines with an ellipsis and never break mid-word, alone in a row and
 * inside a sentence, at 100% and 200% on the small phone (wave 29 N2, N3, N5). Normal names lay
 * out as plain text, with no line limit applied.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class WaiveraNameTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun layouts(
        names: List<String>,
        fontScale: Float,
        maxLines: Int,
        width: Dp = ROW_WIDTH,
        sentence: (String) -> String = { it },
    ): List<TextLayoutResult> {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Column {
                        names.forEachIndexed { i, name ->
                            WaiveraNameText(
                                text = sentence(name),
                                name = name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = maxLines,
                                modifier = Modifier.width(width).testTag("name_$i"),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return names.indices.map { i ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("name_$i").performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(results)
            }
            results.first()
        }
    }

    /** [plain] is the text before break points were added; the name starts at the same offset. */
    private fun assertNameKeepsToItsLines(
        name: String,
        layout: TextLayoutResult,
        nameLines: Int,
        plain: String = name,
    ) {
        val text = layout.layoutInput.text.text
        val start = NameInText.of(text = plain, name = name).nameStart
        assertTrue("\"$name\" is not in \"$plain\"", start >= 0)
        val lines = layout.lineCount - layout.getLineForOffset(start)
        assertTrue("\"$name\" takes $lines lines, more than $nameLines", lines <= nameLines)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertFalse(
                "\"$name\" breaks mid-word after \"${text.substring(0, end)}\"",
                text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
            )
        }
        for (line in 0 until layout.lineCount) {
            assertTrue(
                "\"$name\" line $line ends at ${layout.getLineRight(line)} past ${layout.size.width}",
                layout.getLineRight(line) <= layout.size.width,
            )
        }
    }

    private fun assertRowNames(fontScale: Float, nameLines: Int) {
        val names = LongFixtures.NAMES.values.toList()
        layouts(names, fontScale = fontScale, maxLines = nameLines).forEachIndexed { i, layout ->
            assertNameKeepsToItsLines(names[i], layout, nameLines)
        }
    }

    @Test
    fun rowNamesKeepToOneLineAtDefaultSize() = assertRowNames(fontScale = 1f, nameLines = 1)

    @Test
    fun rowNamesKeepToTwoLinesAtLargeFont() = assertRowNames(fontScale = LARGE_FONT, nameLines = 2)

    /** A single word wider than the line gets one line and an ellipsis instead of two broken halves. */
    @Test
    fun anUnbrokenNameEllipsizesOnOneLineInsteadOfBreaking() {
        val layout = layouts(listOf(LongFixtures.NAME_UNBROKEN_40), fontScale = LARGE_FONT, maxLines = 2).single()
        assertEquals(1, layout.lineCount)
        assertTrue("the unbroken name is not ellipsized", layout.isLineEllipsized(0))
    }

    @Test
    fun aLongNameInASentenceKeepsToTwoLinesAtLargeFont() {
        val names = LongFixtures.NAMES.values.toList()
        layouts(names, fontScale = LARGE_FONT, maxLines = 2, width = CARD_WIDTH, sentence = ::birthday)
            .forEachIndexed { i, layout ->
                assertNameKeepsToItsLines(names[i], layout, nameLines = 2, plain = birthday(names[i]))
            }
    }

    @Test
    fun aLongNameInASentenceKeepsToTwoLinesAtDefaultSize() {
        val names = LongFixtures.NAMES.values.toList()
        layouts(names, fontScale = 1f, maxLines = 2, width = CARD_WIDTH, sentence = ::birthday)
            .forEachIndexed { i, layout ->
                assertNameKeepsToItsLines(names[i], layout, nameLines = 2, plain = birthday(names[i]))
            }
    }

    /** Normal names lay out exactly as a plain Text: no limit is applied and nothing is cut. */
    @Test
    fun normalNamesAreLeftAsTheyAre() {
        layouts(NORMAL, fontScale = LARGE_FONT, maxLines = 2, width = CARD_WIDTH, sentence = ::birthday)
            .forEach { layout ->
                assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
                assertFalse(layout.isLineEllipsized(layout.lineCount - 1))
            }
    }

    @Test
    fun normalRowNamesStayOnOneUncutLine() {
        layouts(NORMAL, fontScale = 1f, maxLines = 1).forEach { layout ->
            assertEquals(1, layout.lineCount)
            assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines)
            assertFalse(layout.isLineEllipsized(0))
        }
    }

    private fun birthday(name: String) = "Happy birthday, $name!"

    private companion object {
        const val LARGE_FONT = 2f
        val NORMAL = listOf("Priya Raman", "Alex Rivera", "Jordan Lee")

        /** A row's text column on a 360dp phone: gutter, avatar and gaps taken off. */
        val ROW_WIDTH = 248.dp

        /** A celebration card's sentence column on the small phone. */
        val CARD_WIDTH = 200.dp
    }
}
