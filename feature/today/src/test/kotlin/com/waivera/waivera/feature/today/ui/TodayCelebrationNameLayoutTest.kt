package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.CelebrationItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Today's celebration cards with the long-name fixtures on the small phone (wave 29 N3): the
 * name keeps to two lines with an ellipsis and wraps only at a space or after a hyphen, at 100%
 * and 200%. A normal name is whole.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TodayCelebrationNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val items = (LongFixtures.NAMES.values + NORMAL).mapIndexed { i, name ->
        CelebrationItem("usr_c$i", name, isBirthday = i % 2 == 0, anniversaryYears = if (i % 2 == 0) null else YEARS)
    }

    private fun plainSentence(item: CelebrationItem): String = if (item.isBirthday) {
        context.getString(R.string.feature_today_celebration_birthday, item.name)
    } else {
        context.getString(R.string.feature_today_celebration_anniversary, item.name, YEARS)
    }

    private fun sentences(fontScale: Float): List<TextLayoutResult> {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Column(Modifier.fillMaxWidth()) {
                        items.forEach { item -> CelebrationCard(item = item, onOpen = {}, onSendWishes = {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val nodes = compose.onAllNodesWithTag(CELEBRATION_SENTENCE_TAG, useUnmergedTree = true)
        return items.indices.map { i ->
            val results = mutableListOf<TextLayoutResult>()
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.first()
        }
    }

    private fun assertNamesKeepToTwoLines(fontScale: Float) {
        sentences(fontScale).forEachIndexed { i, layout ->
            val item = items[i]
            val name = requireNotNull(item.name)
            val text = layout.layoutInput.text.text
            val start = plainSentence(item).lastIndexOf(name)
            val lines = layout.lineCount - layout.getLineForOffset(start)
            assertTrue(lines <= 2, "\"$name\" takes $lines lines at ${fontScale}x")
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertFalse(
                    text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
                    "\"$name\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
                )
            }
            for (line in 0 until layout.lineCount) {
                assertTrue(layout.getLineRight(line) <= layout.size.width, "\"$name\" line $line runs past the card")
            }
            if (name in NORMAL) {
                assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "\"$name\" is ellipsized")
                assertEquals(Int.MAX_VALUE, layout.layoutInput.maxLines, "\"$name\" gets a line limit")
            }
        }
    }

    @Test
    fun namesKeepToTwoLinesAtDefaultSize() = assertNamesKeepToTwoLines(fontScale = 1f)

    @Test
    fun namesKeepToTwoLinesAtLargeFont() = assertNamesKeepToTwoLines(fontScale = LARGE_FONT)

    private companion object {
        const val LARGE_FONT = 2f
        const val YEARS = 12
        val NORMAL = listOf("Jordan Lee", "Sam Ortiz")
    }
}
