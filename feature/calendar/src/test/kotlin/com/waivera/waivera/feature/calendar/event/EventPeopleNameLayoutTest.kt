package com.waivera.waivera.feature.calendar.event

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.data.model.Attendee
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.component.LIST_ROW_NAME_TAG
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.dto.AttendeeType
import com.waivera.waivera.core.network.dto.InviteStatus
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.calendar.EventContent
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
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
 * The event's organizer and attendee rows with the long-name fixtures (wave 29 N5): each name is
 * one line at 100% and two at 200%, with an ellipsis and no mid-word break. A tall window on the
 * small phone's width keeps every row composed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h2400dp-notnight-xhdpi")
class EventPeopleNameLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val names = LongFixtures.NAMES.values.toList()

    private fun show(fontScale: Float) {
        val attendees = names.mapIndexed { i, name ->
            Attendee(
                id = "att-$i",
                type = AttendeeType.EMPLOYEE,
                userId = "usr_a$i",
                displayName = name,
                inviteStatus = InviteStatus.entries[i % InviteStatus.entries.size],
            )
        } + Attendee("att-me", AttendeeType.EMPLOYEE, DemoData.ME, displayName = "Alex Rivera")
        val event = DemoData.events().first { it.id == "demo_invite" }.copy(
            ownerName = LongFixtures.NAME_60,
            ownerUserId = "usr_owner",
            attendees = attendees,
        )
        val state = EventUiState.Loaded(
            EventRules.details(
                event = event,
                zone = DemoData.ZONE,
                milestones = MilestoneIndex(emptyList()),
                now = DemoData.NOW,
                myUserId = DemoData.ME,
                savedZoneId = DemoData.ZONE.id,
                deviceZone = DemoData.ZONE,
            ),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    EventContent(state = state, actions = EventActions())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertRows(fontScale: Float, maxLines: Int) {
        show(fontScale)
        val nodes = compose.onAllNodesWithTag(LIST_ROW_NAME_TAG, useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertEquals("the organizer and every attendee", names.size + 2, count)
        repeat(count) { i ->
            val results = mutableListOf<TextLayoutResult>()
            nodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.first()
            val text = layout.layoutInput.text.text
            assertTrue("\"$text\" takes ${layout.lineCount} lines at ${fontScale}x", layout.lineCount <= maxLines)
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                assertFalse(
                    "\"$text\" breaks mid-word at ${fontScale}x after \"${text.substring(0, end)}\"",
                    text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit(),
                )
            }
        }
    }

    @Test
    fun oneLineAtDefaultSize() = assertRows(fontScale = 1f, maxLines = 1)

    @Test
    fun twoLinesAtLargeFont() = assertRows(fontScale = 2f, maxLines = 2)
}
