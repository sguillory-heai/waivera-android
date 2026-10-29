package com.waivera.waivera.feature.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.thread.ThreadContent
import com.waivera.waivera.feature.chat.thread.ThreadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The thread header at 200% on the small phone with the wave 28 68-character channel name: two
 * lines ("Thread", then "In north-region-…"), not three, and TalkBack still hears the whole
 * channel name. A short channel name is not cut.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class ThreadHeaderLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private fun show(channel: String) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, LARGE_FONT)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    ThreadScreenContent(
                        state = ThreadUiState(content = ThreadContent.Invalid, conversationTitle = channel),
                        draft = "",
                        onDraftChange = {},
                        time = RowTime(DemoData.NOW, DemoData.ZONE),
                        actions = ThreadActions.None,
                        skeletonDelayMillis = 0,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private fun title(): TextLayoutResult =
        compose.onNodeWithText(context.getString(R.string.feature_chat_thread_screen_title), useUnmergedTree = true)
            .layout()

    private fun subtitle(channel: String): TextLayoutResult = compose.onNodeWithContentDescription(
        context.getString(R.string.feature_chat_thread_in, channel),
        useUnmergedTree = true,
    ).layout()

    @Test
    fun aLongChannelKeepsTheHeaderToTwoLinesAndIsReadWhole() {
        show(LongFixtures.CHANNEL_LONG)
        val title = title()
        val subtitle = subtitle(LongFixtures.CHANNEL_LONG)
        assertEquals("the header takes more than two lines", 2, title.lineCount + subtitle.lineCount)
        assertTrue("the long channel name is not ellipsized", subtitle.isLineEllipsized(subtitle.lineCount - 1))
        // "In" and the start of the name share the line instead of "In" standing alone.
        val inPrefix = context.getString(R.string.feature_chat_thread_in, "")
        assertTrue(subtitle.getLineEnd(0, visibleEnd = true) > inPrefix.length)
    }

    @Test
    fun aShortChannelIsNotCut() {
        show(SHORT_CHANNEL)
        val subtitle = subtitle(SHORT_CHANNEL)
        assertEquals(1, subtitle.lineCount)
        assertFalse("\"$SHORT_CHANNEL\" is ellipsized", subtitle.isLineEllipsized(0))
    }

    private companion object {
        const val LARGE_FONT = 2f
        const val SHORT_CHANNEL = "Priya Raman"
    }
}
