package com.waivera.waivera.feature.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.component.LIST_ROW_NAME_TAG
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
 * The people list with the long-name fixtures (wave 29 N5): each name is one line at 100% and two
 * at 200%, with an ellipsis and no mid-word break. A tall window on the small phone's width keeps
 * every row composed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h2400dp-notnight-xhdpi")
class PeopleRowNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val names = LongFixtures.NAMES.values.toList()

    private fun rowNames(fontScale: Float): List<TextLayoutResult> {
        val rows = names.mapIndexed { i, name ->
            PersonRow("usr_$i", name, "person$i@example.com", if (i == 0) Celebration(true, YEARS) else null)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    PeopleContent(
                        state = PeopleUiState(rows = rows, totalCount = rows.size, phase = ListPhase.Content),
                        onBack = {},
                        onQueryChange = {},
                        onRefresh = {},
                        onRetryConnection = {},
                        onOpenPerson = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val nodes = compose.onAllNodesWithTag(LIST_ROW_NAME_TAG, useUnmergedTree = true)
        assertEquals("every row is composed", names.size, nodes.fetchSemanticsNodes().size)
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
        }
    }

    @Test
    fun oneLineAtDefaultSize() = assertRows(fontScale = 1f, maxLines = 1)

    @Test
    fun twoLinesAtLargeFont() = assertRows(fontScale = 2f, maxLines = 2)

    private companion object {
        const val YEARS = 12
    }
}
