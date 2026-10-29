package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import com.waivera.waivera.core.data.model.TicketUpdate
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.testing.LongFixtures
import com.waivera.waivera.feature.chat.R
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
 * The ticket card with the wave 28 long-content fixtures on the small phone. The meta labels
 * ("Created by", "Created", "Due") keep their one-line width beside a 60-character name or a long
 * raw due label, which used to squeeze them to one letter per line and lay "Due" over its value
 * (B3). At 200% a long type wraps the CRITICAL badge to a line of its own on the card instead of
 * pushing it off (B4).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class TicketCardLongContentLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val createdBy = context.getString(R.string.feature_chat_ticket_created_by)
    private val created = context.getString(R.string.feature_chat_ticket_created)
    private val due = context.getString(R.string.feature_chat_ticket_due)

    private fun show(ticket: TicketNotification, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
                    // Tall cards scroll, so every row is laid out at its full height.
                    Box(Modifier.verticalScroll(rememberScrollState()).padding(WaiveraSpacing.Medium)) {
                        TicketCard(ticket = ticket, onOpen = {}, modifier = Modifier.testTag(CARD_TAG))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun node(text: String): SemanticsNode =
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()

    /** Unclipped, so a row below the window still reports where it is. */
    private fun bounds(text: String): Rect = node(text).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.first()
    }

    private fun assertReadable(label: String) {
        val layout = layoutOf(label)
        assertEquals("\"$label\" wraps instead of keeping its width", 1, layout.lineCount)
        assertFalse("\"$label\" is ellipsized", layout.isLineEllipsized(0))
        assertFalse("\"$label\" is cut", layout.didOverflowWidth)
    }

    /** Beside its label, or under it when the row stacks; never over it. */
    private fun assertClearOf(label: String, value: String) {
        val l = bounds(label)
        val v = bounds(value)
        assertTrue(
            "\"$label\" $l and its value $v overlap",
            l.right <= v.left || l.bottom <= v.top,
        )
    }

    @Test
    fun labelsKeepTheirWidthBesideALongName() {
        show(longTicket(), fontScale = 1f)
        listOf(createdBy, created, due).forEach(::assertReadable)
        assertClearOf(createdBy, LongFixtures.NAME_60)
    }

    @Test
    fun aLongRawDueLabelWrapsBesideDueWithoutCoveringIt() {
        show(unbrokenTicket(), fontScale = 1f)
        listOf(createdBy, created, due).forEach(::assertReadable)
        assertClearOf(due, LongFixtures.CODE_LIKE)
        assertClearOf(createdBy, UNBROKEN_CREATOR)
        // There is room beside "Due" on the small phone, so the value wraps there.
        assertTrue(layoutOf(LongFixtures.CODE_LIKE).lineCount > 1)
        assertTrue(bounds(due).right <= bounds(LongFixtures.CODE_LIKE).left)
    }

    /** 130% is the largest font that still lays the rows side by side. */
    @Test
    fun labelsKeepTheirWidthAtTheLargestSideBySideFont() {
        show(unbrokenTicket(), fontScale = SIDE_BY_SIDE_MAX_FONT)
        listOf(createdBy, created, due).forEach(::assertReadable)
        assertClearOf(due, LongFixtures.CODE_LIKE)
        assertClearOf(createdBy, UNBROKEN_CREATOR)
    }

    @Test
    fun shortValuesStaySideBySideOnOneLine() {
        show(shortTicket(), fontScale = 1f)
        val label = bounds(createdBy)
        val value = bounds(SHORT_CREATOR)
        assertEquals(1, layoutOf(SHORT_CREATOR).lineCount)
        assertTrue("the value is not beside its label", label.right <= value.left)
        assertTrue("the value is not on the label's line", value.top < label.bottom && label.top < value.bottom)
    }

    @Test
    fun criticalStaysOnTheCardBesideALongTypeAt200() {
        show(longTicket(), fontScale = LARGE_FONT)
        val card = compose.onNodeWithTag(CARD_TAG).fetchSemanticsNode()
        val cardLeft = card.positionInRoot.x
        val cardRight = cardLeft + card.size.width
        listOf(CRITICAL, LONG_TYPE).forEach { badge ->
            val n = node(badge)
            val left = n.positionInRoot.x
            val right = left + n.size.width
            assertTrue("\"$badge\" ($left..$right) leaves the card ($cardLeft..$cardRight)", left >= cardLeft)
            assertTrue("\"$badge\" ($left..$right) leaves the card ($cardLeft..$cardRight)", right <= cardRight)
        }
        // A badge never wraps (softWrap = false), and for such text didOverflowWidth compares the
        // drawn width with the paragraph laid out at the full row width, so it reads true even
        // when the badge fits. Its one line ending inside the badge is what shows it is whole.
        val critical = layoutOf(CRITICAL)
        assertFalse("CRITICAL is ellipsized", critical.isLineEllipsized(0))
        assertTrue("CRITICAL is squeezed or cut", critical.getLineRight(0) <= critical.size.width)
        // The badges still lead the card, above the title.
        assertTrue("CRITICAL is below the title", bounds(CRITICAL).bottom <= bounds(LONG_TITLE).top)
    }

    private fun longTicket(): TicketNotification = requireNotNull(
        TicketNotifications.from(
            TicketUpdate(
                ticketId = "WAIV-" + LongFixtures.TOKEN_200.take(LONG_ID_LENGTH),
                title = LONG_TITLE,
                url = LongFixtures.URL_400,
                type = LONG_TYPE,
                priority = "critical",
                createdBy = LongFixtures.NAME_60,
                createdAt = "2026-10-30T15:00:00Z",
                dueDate = "2026-11-04T23:00:00Z",
            ),
        ),
    )

    private fun unbrokenTicket(): TicketNotification = requireNotNull(
        TicketNotifications.from(
            TicketUpdate(
                ticketId = LongFixtures.TOKEN_200.take(UNBROKEN_ID_LENGTH),
                title = LongFixtures.TOKEN_200,
                url = "https://app.example.test/t/1",
                createdBy = UNBROKEN_CREATOR,
                dueDateLabel = LongFixtures.CODE_LIKE,
            ),
        ),
    )

    private fun shortTicket(): TicketNotification = requireNotNull(
        TicketNotifications.from(
            TicketUpdate(
                ticketId = "WAIV-2527",
                title = "Replace the shower grab rail",
                url = "https://app.example.test/t/2527",
                type = "Task",
                priority = "high",
                createdBy = SHORT_CREATOR,
                createdAt = "2026-10-30T15:00:00Z",
                dueDate = "2026-11-04T23:00:00Z",
            ),
        ),
    )

    private companion object {
        const val CARD_TAG = "ticket_card"
        const val LARGE_FONT = 2f
        const val SIDE_BY_SIDE_MAX_FONT = 1.3f
        const val CRITICAL = "CRITICAL"
        const val LONG_TYPE = "Incident follow-up and family communication"
        const val LONG_ID_LENGTH = 40
        const val UNBROKEN_ID_LENGTH = 60
        const val SHORT_CREATOR = "Priya Raman"
        val LONG_TITLE = LongFixtures.PARAGRAPHS_3000.take(400)
        val UNBROKEN_CREATOR = LongFixtures.TOKEN_200.take(80)
    }
}
