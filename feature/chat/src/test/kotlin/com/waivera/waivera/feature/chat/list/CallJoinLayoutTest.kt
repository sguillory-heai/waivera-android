package com.waivera.waivera.feature.chat.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.chat.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The inbox's live-call pill on the small phone (UI10-03): at 200% it says "Join call" in full on
 * a line of its own, with the unread count moved up to the title line, and TalkBack still hears
 * the whole "Call in progress · Join". At 100% the full line fits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class CallJoinLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val callRow = ChatListPreviewData.rows.first { it.isCallLive && it.isUnread }

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    ConversationRow(
                        row = callRow,
                        avatar = null,
                        onClick = {},
                        onShowActions = {},
                        onJoinCall = {},
                        onMarkRead = {},
                        onOpenProfile = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    @Test
    fun largeFontKeepsTheVerbWholeOnItsOwnLine() {
        show(fontScale = LARGE_FONT)
        val short = context.getString(R.string.feature_chat_list_call_join_short)
        assertFalse("\"$short\" is cut at 200%", layoutOf(short).hasVisualOverflow)
        val pill = compose.onNodeWithText(short, useUnmergedTree = true).fetchSemanticsNode()
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the pill ends past the window", pill.positionInRoot.x + pill.size.width <= window.right)

        // The count sits on the title line now, above the pill rather than beside it.
        val count = compose.onNodeWithText(callRow.unreadCount.toString(), useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "the count (bottom ${count.boundsInRoot.bottom}) is not above the pill (top ${pill.boundsInRoot.top})",
            count.boundsInRoot.bottom <= pill.boundsInRoot.top,
        )

        val spoken = context.getString(R.string.feature_chat_list_call_join)
        compose.onNode(hasContentDescription(spoken), useUnmergedTree = true).assertExists()
    }

    @Test
    fun regularFontShowsTheWholeLine() {
        show(fontScale = 1f)
        val full = context.getString(R.string.feature_chat_list_call_join)
        assertFalse("\"$full\" is cut at 100%", layoutOf(full).hasVisualOverflow)
    }

    private companion object {
        const val LARGE_FONT = 2f
    }
}
