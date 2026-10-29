package com.waivera.waivera.feature.chat.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.component.LIST_ROW_NAME_TAG
import com.waivera.waivera.core.designsystem.component.TOP_BAR_SUBTITLE_BUTTON_TAG
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.chat.ConversationActions
import com.waivera.waivera.feature.chat.ConversationScreenContent
import com.waivera.waivera.feature.chat.conversation.ConversationContent
import com.waivera.waivera.feature.chat.conversation.ConversationUiState
import com.waivera.waivera.feature.chat.message.ChatSheetCallbacks
import com.waivera.waivera.feature.chat.message.MessageRowCallbacks
import com.waivera.waivera.feature.chat.message.RowTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The members screen's rows (v7, item 4): a row opens the person, Message opens their chat and
 * is a full touch target, my own row says "You" with no Message, names stay on one line at 200%,
 * and the conversation header's "N members" line is a button that opens the list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h2400dp-notnight-xhdpi")
class GroupMembersScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val messaged = mutableListOf<String>()

    private val rows = listOf(
        MemberRowUi(DemoData.ME, "Alex Rivera", isMe = true),
        MemberRowUi(DemoData.JORDAN, LongFixtures.NAME_UNBROKEN_40, isMe = false),
        MemberRowUi("usr_long", LongFixtures.NAME_60, isMe = false),
        MemberRowUi(DemoData.PRIYA, "Priya Raman", isMe = false),
    )

    private fun showMembers(fontScale: Float = 1f, state: GroupMembersUiState = content()) = show(fontScale) {
        GroupMembersContent(
            state = state,
            actions = GroupMembersActions(onOpenPerson = { opened += it }, onMessage = { messaged += it }),
        )
    }

    private fun show(fontScale: Float = 1f, content: @Composable () -> Unit) {
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

    private fun clickLabel(label: String) = SemanticsMatcher("click label '$label'") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun content() = GroupMembersUiState(phase = MembersPhase.Content, rows = rows, totalCount = rows.size)

    @Test
    fun aRowOpensThatPerson() {
        showMembers()
        compose.onNodeWithTag(MEMBER_ROW_TAG_PREFIX + DemoData.PRIYA).performClick()
        assertEquals(listOf(DemoData.PRIYA), opened)
        assertTrue("opening a row is not a message", messaged.isEmpty())
    }

    @Test
    fun messageOpensTheirChatAndIsAFullTouchTarget() {
        showMembers()
        val button = compose.onNodeWithContentDescription("Message Priya Raman")
        val bounds = button.getBoundsInRoot()
        assertTrue("Message is narrower than 48dp", bounds.width >= 48.dp)
        assertTrue("Message is shorter than 48dp", bounds.height >= 48.dp)
        button.performClick()
        assertEquals(listOf(DemoData.PRIYA), messaged)
        assertTrue("Message does not also open the profile", opened.isEmpty())
    }

    @Test
    fun myRowSaysYouAndHasNoMessage() {
        showMembers()
        compose.onNodeWithText("You", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Message Alex Rivera").assertDoesNotExist()
        compose.onNodeWithContentDescription("Message ${LongFixtures.NAME_UNBROKEN_40}").assertExists()
    }

    @Test
    fun aRowSaysWhatATapDoes() {
        showMembers()
        compose.onNodeWithTag(MEMBER_ROW_TAG_PREFIX + DemoData.ME)
            .assert(clickLabel("Open profile"))
    }

    @Test
    fun whileOneChatOpensTheOthersWait() {
        showMembers(state = content().copy(openingChatWith = DemoData.PRIYA))
        compose.onNodeWithContentDescription("Opening chat with Priya Raman").assertExists()
        compose.onNodeWithContentDescription("Message ${LongFixtures.NAME_60}").performClick()
        assertTrue("a second chat opened while the first was busy", messaged.isEmpty())
    }

    @Test
    fun namesStayOnOneLineAtLargeFont() {
        showMembers(fontScale = 2f)
        val names = compose.onAllNodesWithTag(LIST_ROW_NAME_TAG, useUnmergedTree = true)
        names.assertCountEquals(rows.size)
        rows.forEachIndexed { i, row ->
            val results = mutableListOf<TextLayoutResult>()
            names[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals("\"${row.name}\" wraps at 200%", 1, results.first().lineCount)
        }
    }

    @Test
    fun theMembersLineInTheHeaderIsAButtonThatOpensTheList() {
        var openedMembers = 0
        show {
            ConversationScreenContent(
                state = ConversationUiState(
                    title = "Priya Raman, Jordan Lee",
                    memberCount = 3,
                    content = ConversationContent.Empty,
                ),
                draft = "",
                onDraftChange = {},
                time = RowTime(DemoData.NOW, DemoData.ZONE),
                actions = ConversationActions(
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
                    MessageRowCallbacks.None,
                    ChatSheetCallbacks.None,
                    onOpenMembers = { openedMembers++ },
                ),
                skeletonDelayMillis = 0,
            )
        }
        val subtitle = compose.onNodeWithTag(TOP_BAR_SUBTITLE_BUTTON_TAG)
        subtitle.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        subtitle.assert(clickLabel("Show members"))
        subtitle.assert(hasContentDescription("3 members", substring = true))
        subtitle.performClick()
        assertEquals(1, openedMembers)
    }

    @Test
    fun aDirectChatHeaderHasNoMembersButton() {
        show {
            ConversationScreenContent(
                state = ConversationUiState(title = "Priya Raman", content = ConversationContent.Empty),
                draft = "",
                onDraftChange = {},
                time = RowTime(DemoData.NOW, DemoData.ZONE),
                actions = ConversationActions.None,
                skeletonDelayMillis = 0,
            )
        }
        compose.onNodeWithTag(TOP_BAR_SUBTITLE_BUTTON_TAG).assertDoesNotExist()
    }
}
