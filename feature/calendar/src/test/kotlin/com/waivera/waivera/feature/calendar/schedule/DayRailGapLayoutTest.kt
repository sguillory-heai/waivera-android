package com.waivera.waivera.feature.calendar.schedule

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.component.TIMELINE_ENTRY_CONTENT_TEST_TAG
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.ScheduleActions
import com.waivera.waivera.feature.calendar.ScheduleContent
import com.waivera.waivera.feature.calendar.model.MilestoneIndex
import com.waivera.waivera.feature.calendar.model.SchedulePeriods
import com.waivera.waivera.feature.calendar.model.ScheduleView
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Day view on the small phone at 100% font (`schedule_day_small_*_font100`): the gap
 * between one entry's card and the next is the same all along the rail, including under the
 * celebration card, which picks its narrow layout from its own width (UI11-02). The rail row
 * used to reserve the wide layout's intrinsic height and leave about 30dp of empty rail there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class DayRailGapLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val zone = DemoData.ZONE
    private val today: LocalDate = DemoData.NOW.atZone(zone).toLocalDate()

    private fun dayState(): ScheduleUiState {
        val feed = ScheduleFeed(
            range = SchedulePeriods.rangeFor(ScheduleView.DAY, today),
            zoneId = zone.id,
            events = DemoData.events(),
            timeOff = DemoData.timeOff(),
            holidays = DemoData.holidays(),
        )
        return ScheduleStateBuilder.build(
            period = SchedulePeriod(
                view = ScheduleView.DAY,
                date = today,
                today = today,
                zone = zone,
                now = DemoData.NOW,
            ),
            feed = Synced(feed, lastSyncedAt = DemoData.NOW),
            milestones = MilestoneIndex(DemoData.milestones()),
            milestonesError = null,
            savedZoneId = zone.id,
            deviceZone = zone,
            isOffline = false,
            isRefreshing = false,
        )
    }

    @Test
    fun everyGapAlongTheRailIsTheSame() {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                ScheduleContent(state = dayState(), actions = ScheduleActions())
            }
        }
        compose.waitForIdle()

        val cards = compose.onAllNodesWithTag(TIMELINE_ENTRY_CONTENT_TEST_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.top }
        val dividers = compose.onAllNodes(
            hasContentDescription(context.getString(R.string.feature_calendar_now_marker_a11y, ""), substring = true),
        ).fetchSemanticsNodes().map { it.boundsInRoot }
        val wishes = compose.onAllNodesWithText(
            context.getString(R.string.feature_calendar_send_wishes),
            useUnmergedTree = true,
        )
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        assertTrue("no celebration card with Send wishes on the demo day", wishes.isNotEmpty())
        val celebration = cards.indexOfFirst { card -> card.contains(wishes.first().center) }
        assertTrue("Send wishes is outside every rail entry", celebration >= 0)

        // Pairs with the Now line between them are spaced by the line, not by the rail.
        val gaps = cards.indices.drop(1)
            .filter { index -> dividers.none { it.isBetween(cards[index - 1], cards[index]) } }
            .associateWith { index -> cards[index].top - cards[index - 1].bottom }
        assertTrue("only ${gaps.size} rail gaps are on the first screen", gaps.size >= 2)
        assertTrue(
            "the gap under the celebration card is not measured: $gaps",
            celebration + 1 in gaps || celebration in gaps,
        )
        val tolerance = with(compose.density) { TOLERANCE_DP * density }
        val spread = gaps.values.max() - gaps.values.min()
        assertTrue("the rail's gaps differ by ${spread}px (more than 1dp): $gaps", spread <= tolerance)
    }

    private fun Rect.isBetween(above: Rect, below: Rect): Boolean = top >= above.bottom - 1f && bottom <= below.top + 1f

    private companion object {
        const val TOLERANCE_DP = 1f
    }
}
