package com.waivera.waivera.feature.today.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.TodayStats
import com.waivera.waivera.feature.today.model.TodayUiState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What TalkBack and taps get from Today's hero: the ring, the stats and the next item. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotSdk.VALUE])
class TodayHeroRobolectricTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun show(state: TodayUiState, onOpenEvent: (String) -> Unit = {}) {
        val callbacks = TodayCallbacks(
            onAction = {},
            onAccountClick = {},
            onOpenConversation = {},
            onOpenMessage = { _, _, _ -> },
            onOpenSchedule = {},
            onOpenEvent = onOpenEvent,
        )
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                TodayHero(state = state, callbacks = callbacks)
            }
        }
    }

    @Test
    fun theRingReadsAsOneSentenceWithItsProgress() {
        show(TodayPreviewStates.populated())
        val ring = compose.onNodeWithContentDescription("5 of 7 handled today").fetchSemanticsNode()
        val range = ring.config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(5f / 7f, range.current, 0.001f)
        compose.onNodeWithText("2 things need you").assertExists()
        compose.onNodeWithText(context.getString(R.string.feature_today_brand_handled_since, 5)).assertExists()
    }

    @Test
    fun eachStatTileIsOneLabelledItem() {
        show(TodayPreviewStates.populated())
        compose.onNodeWithContentDescription("3 events today").assertExists()
        compose.onNodeWithContentDescription("6 unread chats").assertExists()
        compose.onNodeWithContentDescription("14 events this week").assertExists()
    }

    @Test
    fun aStatStillLoadingSaysSoInsteadOfZero() {
        show(TodayPreviewStates.populated().copy(stats = TodayStats(eventsToday = 1)))
        compose.onNodeWithContentDescription("1 event today").assertExists()
        compose.onNodeWithContentDescription("unread chats, still loading").assertExists()
        compose.onNodeWithContentDescription("events this week, still loading").assertExists()
    }

    @Test
    fun theNextItemOpensItsEvent() {
        var opened: String? = null
        show(TodayPreviewStates.populated(), onOpenEvent = { opened = it })
        val next = compose.onNode(hasClickAction() and hasContentDescription("Next:", substring = true))
        next.assertHasClickAction()
        assertEquals(
            context.getString(R.string.feature_today_open_event),
            next.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        next.performClick()
        assertEquals("demo_allday", opened, "the hero shows the first Up next item")
    }

    @Test
    fun allCaughtUpShowsTheRewardWithItsOneEmoji() {
        show(TodayPreviewStates.caughtUp())
        compose.onNode(hasText(context.getString(R.string.feature_today_all_caught_up_title))).assertExists()
        compose.onNodeWithText("All 9 handled today. Nice work, Alex.").assertExists()
        compose.onNodeWithContentDescription("9 of 9 handled today").assertExists()
    }

    @Test
    fun heroCopyCarriesNoEmoji() {
        listOf(
            context.getString(R.string.feature_today_brand_ring_a11y, 5, 9),
            context.getString(R.string.feature_today_brand_needs_you_none),
            context.getString(R.string.feature_today_brand_handled_since, 5),
            context.getString(R.string.feature_today_brand_handled_none),
            context.getString(R.string.feature_today_brand_day_open),
            context.resources.getQuantityString(R.plurals.feature_today_brand_caught_up_named, 9, 9, "Alex"),
            context.resources.getQuantityString(R.plurals.feature_today_brand_needs_you_count, 4, 4),
        ).forEach { copy ->
            assertFalse(copy.any { Character.getType(it) == Character.SURROGATE.toInt() }, "\"$copy\" holds an emoji")
        }
    }
}
