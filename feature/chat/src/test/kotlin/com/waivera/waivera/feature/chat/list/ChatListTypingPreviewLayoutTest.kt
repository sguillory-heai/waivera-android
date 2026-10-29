package com.waivera.waivera.feature.chat.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.chat.message.TYPING_NAMES_TAG
import com.waivera.waivera.feature.chat.message.TYPING_TAIL_TAG
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
 * The chat list's typing preview (v7 follow-up, item 5): one line at 100% and 200% on the small
 * phone, the name ellipsized first and "is typing…" whole, as the conversation's typing slot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class ChatListTypingPreviewLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    ConversationRow(
                        row = ChatRowUi(
                            id = 1,
                            title = "Care team",
                            leading = RowLeading.Group,
                            preview = "See you at the handover",
                            previewKind = PreviewKind.Text,
                            typingLabel = LongFixtures.NAME_60 + TAIL,
                            typingNamesEnd = LongFixtures.NAME_60.length,
                            timeLabel = "9:41",
                            unreadCount = 2,
                        ),
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

    private fun layoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private fun assertOneLineNameFirst(fontScale: Float) {
        show(fontScale)
        val names = layoutOf(TYPING_NAMES_TAG)
        assertEquals(1, names.lineCount, "the typing name wraps at ${fontScale}x")
        assertTrue(names.isLineEllipsized(0), "the long name is not the part cut at ${fontScale}x")
        assertEquals(
            1,
            compose.onAllNodesWithTag(TYPING_TAIL_TAG, useUnmergedTree = true).fetchSemanticsNodes().size,
            "\"is typing…\" is not its own piece at ${fontScale}x",
        )
        val tail = layoutOf(TYPING_TAIL_TAG)
        assertEquals(TAIL, tail.layoutInput.text.text)
        assertEquals(1, tail.lineCount, "\"is typing…\" wraps at ${fontScale}x")
        assertFalse(tail.isLineEllipsized(0), "\"is typing…\" is ellipsized at ${fontScale}x")
        assertTrue(tail.getLineRight(0) <= tail.size.width, "\"is typing…\" is cut at ${fontScale}x")
    }

    @Test
    fun theTypingPreviewIsOneLineAtDefaultSize() = assertOneLineNameFirst(fontScale = 1f)

    @Test
    fun theTypingPreviewIsOneLineAtLargeFont() = assertOneLineNameFirst(fontScale = 2f)

    private companion object {
        const val TAIL = " is typing…"
    }
}
