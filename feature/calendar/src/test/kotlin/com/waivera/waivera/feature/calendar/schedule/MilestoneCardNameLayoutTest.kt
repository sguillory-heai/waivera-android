package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.MilestoneOccurrence
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.dto.MilestoneType
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
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
import org.robolectric.annotation.GraphicsMode

/**
 * The schedule's milestone cards with the long-name fixtures, in the Day view's narrow card on
 * the small phone (wave 29 N3): the name keeps to two lines with an ellipsis and wraps only at a
 * space or after a hyphen, at 100% and 200%. A normal name is whole.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class MilestoneCardNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val names = LongFixtures.NAMES.values.toList() + NORMAL

    private val cards: List<AgendaItem.MilestoneCard> = names.mapIndexed { i, name ->
        val birthday = i % 2 == 0
        val occurrence = MilestoneOccurrence(
            milestone = Milestone(
                type = if (birthday) MilestoneType.BIRTHDAY else MilestoneType.WORK_ANNIVERSARY,
                userId = "usr_m$i",
                displayName = name,
                month = 11,
                day = 1,
                startYear = if (birthday) null else 2026 - YEARS,
            ),
            date = LocalDate.of(2026, 11, 1),
            years = if (birthday) null else YEARS,
        )
        AgendaItem.MilestoneCard(occurrence, requireNotNull(MilestoneIndex.celebrationOf(listOf(occurrence))))
    }

    private fun plainSentence(index: Int): String = if (index % 2 == 0) {
        context.getString(R.string.feature_calendar_celebration_full_birthday, names[index])
    } else {
        context.getString(R.string.feature_calendar_celebration_full_anniversary, names[index], YEARS)
    }

    private fun sentences(fontScale: Float): List<TextLayoutResult> {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    Column(Modifier.width(DAY_VIEW_CARD)) {
                        cards.forEach { card ->
                            AgendaCard(item = card, currentYear = 2026, onOpenEvent = {}, myUserId = "usr_alex")
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val nodes = compose.onAllNodesWithTag(MILESTONE_SENTENCE_TAG, useUnmergedTree = true)
        return cards.indices.map { i ->
            val results = mutableListOf<TextLayoutResult>()
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.first()
        }
    }

    private fun assertNamesKeepToTwoLines(fontScale: Float) {
        sentences(fontScale).forEachIndexed { i, layout ->
            val name = names[i]
            val text = layout.layoutInput.text.text
            val start = plainSentence(i).lastIndexOf(name)
            assertTrue("\"$name\" is not in its sentence", start >= 0)
            val lines = layout.lineCount - layout.getLineForOffset(start)
            assertTrue("\"$name\" takes $lines lines at ${fontScale}x", lines <= 2)
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertFalse(
                    "\"$name\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
                    text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
                )
            }
            for (line in 0 until layout.lineCount) {
                assertTrue("\"$name\" line $line runs past the card", layout.getLineRight(line) <= layout.size.width)
            }
            if (name in NORMAL) {
                assertFalse("\"$name\" is ellipsized", layout.isLineEllipsized(layout.lineCount - 1))
                assertEquals("\"$name\" gets a line limit", Int.MAX_VALUE, layout.layoutInput.maxLines)
            }
        }
    }

    @Test
    fun namesKeepToTwoLinesAtDefaultSize() = assertNamesKeepToTwoLines(fontScale = 1f)

    @Test
    fun namesKeepToTwoLinesAtLargeFont() = assertNamesKeepToTwoLines(fontScale = LARGE_FONT)

    private companion object {
        /** 360dp less the gutters, the timeline's 68dp time column, its rail and their gaps. */
        val DAY_VIEW_CARD = 238.dp
        const val LARGE_FONT = 2f
        const val YEARS = 12
        val NORMAL = listOf("Jordan Lee", "Sam Ortiz")
    }
}
