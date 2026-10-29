package com.waivera.waivera.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.component.LIST_ROW_NAME_TAG
import com.waivera.waivera.core.designsystem.component.TOP_BAR_SUBTITLE_TAG
import com.waivera.waivera.core.designsystem.component.TOP_BAR_TITLE_TAG
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraNameFit
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.chat.conversation.ConversationContent
import com.waivera.waivera.feature.chat.conversation.ConversationUiState
import com.waivera.waivera.feature.chat.list.CHAT_ROW_NAME_TAG
import com.waivera.waivera.feature.chat.list.ChatRowUi
import com.waivera.waivera.feature.chat.list.ConversationRow
import com.waivera.waivera.feature.chat.list.PreviewKind
import com.waivera.waivera.feature.chat.list.RowActionsContent
import com.waivera.waivera.feature.chat.list.RowLeading
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A group title composed around a name keeps its count (v7 follow-up, item 4): with the
 * 60-character name and 4 others, the chat list row, the conversation header, the row actions
 * header and the thread header each show "& 4 others" whole, on one line, no smaller than the
 * 0.85 floor, at 100% and 200% on the small phone. Only the name is cut, and TalkBack reads the
 * whole title. When not even "… & 4 others" fits beside the time (the compact name, 200%, small
 * phone, "Yesterday"), the chat list row shows the name and "+4" instead, never losing the count.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class GroupTitleCountLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    content()
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

    private fun assertCountShows(
        node: SemanticsNodeInteraction,
        where: String,
        baseSize: Float,
        spoken: String = TITLE,
    ) {
        // TalkBack reads the whole title, name and count, whatever is drawn.
        node.assert(hasText(spoken))
        val layout = node.layout()
        val shown = layout.layoutInput.text.text
        assertEquals("\"$shown\" wraps in $where", 1, layout.lineCount)
        assertTrue("\"$shown\" lost its count in $where", shown.endsWith(SUFFIX))
        assertFalse("\"$shown\" is ellipsized at its end in $where", layout.isLineEllipsized(0))
        assertEquals("\"$shown\" is cut in $where", shown.length, layout.getLineEnd(0, visibleEnd = true))
        assertTrue("\"$shown\" is wider than its line in $where", layout.getLineRight(0) <= layout.size.width)
        val size = layout.layoutInput.style.fontSize.value
        assertTrue("\"$shown\" shrank to $size in $where", size >= baseSize * WaiveraNameFit.MIN_FONT_SCALE - EPSILON)
        // The name is what gave way: the title is too long to show whole on the small phone.
        assertTrue("\"$shown\" kept the whole name in $where", !shown.contains(LongFixtures.NAME_60))
    }

    private val row = ChatRowUi(
        id = 1,
        title = TITLE,
        titleNameRange = NAME_RANGE,
        leading = RowLeading.Group,
        preview = "See you at the handover",
        previewKind = PreviewKind.Text,
        timeLabel = "9:41",
        unreadCount = 2,
    )

    private fun chatList(fontScale: Float) {
        show(fontScale) {
            ConversationRow(
                row = row,
                avatar = null,
                onClick = {},
                onShowActions = {},
                onJoinCall = {},
                onMarkRead = {},
                onOpenProfile = {},
            )
        }
        val name = compose.onNodeWithTag(CHAT_ROW_NAME_TAG, useUnmergedTree = true)
        assertCountShows(name, "the chat list at ${fontScale}x", TITLE_SMALL)
    }

    private fun header(fontScale: Float) {
        show(fontScale) {
            ConversationScreenContent(
                state = ConversationUiState(
                    title = TITLE,
                    titleNameRange = NAME_RANGE,
                    memberCount = 6,
                    content = ConversationContent.Empty,
                ),
                draft = "",
                onDraftChange = {},
                time = RowTime(DemoData.NOW, DemoData.ZONE),
                actions = ConversationActions.None,
                skeletonDelayMillis = 0,
            )
        }
        val title = compose.onNodeWithTag(TOP_BAR_TITLE_TAG, useUnmergedTree = true)
        assertCountShows(title, "the conversation header at ${fontScale}x", HEADLINE_SMALL)
    }

    private fun rowActions(fontScale: Float) {
        show(fontScale) {
            Column {
                RowActionsContent(row = row, onOpen = {}, onMarkRead = {}, onJoinCall = {}, onOpenProfile = {})
            }
        }
        val title = compose.onNodeWithTag(LIST_ROW_NAME_TAG, useUnmergedTree = true)
        assertCountShows(title, "the row actions header at ${fontScale}x", TITLE_SMALL)
    }

    private fun threadHeader(fontScale: Float) {
        show(fontScale) {
            ThreadScreenContent(
                state = ThreadUiState(
                    content = ThreadContent.Invalid,
                    conversationTitle = TITLE,
                    conversationTitleNameRange = NAME_RANGE,
                ),
                draft = "",
                onDraftChange = {},
                time = RowTime(DemoData.NOW, DemoData.ZONE),
                actions = ThreadActions.None,
                skeletonDelayMillis = 0,
            )
        }
        val subtitle = compose.onNodeWithTag(TOP_BAR_SUBTITLE_TAG, useUnmergedTree = true)
        val shown = subtitle.layout().layoutInput.text.text
        assertTrue("the thread line \"$shown\" lost its \"In\"", shown.startsWith("In "))
        assertCountShows(subtitle, "the thread header at ${fontScale}x", LABEL_MEDIUM, spoken = "In $TITLE")
    }

    @Test
    fun theChatListKeepsTheCount() = chatList(1f)

    @Test
    fun theChatListShortensTheCountBesideTheTimeAtLargeFont() {
        val title = "M. Rui" + SUFFIX
        show(fontScale = 2f) {
            ConversationRow(
                row = row.copy(
                    title = title,
                    titleNameRange = 0 until "M. Rui".length,
                    titleCompactCount = "+4",
                    timeLabel = "Yesterday",
                    isCallLive = true,
                ),
                avatar = null,
                onClick = {},
                onShowActions = {},
                onJoinCall = {},
                onMarkRead = {},
                onOpenProfile = {},
            )
        }
        val name = compose.onNodeWithTag(CHAT_ROW_NAME_TAG, useUnmergedTree = true)
        name.assert(hasText(title))
        val layout = name.layout()
        val shown = layout.layoutInput.text.text
        assertTrue("\"$shown\" lost its count beside the time", shown.endsWith("\u00A0+4"))
        assertTrue("\"$shown\" lost the start of the name", shown.startsWith("M"))
        assertEquals("\"$shown\" wraps", 1, layout.lineCount)
        assertFalse("\"$shown\" is ellipsized at its end", layout.isLineEllipsized(0))
        assertEquals("\"$shown\" is cut", shown.length, layout.getLineEnd(0, visibleEnd = true))
        assertTrue("\"$shown\" is wider than its line", layout.getLineRight(0) <= layout.size.width)
        val size = layout.layoutInput.style.fontSize.value
        assertTrue("\"$shown\" shrank to $size", size >= TITLE_SMALL * WaiveraNameFit.MIN_FONT_SCALE - EPSILON)
    }

    @Test
    fun theChatListKeepsTheCountAtLargeFont() = chatList(2f)

    @Test
    fun theHeaderKeepsTheCount() = header(1f)

    @Test
    fun theHeaderKeepsTheCountAtLargeFont() = header(2f)

    @Test
    fun theRowActionsHeaderKeepsTheCount() = rowActions(1f)

    @Test
    fun theRowActionsHeaderKeepsTheCountAtLargeFont() = rowActions(2f)

    @Test
    fun theThreadHeaderKeepsTheCount() = threadHeader(1f)

    @Test
    fun theThreadHeaderKeepsTheCountAtLargeFont() = threadHeader(2f)

    private companion object {
        const val SUFFIX = " & 4 others"
        const val TITLE = LongFixtures.NAME_60 + SUFFIX
        val NAME_RANGE = 0 until LongFixtures.NAME_60.length
        const val EPSILON = 0.01f

        /** The sizes of titleSmall, headlineSmall and labelMedium: the row, header and thread line styles. */
        const val TITLE_SMALL = 15f
        const val HEADLINE_SMALL = 21f
        const val LABEL_MEDIUM = 13f
    }
}
