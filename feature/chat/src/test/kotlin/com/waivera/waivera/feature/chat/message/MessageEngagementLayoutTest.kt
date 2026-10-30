package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reactions and "N replies" sit side by side under the bubble, on its side, and the bubble holds
 * only the message (owner's device review, 2026-10-30): with them it is the size it is without
 * them. Chips are sized from their text, so they grow with the font from 85% to 200%.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class MessageEngagementLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun render(fontScale: Float = 1f, content: @Composable () -> Unit) {
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

    private fun bubbles(text: String): List<DpRect> {
        val nodes = compose.onAllNodes(hasContentDescription(text, substring = true))
        return nodes.fetchSemanticsNodes().indices.map { nodes[it].getBoundsInRoot() }
    }

    private fun bubble(text: String): DpRect = bubbles(text).single()

    private fun chip(label: String): DpRect = compose.onNodeWithContentDescription(label).getBoundsInRoot()

    private fun replies(index: Int = 0): DpRect =
        compose.onAllNodesWithText(ChatPolishFixtures.REPLIES_TWO)[index].getBoundsInRoot()

    @Test
    fun theBubbleIsTheSameSizeWithOrWithoutReactionsAndReplies() {
        val engaged = ChatPolishFixtures.engagementRows().first()
        val plain = engaged.copy(
            key = "plain",
            message = engaged.message.copy(id = 99, threadReplyCount = 0),
            reactions = emptyList(),
        )
        render { ChatPolishFixtures.Rows(listOf(engaged, plain)) }
        val (withChips, without) = bubbles(ChatPolishFixtures.SHORT)
        assertEquals("the bubble kept room for its chips", without.height.value, withChips.height.value, TOLERANCE)
        assertEquals("the bubble grew wider for its chips", without.width.value, withChips.width.value, TOLERANCE)
    }

    @Test
    fun incomingChipsStartUnderTheBubblesLeftEdgeReactionsThenReplies() {
        render { ChatPolishFixtures.Rows(ChatPolishFixtures.engagementRows().take(1)) }
        val bubble = bubble(ChatPolishFixtures.SHORT)
        val thumbs = chip("👍, 1, including you")
        val heart = chip("❤️, 2")
        val replies = replies()
        assertEquals(
            "the first chip does not start at the bubble's left edge",
            bubble.left.value,
            thumbs.left.value,
            TOLERANCE,
        )
        assertTrue("chips $thumbs are not under the bubble $bubble", thumbs.top >= bubble.bottom)
        assertGap(bubble.bottom, thumbs.top, ChatDimens.EngagementTop)
        assertTrue("reactions are not before replies", thumbs.right <= heart.left && heart.right <= replies.left)
        assertEquals("chips are not on one line", centerY(thumbs), centerY(replies), TOLERANCE)
    }

    @Test
    fun myChipsEndUnderTheBubblesRightEdge() {
        render { ChatPolishFixtures.Rows(ChatPolishFixtures.engagementRows().drop(1)) }
        val bubble = bubble("On it")
        val replies = replies()
        assertEquals(
            "my chips do not end at the bubble's right edge",
            bubble.right.value,
            replies.right.value,
            TOLERANCE,
        )
        assertTrue("replies $replies is not under the bubble $bubble", replies.top >= bubble.bottom)
    }

    @Test
    fun manyReactionsWrapInsideTheColumnWithRepliesLast() {
        render { ChatPolishFixtures.Rows(ChatPolishFixtures.engagementRows(ChatPolishFixtures.manyReactions).take(1)) }
        val bubble = bubble(ChatPolishFixtures.SHORT)
        val first = chip("👍, 1, including you")
        val last = chip("👏, 10")
        val replies = replies()
        assertTrue("$last did not wrap below $first", last.top > first.bottom)
        assertTrue("replies $replies is not after the last reaction $last", replies.top >= last.top)
        val columnRight = SMALL_PHONE_WIDTH - WaiveraSpacing.Gutter
        assertTrue("a chip ran past the column", maxOf(last.right, replies.right) <= columnRight)
        assertEquals(
            "the second line does not start at the bubble's edge",
            bubble.left.value,
            last.left.value.coerceAtMost(replies.left.value),
            TOLERANCE,
        )
    }

    @Test
    fun chipsAreSizedFromTheirTextAt85Percent() = chipFitsItsText(fontScale = SMALLEST_FONT)

    @Test
    fun chipsAreSizedFromTheirTextAt100Percent() = chipFitsItsText(fontScale = 1f)

    @Test
    fun chipsAreSizedFromTheirTextAt200Percent() = chipFitsItsText(fontScale = LARGEST_FONT)

    private fun chipFitsItsText(fontScale: Float) {
        render(fontScale) { ChatPolishFixtures.Rows(ChatPolishFixtures.engagementRows().take(1)) }
        val chip = replies()
        val text = compose.onNodeWithText(ChatPolishFixtures.REPLIES_TWO, useUnmergedTree = true).getBoundsInRoot()
        assertTrue(
            "\"2 replies\" $text is clipped by its chip $chip at $fontScale",
            text.top >= chip.top && text.bottom <= chip.bottom,
        )
        assertEquals(
            "the chip is not its text plus padding at $fontScale",
            (text.height + ChatDimens.EngagementChipPaddingY * 2).value,
            chip.height.value,
            TOLERANCE,
        )
    }

    /**
     * A chip is drawn at its own size, but a tap a little outside it still lands on it: Compose
     * gives anything smaller than 48dp a 48dp touch target, so the row needs no room for one.
     */
    @Test
    fun aTapJustBelowAReactionChipStillTogglesIt() {
        val toggled = mutableListOf<String>()
        render {
            Box(Modifier.width(SMALL_PHONE_WIDTH).padding(WaiveraSpacing.ExtraLarge)) {
                MessageEngagementRow(
                    reactions = ChatPolishFixtures.reactions.take(1),
                    alignEnd = false,
                    onToggle = { toggled += it },
                    onDetails = {},
                )
            }
        }
        compose.onNodeWithContentDescription("👍, 1, including you").performTouchInput {
            click(
                Offset(
                    centerX,
                    height + OUTSIDE_TAP.toPx(),
                ),
            )
        }
        compose.waitForIdle()
        assertEquals(listOf("👍"), toggled)
    }

    @Test
    fun readOnlyRowsShowReactionsWithoutTheRepliesChip() {
        render {
            MessageRowView(
                row = ChatPolishFixtures.engagementRows().first(),
                authorName = "Priya Raman",
                time = ChatPolishFixtures.time,
                timestampVisible = false,
                highlighted = false,
                callbacks = MessageRowCallbacks.None,
            )
        }
        assertEquals(1, compose.onAllNodesWithContentDescription("❤️, 2").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText(ChatPolishFixtures.REPLIES_TWO).fetchSemanticsNodes().size)
    }

    private fun assertGap(above: Dp, below: Dp, expected: Dp) =
        assertEquals("gap", expected.value, (below - above).value, TOLERANCE)

    private fun centerY(rect: DpRect): Float = (rect.top.value + rect.bottom.value) / 2

    private companion object {
        const val TOLERANCE = 0.75f
        const val SMALLEST_FONT = 0.85f
        const val LARGEST_FONT = 2f
        val SMALL_PHONE_WIDTH = 360.dp
        val OUTSIDE_TAP = 8.dp
    }
}
