package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MilestoneOccurrence
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.AgendaItem
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The celebration card in the Day view's timeline on the small phone, where it is about 238dp
 * wide (UI10-05): "Send wishes" stays on one line on a row of its own from the card's content
 * start, and the sentence gets more than half the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class CelebrationCardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private val anniversary: AgendaItem.MilestoneCard = run {
        val occurrence = MilestoneOccurrence(
            milestone = Milestone(
                type = MilestoneType.WORK_ANNIVERSARY,
                userId = "usr_jordan_lee",
                displayName = "Jordan Lee",
                month = 11,
                day = 1,
                startYear = 2021,
            ),
            date = LocalDate.of(2026, 11, 1),
            years = 5,
        )
        AgendaItem.MilestoneCard(occurrence, requireNotNull(MilestoneIndex.celebrationOf(listOf(occurrence))))
    }

    private fun show() {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                Box(Modifier.width(DAY_VIEW_CARD)) {
                    AgendaCard(item = anniversary, currentYear = 2026, onOpenEvent = {}, myUserId = "usr_alex")
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
    fun sendWishesStaysOnOneLineFromTheCardsContentStart() {
        show()
        val label = context.getString(R.string.feature_calendar_send_wishes)
        val layout = layoutOf(label)
        assertEquals("\"$label\" wraps inside its button", 1, layout.lineCount)
        assertFalse("\"$label\" is cut", layout.hasVisualOverflow)

        val spoken = context.getString(R.string.feature_calendar_send_wishes_a11y, "Jordan Lee")
        val button = compose.onNode(
            hasContentDescription(spoken) and hasClickAction(),
        ).fetchSemanticsNode().boundsInRoot
        val row = sentenceRow().boundsInRoot
        assertTrue(
            "Send wishes starts at ${button.left}, right of the card's content start at ${row.left}",
            button.left <= row.left + 1f,
        )
    }

    @Test
    fun theSentenceGetsMoreThanHalfTheCard() {
        show()
        val words = compose.onNodeWithText(
            SENTENCE_START,
            substring = true,
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val card = with(compose.density) { DAY_VIEW_CARD.toPx() }
        assertTrue("the sentence is ${words.size.width}px of a ${card}px card", words.size.width > card / 2f)
    }

    /** The avatar-and-sentence row, which TalkBack reads as the sentence. */
    private fun sentenceRow() =
        compose.onNodeWithContentDescription(SENTENCE_START, substring = true).fetchSemanticsNode()

    private companion object {
        /** 360dp less the gutters, the timeline's 68dp time column, its rail and their gaps. */
        val DAY_VIEW_CARD = 238.dp
        const val SENTENCE_START = "Happy"
    }
}
