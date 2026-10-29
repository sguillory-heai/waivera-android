package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import kotlin.test.assertNotNull
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
 * A long message in the focus overlay (v7 item 2, the owner's a + b + c): the clamped bubble shows
 * a bottom fade and scrolls inside itself; at its end the bottom fade goes and a top fade shows;
 * a short message has neither fade nor scroll. Scrolling never closes the overlay, the menu stays
 * where it was, and TalkBack still has the whole text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class MessageFocusScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private var sheet by mutableStateOf<ChatSheet?>(null)
    private val anchors = MessageFocusAnchors()
    private val events = mutableListOf<String>()

    private val callbacks = ChatSheetCallbacks(
        onDismiss = {
            events += "dismiss"
            sheet = null
        },
        onReact = { _, _ -> },
        onCopy = {},
        onReply = {},
        onEdit = {},
        onBookmark = {},
        onRequestDelete = {},
        onConfirmDelete = {},
        onSave = {},
        onShare = {},
        onLink = {},
    )

    private val metrics get() = RuntimeEnvironment.getApplication().resources.displayMetrics
    private val windowWidth get() = metrics.widthPixels.toFloat()
    private val windowHeight get() = metrics.heightPixels.toFloat()
    private val dp get() = metrics.density

    private fun open(message: Message, anchor: Rect) {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = WaiveraMotionScheme.Reduced, hapticsEnabled = false) {
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
        anchors.record(message.id, anchor)
        sheet = ChatSheet.Actions(ChatActions.model(message, DemoData.ME, canWrite = true, allowReply = true))
        compose.waitForIdle()
    }

    private fun tallScrolled(): Rect = Rect(
        left = windowWidth * 0.18f - 16 * dp,
        top = -windowHeight,
        right = windowWidth - 16 * dp,
        bottom = windowHeight - 90 * dp,
    )

    private fun nearComposer(): Rect = Rect(
        left = windowWidth * 0.3f,
        top = windowHeight - 140 * dp,
        right = windowWidth - 16 * dp,
        bottom = windowHeight - 100 * dp,
    )

    private fun fades(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true)

    private fun scrollRange() = compose.onNodeWithTag(FOCUS_BUBBLE_SCROLL_TAG, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)

    private fun menuTop(): Float =
        compose.onNode(hasText("Reply in thread") and hasClickAction()).fetchSemanticsNode().boundsInWindow.top

    @Test
    fun aClampedBubbleShowsTheBottomFadeAndScrolls() {
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.PARAGRAPHS_3000), tallScrolled())
        fades(FOCUS_FADE_BOTTOM_TAG).assertCountEquals(1)
        fades(FOCUS_FADE_TOP_TAG).assertCountEquals(0)
        val range = assertNotNull(scrollRange())
        assertTrue("the clamped bubble cannot scroll", range.maxValue() > 0f)
        compose.onNodeWithTag(FOCUS_BUBBLE_SCROLL_TAG, useUnmergedTree = true).assert(hasScrollAction())
    }

    @Test
    fun atTheEndTheBottomFadeGoesAndTheTopFadeShows() {
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.PARAGRAPHS_3000), tallScrolled())
        compose.onNodeWithTag(FOCUS_BUBBLE_SCROLL_TAG, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, windowHeight * SCROLL_PAST) }
        compose.waitForIdle()
        val range = assertNotNull(scrollRange())
        assertEquals("not at the end", range.maxValue(), range.value(), 1f)
        fades(FOCUS_FADE_BOTTOM_TAG).assertCountEquals(0)
        fades(FOCUS_FADE_TOP_TAG).assertCountEquals(1)
    }

    @Test
    fun scrollingTheBubbleNeverClosesTheOverlayOrMovesTheMenu() {
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.PARAGRAPHS_3000), tallScrolled())
        val before = menuTop()
        val bubble = compose.onNodeWithTag(FOCUS_BUBBLE_SCROLL_TAG, useUnmergedTree = true)
        bubble.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("the swipe did not scroll the bubble", assertNotNull(scrollRange()).value() > 0f)
        bubble.performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertNotNull(sheet, "scrolling closed the overlay")
        assertFalse("scrolling reached the scrim", "dismiss" in events)
        assertEquals("the menu moved", before, menuTop(), 0.5f)
    }

    @Test
    fun aShortMessageHasNoFadeAndNoScroll() {
        open(FocusOverlayFixtures.mine(FocusOverlayFixtures.SHORT), nearComposer())
        fades(FOCUS_FADE_BOTTOM_TAG).assertCountEquals(0)
        fades(FOCUS_FADE_TOP_TAG).assertCountEquals(0)
        assertEquals(0f, scrollRange()?.maxValue() ?: 0f, 0f)
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }

    @Test
    fun talkBackHasTheWholeClampedMessage() {
        val text = FocusOverlayFixtures.PARAGRAPHS_3000
        open(FocusOverlayFixtures.mine(text), tallScrolled())
        val node = compose.onNode(hasText(text.take(PREFIX), substring = true)).fetchSemanticsNode()
        val shown = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        assertTrue("TalkBack does not get the message's end", shown.endsWith(text.takeLast(PREFIX)))
    }

    private companion object {
        const val PREFIX = 24
        const val SCROLL_PAST = 20f
    }
}
