package com.waivera.waivera.feature.tasks.files

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The read-only file viewer for each kind, plus loading and failure. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class TaskFileScreenshotTest {
    @Test
    fun pdf() = shot("task_file_pdf", "Care plan 2026.pdf", FileKind.Pdf, FileContent.Pdf(listOf(PAGE, PAGE)))

    @Test
    fun image() = shot("task_file_image", "Home photo.png", FileKind.Image, FileContent.Image(photo()))

    @Test
    fun text() = shot(
        "task_file_text",
        "Visit notes.txt",
        FileKind.Text,
        FileContent.Text(
            "Oct 21 visit\n\nJordan was in good spirits. Mom asked to move Thursday's visit to the " +
                "afternoon because of a school pickup.\n\nFollow-ups:\n- Confirm 2:30 PM with Mom\n" +
                "- Ask about the wheelchair fitting\n- Upload the signed plan",
            cut = false,
        ),
    )

    @Test
    fun word() = shot(
        "task_file_word",
        "Intake packet.docx",
        FileKind.Word,
        FileContent.Word(
            "<h1>Intake packet</h1><p>Participant: <strong>Jordan Lee</strong></p>" +
                "<br><h2>Goals</h2><ul><li>Ride the bus to the clinic on his own</li>" +
                "<li>Cook two meals a week</li></ul><p>Reviewed with the family on Oct 12.</p>",
        ),
    )

    @Test
    fun loading() = captureScreenshots("task_file_loading") {
        TaskFileContent(
            TaskFileUiState("Care plan 2026.pdf", FileKind.Pdf, FilePhase.Loading),
            onBack = {},
            onRetry = {},
            renderPage = { _, _ -> null },
        )
    }

    @Test
    fun failed() = captureScreenshots("task_file_failed") {
        TaskFileContent(
            TaskFileUiState("Care plan 2026.pdf", FileKind.Pdf, FilePhase.Failed),
            onBack = {},
            onRetry = {},
            renderPage = { _, _ -> null },
        )
    }

    private fun shot(name: String, file: String, kind: FileKind, content: FileContent) = captureScreenshots(name) {
        TaskFileContent(
            TaskFileUiState(file, kind, FilePhase.Ready(content)),
            onBack = {},
            onRetry = {},
            renderPage = { _, width -> page(width) },
        )
    }

    /** A page with a title bar and lines of "text", drawn the way PdfRenderer hands one back. */
    private fun page(width: Int): ImageBitmap {
        val height = (width * PAGE).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val ink = Paint().apply { color = android.graphics.Color.rgb(60, 60, 70) }
        val margin = width / 10f
        canvas.drawRect(margin, margin, width * 0.6f, margin + width / 24f, ink)
        ink.color = android.graphics.Color.rgb(170, 170, 180)
        var y = margin * 2
        while (y < height - margin) {
            canvas.drawRect(margin, y, width - margin, y + width / 60f, ink)
            y += width / 22f
        }
        return bitmap.asImageBitmap()
    }

    private fun photo(): ImageBitmap {
        val bitmap = Bitmap.createBitmap(PHOTO_W, PHOTO_H, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                PHOTO_W.toFloat(),
                PHOTO_H.toFloat(),
                android.graphics.Color.rgb(126, 87, 194),
                android.graphics.Color.rgb(38, 166, 154),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, PHOTO_W.toFloat(), PHOTO_H.toFloat(), paint)
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val PAGE = 1.294f
        const val PHOTO_W = 800
        const val PHOTO_H = 600
    }
}
