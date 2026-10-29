package com.waivera.waivera.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.demo.DemoData
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
import com.waivera.waivera.feature.chat.list.RowLeading
import com.waivera.waivera.feature.chat.message.RowTime
import com.waivera.waivera.feature.chat.newchat.NEW_CHAT_PERSON_NAME_TAG
import com.waivera.waivera.feature.chat.newchat.NewChatSheetContent
import com.waivera.waivera.feature.chat.newchat.NewChatUiState
import com.waivera.waivera.feature.chat.newchat.PersonOptionUi
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
 * Chat names are one line everywhere they title something (v7, item 3): the conversation header,
 * the chat list's rows and the new chat people, with the 60-character and the unbroken
 * 40-character names at 100% and 200% on the small phone. Short names keep the full size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h2400dp-notnight-xhdpi")
class ChatNamesOneLineLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val names = listOf(LongFixtures.NAME_60, LongFixtures.NAME_UNBROKEN_40, SHORT)

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

    private fun assertOneLine(name: String, layout: TextLayoutResult, where: String, baseSize: Float) {
        assertEquals("\"$name\" wraps in $where", 1, layout.lineCount)
        val size = layout.layoutInput.style.fontSize.value
        assertTrue("\"$name\" shrank to $size in $where", size >= baseSize * WaiveraNameFit.MIN_FONT_SCALE - EPSILON)
        if (name == SHORT) {
            assertFalse("\"$name\" is cut in $where", layout.isLineEllipsized(0))
            assertEquals("\"$name\" shrank in $where", baseSize, size, EPSILON)
        }
    }

    private fun row(id: Long, name: String) = ChatRowUi(
        id = id,
        title = name,
        leading = RowLeading.Group,
        preview = "See you at the handover",
        previewKind = PreviewKind.Text,
        timeLabel = "9:41",
        unreadCount = 2,
    )

    private fun chatListRows(fontScale: Float) {
        show(fontScale) {
            Column {
                names.forEachIndexed { i, name ->
                    ConversationRow(
                        row = row(i.toLong() + 1, name),
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
        val nodes = compose.onAllNodesWithTag(CHAT_ROW_NAME_TAG, useUnmergedTree = true)
        names.forEachIndexed { i, name ->
            assertOneLine(name, nodes[i].layout(), "the chat list at ${fontScale}x", TITLE_SMALL)
        }
    }

    private fun newChatPeople(fontScale: Float) {
        show(fontScale) {
            Column {
                NewChatSheetContent(
                    state = NewChatUiState(
                        people = names.mapIndexed { i, name -> PersonOptionUi("usr_$i", name, selected = i == 0) },
                        selectedCount = 1,
                        isLoading = false,
                    ),
                    avatars = emptyMap(),
                    onQueryChange = {},
                    onToggle = {},
                    onSubmit = {},
                    onRetryLoad = {},
                    onVisiblePeople = {},
                )
            }
        }
        val nodes = compose.onAllNodesWithTag(NEW_CHAT_PERSON_NAME_TAG, useUnmergedTree = true)
        names.forEachIndexed { i, name ->
            assertOneLine(name, nodes[i].layout(), "new chat at ${fontScale}x", TITLE_SMALL)
        }
    }

    private fun conversationTitles(fontScale: Float) {
        var title by mutableStateOf(names.first())
        show(fontScale) {
            ConversationScreenContent(
                state = ConversationUiState(title = title, memberCount = 3, content = ConversationContent.Empty),
                draft = "",
                onDraftChange = {},
                time = RowTime(DemoData.NOW, DemoData.ZONE),
                actions = ConversationActions.None,
                skeletonDelayMillis = 0,
            )
        }
        names.forEach { name ->
            title = name
            compose.waitForIdle()
            val layout = compose.onNodeWithTag(TOP_BAR_TITLE_TAG, useUnmergedTree = true).layout()
            assertOneLine(name, layout, "the conversation header at ${fontScale}x", HEADLINE_SMALL)
        }
    }

    @Test
    fun chatListNamesAreOneLineAtDefaultSize() = chatListRows(1f)

    @Test
    fun chatListNamesAreOneLineAtLargeFont() = chatListRows(2f)

    @Test
    fun newChatNamesAreOneLineAtDefaultSize() = newChatPeople(1f)

    @Test
    fun newChatNamesAreOneLineAtLargeFont() = newChatPeople(2f)

    @Test
    fun conversationTitlesAreOneLineAtDefaultSize() = conversationTitles(1f)

    @Test
    fun conversationTitlesAreOneLineAtLargeFont() = conversationTitles(2f)

    private companion object {
        const val SHORT = "Priya Raman"
        const val EPSILON = 0.01f

        /** The sizes of titleSmall and headlineSmall, the row and header name styles. */
        const val TITLE_SMALL = 15f
        const val HEADLINE_SMALL = 21f
    }
}
