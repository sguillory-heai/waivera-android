package com.waivera.waivera.feature.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.demo.DemoData
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
 * Bookmark cards with the wave 28 60-character author on the small phone: the name takes one
 * line at 100% and two at 200%, ends in "…", and never breaks inside a word ("Maria-Fernan" /
 * "da"), so the card still shows the message (B5). A short name is not cut.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class BookmarkAuthorLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(author: String, fontScale: Float) {
        val person = Person("usr_long", displayName = author)
        val messages = listOf(LongFixtures.PARAGRAPHS_3000, LongFixtures.URL_400).mapIndexed { i, text ->
            Message(
                id = FIRST_ID + i,
                conversationId = DemoData.DM_PRIYA,
                author = person,
                authorId = person.id,
                text = text,
                createdAt = DemoData.NOW.minusSeconds(HOUR_SECONDS * (i + 1)),
                isBookmarked = true,
            )
        }
        val rows = BookmarkRules.dated(messages.map(BookmarkRules::row), DemoData.NOW, DemoData.ZONE)
        val state = BookmarksUiState(rows = rows, totalCount = rows.size, phase = ListPhase.Content, now = DemoData.NOW)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    BookmarksContent(
                        state = state,
                        snackbar = remember { SnackbarHostState() },
                        onBack = {},
                        onQueryChange = {},
                        onFilterChange = {},
                        onRefresh = {},
                        onRetryConnection = {},
                        onOpen = {},
                        onRemove = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun firstAuthor(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithTag(BOOKMARK_AUTHOR_TAG, useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    /** Clamped at [lines] with an ellipsis, each full line ending at a space or after a hyphen. */
    private fun assertClamped(layout: TextLayoutResult, lines: Int) {
        val text = layout.layoutInput.text.text
        assertTrue("the author takes ${layout.lineCount} lines, more than $lines", layout.lineCount <= lines)
        assertTrue("the cut author has no ellipsis", layout.isLineEllipsized(layout.lineCount - 1))
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            val shown = text.substring(layout.getLineStart(line), end)
            assertTrue("line $line \"$shown\" breaks mid-word", text[end - 1] == ' ' || text[end - 1] == '​')
        }
    }

    @Test
    fun aLongAuthorTakesOneLine() {
        show(LongFixtures.NAME_60, fontScale = 1f)
        assertClamped(firstAuthor(), lines = AUTHOR_LINES)
    }

    @Test
    fun aLongAuthorTakesTwoLinesAt200() {
        show(LongFixtures.NAME_60, fontScale = LARGE_FONT)
        assertClamped(firstAuthor(), lines = AUTHOR_LINES_LARGE_FONT)
    }

    @Test
    fun aShortAuthorIsNotCutAt200() {
        show(SHORT_AUTHOR, fontScale = LARGE_FONT)
        val layout = firstAuthor()
        assertEquals(1, layout.lineCount)
        assertFalse("\"$SHORT_AUTHOR\" is ellipsized", layout.isLineEllipsized(0))
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val FIRST_ID = 70_000L
        const val HOUR_SECONDS = 3_600L
        const val SHORT_AUTHOR = "Priya Raman"
    }
}
