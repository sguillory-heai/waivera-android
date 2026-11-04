package com.waivera.waivera.core.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.createBitmap
import com.waivera.waivera.core.data.live.TaskFileCacheDirectory
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.network.error.WaiveraError
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Draws the demo task files on the phone, so the viewer works with no server. */
@Singleton
class DemoTaskFileRepository @Inject constructor(@TaskFileCacheDirectory private val directory: File) :
    TaskFileRepository {
    override suspend fun open(taskId: Int, file: TaskAttachment): Result<File> = runCatching {
        directory.mkdirs()
        val target = File(directory, UUID.randomUUID().toString())
        when (file.kind) {
            FileKind.Text -> target.writeText(DemoData.TASK_NOTE_TEXT)
            FileKind.Pdf -> writePdf(target)
            FileKind.Image -> writeImage(target)
            FileKind.Word, FileKind.Other -> throw WaiveraError.Unexpected()
        }
        target
    }

    override fun close(file: File) {
        if (file.parentFile == directory) file.delete()
    }

    private fun writePdf(target: File) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = TEXT_SIZE }
        repeat(PAGES) { index ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
            page.canvas.drawText("Care plan, page ${index + 1}", MARGIN, MARGIN * TITLE_LINE, paint)
            page.canvas.drawText("Goals reviewed with the family.", MARGIN, MARGIN * BODY_LINE, paint)
            document.finishPage(page)
        }
        target.outputStream().use(document::writeTo)
        document.close()
    }

    private fun writeImage(target: File) {
        val bitmap = createBitmap(IMAGE_SIDE, IMAGE_SIDE)
        Canvas(bitmap).apply {
            drawColor(BACKGROUND)
            drawCircle(IMAGE_SIDE / 2f, IMAGE_SIDE / 2f, IMAGE_SIDE / CIRCLE_SHARE, Paint().apply { color = INK })
        }
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }
        bitmap.recycle()
    }

    private companion object {
        const val PAGES = 2
        const val TITLE_LINE = 2
        const val BODY_LINE = 3
        const val CIRCLE_SHARE = 3f
        const val BACKGROUND = 0xFFF1EBFA.toInt()
        const val INK = 0xFF7E57C2.toInt()
        const val PAGE_WIDTH = 612
        const val PAGE_HEIGHT = 792
        const val MARGIN = 48f
        const val TEXT_SIZE = 16f
        const val IMAGE_SIDE = 600
        const val QUALITY = 100
    }
}
