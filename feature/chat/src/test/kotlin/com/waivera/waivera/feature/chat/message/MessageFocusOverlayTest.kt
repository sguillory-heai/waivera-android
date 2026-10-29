package com.waivera.waivera.feature.chat.message

import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The long-press focus overlay (owner's pick, option B) in its real dialog window, on a 360x640dp
 * phone upright and on its side, at 100% and 200% font. With the wave 28 fixtures (3,000
 * characters, a 400-character link, 60 lines) every reaction and every action is laid out, inside
 * the window, at least 48dp and clickable: the old sheet cut Bookmark and Delete off (B1) and let
 * its emoji row overflow its gutter (B2). Also: how it closes, what TalkBack gets, and the
 * per-message flags and states the old sheet's tests covered.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class MessageFocusOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private var sheet by mutableStateOf<ChatSheet?>(null)
    private val anchors = MessageFocusAnchors()
    private val events = mutableListOf<String>()

    /** Whether an action closes the overlay, as the view models do (their tests cover that side). */
    private var actionsClose = false

    private val callbacks = ChatSheetCallbacks(
        onDismiss = {
            events += "dismiss"
            sheet = null
        },
        onReact = { _, emoji -> act("react $emoji") },
        onCopy = { events += "copy" },
        onReply = { act("reply") },
        onEdit = { act("edit") },
        onBookmark = { act("bookmark") },
        onRequestDelete = { act("delete") },
        onConfirmDelete = {},
        onSave = {},
        onShare = {},
        onLink = {},
    )

    private fun act(name: String) {
        events += name
        if (actionsClose) sheet = null
    }

    private val metrics get() = RuntimeEnvironment.getApplication().resources.displayMetrics
    private val windowWidth get() = metrics.widthPixels.toFloat()
    private val windowHeight get() = metrics.heightPixels.toFloat()
    private val dp get() = metrics.density

    private fun show(motion: WaiveraMotionScheme = WaiveraMotionScheme.Reduced) {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                Box(Modifier.fillMaxSize().background(WaiveraTheme.colors.background))
                ChatSheetHost(
                    sheet = sheet,
                    messageFor = { null },
                    myUserId = DemoData.ME,
                    nameFor = { it },
                    callbacks = callbacks,
                    anchors = anchors,
                )
            }
        }
    }

    private fun open(message: Message, anchor: Rect?, canWrite: Boolean = true, copied: Boolean = false) {
        sheet = null
        compose.waitForIdle()
        anchors.record(message.id, anchor)
        val model = ChatActions.model(message, DemoData.ME, canWrite = canWrite, allowReply = true)
        sheet = ChatSheet.Actions(model.copy(copied = copied))
        compose.waitForIdle()
    }

    /** A short bubble of mine pressed just above the composer, where the old sheet hid it. */
    private fun nearComposer(): Rect = Rect(
        left = windowWidth * 0.3f,
        top = windowHeight - 140 * dp,
        right = windowWidth - 16 * dp,
        bottom = windowHeight - 100 * dp,
    )

    /** A long bubble of mine pressed with its start scrolled far above the screen. */
    private fun tallScrolled(): Rect = Rect(
        left = windowWidth * 0.18f - 16 * dp,
        top = -windowHeight,
        right = windowWidth - 16 * dp,
        bottom = windowHeight - 90 * dp,
    )

    private val longFixtures = listOf(
        "3,000 characters" to FocusOverlayFixtures.PARAGRAPHS_3000,
        "400-character link" to FocusOverlayFixtures.URL_400,
        "60 lines" to FocusOverlayFixtures.LINES_60,
    )

    private val actionLabels = listOf("Reply in thread", "Copy text", "Edit message", "Bookmark", "Delete message")

    private fun everyActionIsReachable(where: String) {
        show()
        val cases = longFixtures.flatMap { (name, text) ->
            listOf("$name, tall and scrolled" to tallScrolled(), "$name, near the composer" to nearComposer())
                .map { (place, anchor) -> Triple(place, text, anchor) }
        } + Triple("short, near the composer", FocusOverlayFixtures.SHORT, nearComposer())
        cases.forEachIndexed { index, (name, text, anchor) ->
            open(FocusOverlayFixtures.mine(text, id = 80_500L + index), anchor)
            val label = "$name, $where"
            val reactions = Reactions.QUICK.map { emoji -> reachable(hasContentDescription(emoji), "$emoji ($label)") }
            val actions = actionLabels.map { action -> reachable(hasText(action), "$action ($label)") }
            reactions.forEach { r ->
                actions.forEach { a -> assertFalse("a reaction overlaps an action ($label)", r.overlaps(a)) }
            }
            assertPillIsEven(reactions, label)
            compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
            assertBubbleOpensAtItsStart(text, actions + reactions, label)
            events.clear()
            Reactions.QUICK.forEach { compose.onNode(hasContentDescription(it) and hasClickAction()).performClick() }
            actionLabels.forEach { compose.onNode(hasText(it) and hasClickAction()).performClick() }
            assertEquals(
                "every target answers a tap ($label)",
                Reactions.QUICK.map { "react $it" } + listOf("reply", "copy", "edit", "bookmark", "delete"),
                events,
            )
        }
    }

    /** [matcher]'s node is laid out, wholly inside the window, at least 48dp each way and clickable. */
    private fun reachable(matcher: SemanticsMatcher, label: String): Rect {
        val node = compose.onNode(matcher and hasClickAction())
        node.assertHasClickAction()
        val bounds = node.fetchSemanticsNode().boundsInWindow
        assertTrue("$label is not laid out: $bounds", bounds.width > 0f && bounds.height > 0f)
        assertTrue(
            "$label is outside the ${windowWidth}x$windowHeight window: $bounds",
            bounds.left >= 0f && bounds.top >= 0f && bounds.right <= windowWidth && bounds.bottom <= windowHeight,
        )
        val target = WaiveraSpacing.MinTouchTarget.value * dp - 1f
        assertTrue("$label is under 48dp: $bounds", bounds.width >= target && bounds.height >= target)
        return bounds
    }

    /** B2: the six reactions are evenly spaced in a row, and the row keeps the 16dp gutter. */
    private fun assertPillIsEven(reactions: List<Rect>, label: String) {
        val gaps = reactions.zipWithNext { a, b -> b.left - a.right }
        gaps.forEach { assertEquals("the reactions are unevenly spaced ($label): $gaps", gaps.first(), it, 1f) }
        reactions.forEach {
            assertEquals("the reactions are not in one row ($label)", reactions.first().top, it.top, 1f)
        }
        val gutter = WaiveraSpacing.Gutter.value * dp - 1f
        assertTrue("the pill crosses the start gutter ($label)", reactions.first().left >= gutter)
        assertTrue("the pill crosses the end gutter ($label)", windowWidth - reactions.last().right >= gutter)
    }

    /** The lifted bubble shows the start of the message, on screen, and never sits under a control. */
    private fun assertBubbleOpensAtItsStart(text: String, controls: List<Rect>, label: String) {
        val bubble = compose.onNode(hasText(text.take(24), substring = true)).fetchSemanticsNode().boundsInWindow
        assertTrue("the bubble's start is off screen ($label): $bubble", bubble.top >= 0f && bubble.top < windowHeight)
        controls.forEach { control ->
            assertFalse(
                "a control covers the bubble's first line ($label)",
                control.contains(
                    Offset(
                        bubble.center.x,
                        bubble.top + 4 * dp,
                    ),
                ),
            )
        }
    }

    @Test
    fun everyActionIsReachableOnASmallPhone() = everyActionIsReachable("360x640 at 100%")

    @Test
    @Config(fontScale = 2f)
    fun everyActionIsReachableOnASmallPhoneAt200Percent() = everyActionIsReachable("360x640 at 200%")

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-notnight-xhdpi")
    fun everyActionIsReachableInLandscape() = everyActionIsReachable("640x360 at 100%")

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-notnight-xhdpi", fontScale = 2f)
    fun everyActionIsReachableInLandscapeAt200Percent() = everyActionIsReachable("640x360 at 200%")

    @Test
    fun aScrimTapCloses() {
        show()
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT), nearComposer())
        // The top-start corner is scrim: the bubble, pill and menu sit at the bottom end.
        compose.onNode(hasContentDescription(CLOSE) and hasClickAction()).performTouchInput { click(Offset(4f, 4f)) }
        compose.waitForIdle()
        assertNull(sheet)
        assertEquals(listOf("dismiss"), events)
        compose.onAllNodes(hasText("Copy text")).assertCountEquals(0)
    }

    @Test
    fun backCloses() {
        show()
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.PARAGRAPHS_3000), tallScrolled())
        val dialog = ShadowDialog.getLatestDialog()
        assertTrue("the overlay is not its own window", dialog is ComponentDialog && dialog.isShowing)
        (dialog as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertNull(sheet)
        compose.onAllNodes(hasText("Delete message")).assertCountEquals(0)
    }

    @Test
    fun anActionCloses() {
        actionsClose = true
        show()
        listOf(
            "Reply in thread" to "reply",
            "Edit message" to "edit",
            "Bookmark" to "bookmark",
            "Delete message" to "delete",
        )
            .forEach { (label, event) ->
                events.clear()
                open(FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT), nearComposer())
                compose.onNode(hasText(label) and hasClickAction()).performClick()
                compose.waitForIdle()
                assertEquals(listOf(event), events)
                compose.onAllNodes(hasText(label)).assertCountEquals(0)
            }
        events.clear()
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT), nearComposer())
        compose.onNode(hasContentDescription("👍") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf("react 👍"), events)
        assertNull(sheet)
    }

    /** Copy keeps the overlay up long enough to see its check and "Copied", then closes it. */
    @Test
    fun copyShowsItsCheckThenCloses() {
        show()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val message = FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT)
        anchors.record(message.id, nearComposer())
        val model = ChatActions.model(message, DemoData.ME, canWrite = true, allowReply = true)
        sheet = ChatSheet.Actions(model.copy(copied = true))
        compose.mainClock.advanceTimeBy(ChatDimens.FOCUS_COPIED_HOLD_MILLIS / 2)
        compose.onNode(hasText("Copied") and hasClickAction()).assertHasClickAction()
        assertTrue("the overlay closed before \"Copied\" could be seen", events.isEmpty())
        compose.mainClock.advanceTimeBy(ChatDimens.FOCUS_COPIED_HOLD_MILLIS)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertNull(sheet)
        assertEquals(listOf("dismiss"), events)
    }

    @Test
    fun aBookmarkedMessageSaysSo() {
        show()
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT).copy(isBookmarked = true), nearComposer())
        compose.onNode(hasText("Bookmarked · tap to remove") and hasClickAction()).assertHasClickAction()
        compose.onAllNodes(hasText("Bookmark")).assertCountEquals(0)
    }

    /** The same flags as the old sheet: someone else's message has no Edit or Delete. */
    @Test
    fun eachActionFollowsItsFlag() {
        show()
        open(FocusOverlayFixtures.theirs(FocusOverlayFixtures.SHORT), nearComposer())
        listOf("Reply in thread", "Copy text", "Bookmark").forEach {
            compose.onNode(hasText(it) and hasClickAction()).assertHasClickAction()
        }
        compose.onAllNodes(hasText("Edit message")).assertCountEquals(0)
        compose.onAllNodes(hasText("Delete message")).assertCountEquals(0)
        // Read-only: no reactions and no reply, but Copy and Bookmark stay.
        open(FocusOverlayFixtures.theirs(FocusOverlayFixtures.SHORT), nearComposer(), canWrite = false)
        Reactions.QUICK.forEach { compose.onAllNodes(hasContentDescription(it)).assertCountEquals(0) }
        compose.onAllNodes(hasText("Reply in thread")).assertCountEquals(0)
        compose.onNode(hasText("Copy text") and hasClickAction()).assertHasClickAction()
    }

    /** TalkBack hears "Message actions" as the pane opens, starts in the menu, and can close it. */
    @Test
    fun talkBackStartsInTheMenu() {
        show()
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT), nearComposer())
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Message actions")).assertExists()
        val menu = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, -1f) and
                SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true),
        ).fetchSemanticsNode()
        val first = compose.onNode(hasText("Reply in thread") and hasClickAction()).fetchSemanticsNode()
        assertTrue(
            "Reply is not in the first traversal group",
            menu.boundsInWindow.contains(first.boundsInWindow.center),
        )
        compose.onNode(hasContentDescription(CLOSE) and hasClickAction()).assertHasClickAction()
    }

    /** With no anchor (a preview) the bubble still sits on its author's side above the menu. */
    @Test
    fun withoutAnAnchorTheBubbleSitsAboveTheMenu() {
        show()
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT), anchor = null)
        val bubble = compose.onNode(hasText(FocusOverlayFixtures.SHORT)).fetchSemanticsNode().boundsInWindow
        val reply = compose.onNode(hasText("Reply in thread") and hasClickAction()).fetchSemanticsNode().boundsInWindow
        assertTrue("the bubble is not above the menu", bubble.bottom <= reply.top)
        assertTrue("my bubble is not at the end", abs(windowWidth - 16 * dp - bubble.right) < 16 * dp)
    }

    private companion object {
        const val CLOSE = "Close message actions"
    }
}
