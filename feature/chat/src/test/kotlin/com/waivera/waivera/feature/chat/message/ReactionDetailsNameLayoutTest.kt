package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.designsystem.component.LIST_ROW_NAME_TAG
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Who reacted, with the long-name fixtures on the small phone's width: each name is one line at
 * 100% and at 200%, shrinking a little and then ending in an ellipsis (v7 item 3, replacing the
 * wave 29 two-line cap at large font for chat names).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h2400dp-notnight-xhdpi")
class ReactionDetailsNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val names = LongFixtures.NAMES.entries.associate { (key, name) -> "usr_$key" to name }

    private fun rowNames(fontScale: Float): List<TextLayoutResult> {
        val reactions = names.keys.map { Reaction(it, THUMBS_UP) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    ReactionDetailsContent(
                        reactions = Reactions.summarize(reactions, myUserId = null),
                        initialEmoji = THUMBS_UP,
                        nameFor = { id -> names.getValue(id) },
                        onClose = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val nodes = compose.onAllNodesWithTag(LIST_ROW_NAME_TAG, useUnmergedTree = true)
        assertEquals(names.size, nodes.fetchSemanticsNodes().size, "one row per person")
        return names.values.indices.map { i ->
            val results = mutableListOf<TextLayoutResult>()
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.first()
        }
    }

    private fun assertRows(fontScale: Float, maxLines: Int) {
        val shown = names.values.toList()
        rowNames(fontScale).forEachIndexed { i, layout ->
            val name = shown[i]
            val text = layout.layoutInput.text.text
            assertTrue(layout.lineCount <= maxLines, "\"$name\" takes ${layout.lineCount} lines at ${fontScale}x")
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertFalse(
                    text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
                    "\"$name\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
                )
            }
        }
    }

    @Test
    fun oneLineAtDefaultSize() = assertRows(fontScale = 1f, maxLines = 1)

    @Test
    fun oneLineAtLargeFont() = assertRows(fontScale = 2f, maxLines = 1)

    private companion object {
        const val THUMBS_UP = "👍"
    }
}
