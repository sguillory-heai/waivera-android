package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
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
 * Tapping Retry on a failed send (M3-01). The leaving "Not sent" side keeps its own text and
 * actions while it fades, so the footer never re-renders the old side as a full-strength
 * "Sending…" that then fades and comes back. Measured on drawn pixels, with animations on and off.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE], qualifiers = "w360dp-h640dp-notnight-xhdpi")
class RetryFooterMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private var colors: WaiveraColors? = null

    private val inks: Inks get() = Inks(requireNotNull(colors))

    private fun row(state: SendState) = TimelineItem.MessageRow(
        key = CLIENT_ID,
        message = Message(
            id = -1,
            conversationId = DemoData.DM_PRIYA,
            clientMessageId = CLIENT_ID,
            authorId = DemoData.ME,
            text = "On my way",
            createdAt = DemoData.NOW,
            sendState = state,
        ),
        isMine = true,
        isBot = false,
        groupedWithPrevious = false,
        groupedWithNext = false,
        rich = null,
        ticket = null,
        reactions = emptyList(),
        isLiveArrival = true,
    )

    private fun showFailed(motion: WaiveraMotionScheme) {
        compose.setContent {
            WaiveraTheme(darkTheme = false, motion = motion, hapticsEnabled = false) {
                colors = WaiveraTheme.colors
                var state by remember { mutableStateOf(SendState.FAILED) }
                val callbacks = remember {
                    MessageRowCallbacks(
                        onToggleTimestamp = {},
                        onLongPress = null,
                        onReact = null,
                        onReactionDetails = null,
                        onOpenThread = null,
                        onOpenAttachment = {},
                        onOpenPerson = {},
                        onLink = {},
                        onRetry = {
                            state = SendState.SENDING
                            Snapshot.sendApplyNotifications()
                        },
                        onEditFailed = {},
                        onDiscard = {},
                    )
                }
                Box(Modifier.fillMaxWidth().background(WaiveraTheme.colors.background)) {
                    MessageRowView(
                        row = row(state),
                        authorName = "Me",
                        time = RowTime(DemoData.NOW, DemoData.ZONE),
                        timestampVisible = false,
                        highlighted = false,
                        callbacks = callbacks,
                    )
                }
            }
        }
        compose.waitForIdle()
        // The failure came well after the send, so the "Sending…" wait is long over.
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    /** What a person or TalkBack can find: the merged tree, as in [MessageFooterMotionTest]. */
    private fun count(text: String): Int = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size

    private fun boundsOf(text: String): Rect? =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().singleOrNull()?.boundsInRoot

    private fun capture(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private fun aRetryNeverShowsAStaleOrFullStrengthSendingFrame(motion: WaiveraMotionScheme) {
        compose.mainClock.autoAdvance = false
        showFailed(motion)
        val retryAtRest = inks.retry(capture(), requireNotNull(boundsOf(RETRY)))
        compose.onNodeWithText(RETRY).performSemanticsAction(SemanticsActions.OnClick)
        val sending = mutableListOf<Float>()
        val retry = mutableListOf<Float>()
        repeat(WATCHED_FRAMES) { frame ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            assertTrue("frame $frame shows \"Sending…\" twice: the leaving side went stale", count(SENDING) <= 1)
            val retryBounds = boundsOf(RETRY)
            if (retryBounds != null) {
                assertEquals("frame $frame: the leaving side lost \"Not sent\" but kept Retry", 1, count(NOT_SENT))
            }
            val pixels = capture()
            sending += boundsOf(SENDING)?.let { inks.sending(pixels, it) } ?: 0f
            retry += retryBounds?.let { inks.retry(pixels, it) / retryAtRest } ?: 0f
        }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val sendingAtRest = inks.sending(capture(), requireNotNull(boundsOf(SENDING)))
        val shown = sending.map { it / sendingAtRest }
        assertTrue(
            "\"Sending…\" showed at full strength at once: ${shown.first()}",
            shown.first() < FIRST_FRAME_MAX_SHARE,
        )
        var peak = 0f
        shown.forEachIndexed { frame, value ->
            assertTrue(
                "\"Sending…\" faded back at frame $frame: $value after $peak (${shown.joinToString()})",
                value >= peak - FADE_TOLERANCE,
            )
            peak = maxOf(peak, value)
            assertTrue(
                "frame $frame: Retry at ${retry[frame]} while \"Sending…\" is at $value",
                value <= SENDING_SHOWING || retry[frame] < RETRY_FULL,
            )
        }
    }

    @Test
    fun aRetryNeverShowsAStaleOrFullStrengthSendingFrame() =
        aRetryNeverShowsAStaleOrFullStrengthSendingFrame(WaiveraMotionScheme.Standard)

    @Test
    fun withAnimationsOffARetryNeverShowsAStaleOrFullStrengthSendingFrame() =
        aRetryNeverShowsAStaleOrFullStrengthSendingFrame(WaiveraMotionScheme.Reduced)

    private companion object {
        const val CLIENT_ID = "c-1"
        const val FRAME_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val WATCHED_FRAMES = 12
        const val SENDING = "Sending…"
        const val NOT_SENT = "Not sent"
        const val RETRY = "Retry"

        /** On the tap's frame the arriving label is still in its fade-through wait (or 16 ms into the reduced fade). */
        const val FIRST_FRAME_MAX_SHARE = 0.5f

        /** Glyph coverage moves a little under the swap's small pop; a fade back is far larger. */
        const val FADE_TOLERANCE = 0.15f
        const val SENDING_SHOWING = 0.1f
        const val RETRY_FULL = 0.9f
    }
}

/** Reads how strongly the footer's parts are drawn, from the theme's own colours. */
private class Inks(colors: WaiveraColors) {
    private val background = colors.background
    private val secondary = colors.textSecondary
    private val danger = colors.dangerText

    /**
     * How strongly the grey "Sending…" is drawn in [bounds]: the strongest pixel's share of the
     * grey, separated from the red "Not sent" that fades out in the same place. Each pixel is the
     * background plus some grey and some red, so its red and blue drops give both amounts.
     */
    fun sending(pixels: PixelMap, bounds: Rect): Float {
        val greyRed = background.red - secondary.red
        val greyBlue = background.blue - secondary.blue
        val dangerRed = background.red - danger.red
        val dangerBlue = background.blue - danger.blue
        val determinant = greyRed * dangerBlue - dangerRed * greyBlue
        return maxOver(pixels, bounds) { color ->
            val dropRed = background.red - color.red
            val dropBlue = background.blue - color.blue
            (dropRed * dangerBlue - dropBlue * dangerRed) / determinant
        }
    }

    /** How strongly the violet Retry button is drawn in [bounds]: its strongest green drop. */
    fun retry(pixels: PixelMap, bounds: Rect): Float =
        maxOver(pixels, bounds) { color -> background.green - color.green }
}

private fun maxOver(pixels: PixelMap, bounds: Rect, value: (Color) -> Float): Float {
    var best = 0f
    val left = bounds.left.toInt().coerceIn(0, pixels.width - 1)
    val right = bounds.right.toInt().coerceIn(0, pixels.width)
    val top = bounds.top.toInt().coerceIn(0, pixels.height - 1)
    val bottom = bounds.bottom.toInt().coerceIn(0, pixels.height)
    for (y in top until bottom) {
        for (x in left until right) best = maxOf(best, value(pixels[x, y]))
    }
    return best
}
