package com.waivera.waivera.feature.chat.message

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TalkBack can tap a reaction pill to toggle it and long-press it to see who reacted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class ReactionPillSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val reaction = ReactionSummary(emoji = "👍", count = 3, mine = false, userIds = listOf("a", "b", "c"))

    @Test
    fun pillKeepsClickAndLongClickActions() {
        val toggled = mutableListOf<String>()
        val details = mutableListOf<String>()
        compose.setContent {
            WaiveraTheme {
                MessageEngagementRow(
                    reactions = listOf(reaction),
                    alignEnd = false,
                    onToggle = { toggled += it },
                    onDetails = { details += it },
                )
            }
        }
        val pill = compose.onNodeWithContentDescription("👍, 3")
        pill.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        pill.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        pill.performSemanticsAction(SemanticsActions.OnClick)
        pill.performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(listOf("👍"), toggled)
        assertEquals(listOf("👍"), details)
    }

    @Test
    fun readOnlyPillHasNoActions() {
        compose.setContent {
            WaiveraTheme {
                MessageEngagementRow(reactions = listOf(reaction), alignEnd = false, onToggle = null, onDetails = null)
            }
        }
        compose.onNodeWithContentDescription("👍, 3")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
