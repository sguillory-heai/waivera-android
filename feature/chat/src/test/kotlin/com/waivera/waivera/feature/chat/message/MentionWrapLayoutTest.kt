package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wave 28 message with 15 mentions in an incoming bubble on the small phone: no line starts
 * or ends at a mention's own hyphen ("@Maria" / "-Fernanda", "@Jean" / "-Baptiste"), at 100% and
 * at 200%.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class MentionWrapLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float) {
        val document = TipTapParser.parse(Json.parseToJsonElement(LongFixtures.MENTIONS_DOC).jsonObject)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Box(Modifier.width(BUBBLE_TEXT_WIDTH)) { RichText(document = document, onLink = {}) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertNoLineBreaksAtAMentionHyphen() {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(TAIL, substring = true, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val layout = results.first()
        val text = layout.layoutInput.text.text
        assertFalse("a mention kept a breakable hyphen", '-' in text)
        assertTrue("the mentions do not wrap at all", layout.lineCount > 1)
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            val shown = text.substring(layout.getLineStart(line), end)
            assertFalse("line $line \"$shown\" ends at a hyphen", text[end - 1] == NON_BREAKING_HYPHEN)
            assertFalse("line ${line + 1} starts at a hyphen after \"$shown\"", text[end] == NON_BREAKING_HYPHEN)
        }
    }

    @Test
    fun mentionsNeverWrapAtTheirHyphens() {
        show(fontScale = 1f)
        assertNoLineBreaksAtAMentionHyphen()
    }

    @Test
    fun mentionsNeverWrapAtTheirHyphensAt200() {
        show(fontScale = LARGE_FONT)
        assertNoLineBreaksAtAMentionHyphen()
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val NON_BREAKING_HYPHEN = '‑'
        const val TAIL = "please read the handover notes."

        // The text column of an incoming bubble on the 360dp phone, as in the verifier's shot.
        val BUBBLE_TEXT_WIDTH = 210.dp
    }
}
