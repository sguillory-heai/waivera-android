package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
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
 * Name rows (people, reactions, event people) with the long-name fixtures on the small phone
 * (wave 29 N5): one line at 100%, two at 200%, with an ellipsis and no mid-word break.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class WaiveraListRowNameTest {
    @get:Rule
    val compose = createComposeRule()

    private val names = LongFixtures.NAMES.values.toList() + NORMAL

    private fun rowNames(fontScale: Float): List<TextLayoutResult> {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Column {
                        names.forEach { name ->
                            WaiveraListRow(
                                headline = name,
                                nameHeadline = true,
                                supporting = "5-year anniversary",
                                onClick = {},
                                showChevron = true,
                                leading = {
                                    WaiveraAvatar(
                                        name = name,
                                        size = WaiveraSizes.AvatarMedium,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val nodes = compose.onAllNodesWithTag(LIST_ROW_NAME_TAG, useUnmergedTree = true)
        return names.indices.map { i ->
            val results = mutableListOf<TextLayoutResult>()
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.first()
        }
    }

    private fun assertRows(fontScale: Float, maxLines: Int) {
        rowNames(fontScale).forEachIndexed { i, layout ->
            val name = names[i]
            val text = layout.layoutInput.text.text
            assertTrue("\"$name\" takes ${layout.lineCount} lines at ${fontScale}x", layout.lineCount <= maxLines)
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertFalse(
                    "\"$name\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
                    text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
                )
            }
            for (line in 0 until layout.lineCount) {
                assertTrue("\"$name\" line $line runs past the row", layout.getLineRight(line) <= layout.size.width)
            }
            if (name in NORMAL) {
                assertEquals("\"$name\" gets a line limit", Int.MAX_VALUE, layout.layoutInput.maxLines)
                assertFalse("\"$name\" is ellipsized", layout.isLineEllipsized(layout.lineCount - 1))
            }
        }
    }

    @Test
    fun oneLineAtDefaultSize() = assertRows(fontScale = 1f, maxLines = 1)

    @Test
    fun twoLinesAtLargeFont() = assertRows(fontScale = 2f, maxLines = 2)

    private companion object {
        val NORMAL = listOf("Priya Raman", "Jordan Lee")
    }
}
