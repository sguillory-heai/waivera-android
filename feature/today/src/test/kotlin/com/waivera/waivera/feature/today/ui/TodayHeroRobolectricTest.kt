package com.waivera.waivera.feature.today.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.NeedsYouKind
import com.waivera.waivera.feature.today.model.NeedsYouKinds
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
        // Option A: the line under the headline says what is waiting, not the handled count again.
        compose.onNodeWithText("1 message · 1 invite").assertExists()
    }

    /** Option A: the ring holds only the count, "5" and "/7"; the word "handled" is gone. */
    @Test
    fun theRingShowsOnlyTheCount() {
        show(TodayPreviewStates.populated())
        compose.onNodeWithText("5", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("/7", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("handled", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("since this morning", substring = true).assertCountEquals(0)
    }

    @Test
    fun theWaitingLineNamesEachKindWithItsPlural() {
        assertEquals(
            "1 message · 1 invite · 1 ticket",
            breakdown(NeedsYouKind.Message to 1, NeedsYouKind.Invitation to 1, NeedsYouKind.Ticket to 1),
        )
        assertEquals(
            "2 messages · 3 invites · 4 tickets",
            breakdown(NeedsYouKind.Message to 2, NeedsYouKind.Invitation to 3, NeedsYouKind.Ticket to 4),
        )
        assertEquals("1 not sent · 1 mention", breakdown(NeedsYouKind.Mention to 1, NeedsYouKind.NotSent to 1))
        assertEquals("2 mentions", breakdown(NeedsYouKind.Mention to 2, NeedsYouKind.Ticket to 0))
        assertEquals("", breakdown(NeedsYouKind.Message to 0), "nothing waiting is no line")
        listOf(0, 1, 5).forEach { n ->
            val expected = if (n == 1) "1 invite" else "$n invites"
            assertEquals(expected, quantity(R.plurals.feature_today_brand_kind_invites, n))
        }
        assertEquals("0 messages", quantity(R.plurals.feature_today_brand_kind_messages, 0))
        assertEquals("1 message", quantity(R.plurals.feature_today_brand_kind_messages, 1))
        assertEquals("12 messages", quantity(R.plurals.feature_today_brand_kind_messages, 12))
    }

    @Test
    fun aKindWithNothingWaitingIsLeftOutOfTheHero() {
        val kinds = NeedsYouKinds(mapOf(NeedsYouKind.Ticket to 2, NeedsYouKind.Message to 0))
        show(TodayPreviewStates.populated().let { it.copy(needsYou = it.needsYou.copy(kinds = kinds)) })
        compose.onNodeWithText("2 tickets").assertExists()
        compose.onAllNodesWithText("0 messages", substring = true).assertCountEquals(0)
    }

    @Test
    fun theWaitingKindsAreCountedFromEveryOpenItem() {
        val items = TodayPreviewStates.populated().needsYou.items
        assertEquals(
            listOf(NeedsYouKind.Message to 1, NeedsYouKind.Invitation to 1),
            NeedsYouKinds.of(items).parts,
        )
        assertEquals(emptyList(), NeedsYouKinds.of(emptyList()).parts)
    }

    private fun quantity(id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)

    private fun breakdown(vararg counts: Pair<NeedsYouKind, Int>): String = NeedsYouKinds(
        counts.toMap(),
    ).parts.joinToString(context.getString(R.string.feature_today_brand_kind_separator)) { (kind, n) ->
        quantity(kind.pluralRes, n)
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
        assertEquals("demo_visit", opened, "the hero features the first timed Up next item, not the all-day one")
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
            breakdown(NeedsYouKind.Message to 2, NeedsYouKind.Invitation to 1, NeedsYouKind.Ticket to 1),
            context.getString(R.string.feature_today_brand_day_open),
            context.resources.getQuantityString(R.plurals.feature_today_brand_caught_up_named, 9, 9, "Alex"),
            context.resources.getQuantityString(R.plurals.feature_today_brand_needs_you_count, 4, 4),
        ).forEach { copy ->
            assertFalse(copy.any { Character.getType(it) == Character.SURROGATE.toInt() }, "\"$copy\" holds an emoji")
        }
    }
}
