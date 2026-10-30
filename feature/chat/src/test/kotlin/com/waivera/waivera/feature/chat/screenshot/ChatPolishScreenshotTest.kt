package com.waivera.waivera.feature.chat.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.chat.message.ChatDimens
import com.waivera.waivera.feature.chat.message.ChatImageState
import com.waivera.waivera.feature.chat.message.ChatPolishFixtures
import com.waivera.waivera.feature.chat.message.ImageDismissDrag
import com.waivera.waivera.feature.chat.message.ImagePreviewContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The owner's device review of 2026-10-30, across the shared matrix (light and dark, small and
 * large phone, 100% and 200% font): reactions and replies under the bubble, a run with and
 * without a revealed time, and the image preview partway through a swipe to close.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class ChatPolishScreenshotTest {
    /** An incoming and a mine message, each with my 👍, two ❤️ and "2 replies", side by side under the bubble. */
    @Test
    fun reactionsAndReplies() = captureScreenshots("chat_engagement") {
        WithDemoChatAvatars { ChatPolishFixtures.Rows(ChatPolishFixtures.engagementRows()) }
    }

    /** Ten reactions wrap onto more lines inside the bubble column, with "2 replies" last. */
    @Test
    fun manyReactionsWrap() = captureScreenshots("chat_engagement_wrapping") {
        WithDemoChatAvatars {
            ChatPolishFixtures.Rows(ChatPolishFixtures.engagementRows(ChatPolishFixtures.manyReactions))
        }
    }

    /** Priya's run of three and my run of two, held together by the run gap: only each run's last bubble has a time. */
    @Test
    fun runWithTimesHidden() = captureScreenshots("chat_run_times_hidden") {
        WithDemoChatAvatars { ChatPolishFixtures.Rows(ChatPolishFixtures.runs()) }
    }

    /** The same runs with the middle bubble's time tapped open: only that line takes room. */
    @Test
    fun runWithATimeRevealed() = captureScreenshots("chat_run_time_revealed") {
        WithDemoChatAvatars { ChatPolishFixtures.Rows(ChatPolishFixtures.runs(), revealedKeys = setOf(REVEALED_KEY)) }
    }

    /**
     * The image preview held partway down a swipe to close: the image moved with the finger and
     * the backdrop half faded. Captured with animations on, since reduced motion keeps the image still.
     */
    @Test
    fun imagePreviewMidDrag() = captureScreenshots("chat_image_preview_dragging", animations = true) {
        val offset = with(LocalDensity.current) { ChatDimens.ImageDismissFadeDistance.toPx() / 2 }
        ImagePreviewContent(
            image = ChatImageState.Ready(photo()),
            description = "site-photo.jpg",
            onClose = {},
            drag = remember { ImageDismissDrag(initialOffset = offset) },
        )
    }

    /** A stand-in photo: a diagonal wash, so the image's edges show against the fading backdrop. Test data only. */
    private fun photo(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(PHOTO_WIDTH, PHOTO_HEIGHT, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                PHOTO_WIDTH.toFloat(),
                PHOTO_HEIGHT.toFloat(),
                PHOTO_START,
                PHOTO_END,
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, PHOTO_WIDTH.toFloat(), PHOTO_HEIGHT.toFloat(), paint)
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val REVEALED_KEY = "m-12"
        const val PHOTO_WIDTH = 800
        const val PHOTO_HEIGHT = 600
        const val PHOTO_START = 0xFF7E57C2.toInt()
        const val PHOTO_END = 0xFFF2C14E.toInt()
    }
}
