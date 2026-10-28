package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.Message
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
 * The file card at 200% in a small phone's bubble (UI6-06): the end of the name and its
 * extension ("wk43.pdf") stay whole, and the head wraps after its hyphens instead of giving up
 * letters it has room for (UI7-05): "north- / roster- / wk43.pdf".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class FileCardNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.width(BUBBLE_WIDTH)) {
                        MessageAttachmentView(
                            message = Message(id = 1, conversationId = 1, attachment = PDF),
                            attachment = PDF,
                            onOpen = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theTailAndExtensionAreNeverCut() {
        render()
        compose.onNodeWithContentDescription(NAME, substring = true, useUnmergedTree = true).assertIsDisplayed()
        val tail = compose.onNodeWithTag(FILE_NAME_TAIL_TAG, useUnmergedTree = true).assertIsDisplayed()
        val layout = tail.fetchTextLayoutResult()
        val shown = layout.layoutInput.text.text
        assertEquals("wk43.pdf", shown.filterNot { it == '⁠' })
        assertEquals(1, layout.lineCount)
        assertFalse("\"$shown\" is ellipsized", layout.isLineEllipsized(0))
        assertEquals("\"$shown\" is not fully laid out", shown.length, layout.getLineEnd(0))
        assertTrue("\"$shown\" runs past its width", layout.getLineRight(0) <= layout.size.width)
    }

    @Test
    fun theHeadKeepsEveryLetter() {
        render()
        val head = compose.onNodeWithTag(FILE_NAME_HEAD_TAG, useUnmergedTree = true).assertIsDisplayed()
        val layout = head.fetchTextLayoutResult()
        val shown = layout.layoutInput.text.text
        assertEquals("north-roster-", shown.filterNot { it == '\u200B' || it == '⁠' })
        assertFalse("\"$shown\" overflows its lines", layout.hasVisualOverflow)
        (0 until layout.lineCount).forEach { line ->
            assertFalse("\"$shown\" is ellipsized on line $line", layout.isLineEllipsized(line))
        }
        assertEquals("\"$shown\" is not fully laid out", shown.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun SemanticsNodeInteraction.fetchTextLayoutResult(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val NAME = "north-roster-wk43.pdf"
        val PDF = Attachment(url = "api/attachments/1", originalName = NAME, contentType = "application/pdf")

        // An outgoing bubble on the 360dp phone, narrower than the card's 250dp maximum.
        val BUBBLE_WIDTH = 220.dp
    }
}
