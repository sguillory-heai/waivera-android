package com.waivera.waivera.feature.tasks.files

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.FileKind
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.network.di.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.zwobble.mammoth.DocumentConverter

/** What the viewer shows once the file is on the phone. */
@Immutable
sealed interface FileContent {
    data class Text(val text: String, val cut: Boolean) : FileContent

    data class Image(val bitmap: ImageBitmap) : FileContent

    /** [aspects] is each page's height over its width, so pages hold their space before they draw. */
    data class Pdf(val aspects: List<Float>) : FileContent

    /** The document as simple HTML, the same conversion the web preview uses. */
    data class Word(val html: String) : FileContent
}

sealed interface FilePhase {
    data object Loading : FilePhase

    /** Couldn't download or read it; the web can still open it. */
    data object Failed : FilePhase

    data class Ready(val content: FileContent) : FilePhase
}

@Immutable
data class TaskFileUiState(val name: String = "", val kind: FileKind? = null, val phase: FilePhase = FilePhase.Loading)

/**
 * A task file, read-only: downloads it into the private cache, turns it into something to show,
 * and deletes it when the viewer closes. PDF pages render on demand at the width they are shown.
 */
@HiltViewModel(assistedFactory = TaskFileViewModel.Factory::class)
class TaskFileViewModel @AssistedInject constructor(
    @Assisted("task") private val taskId: Int,
    @Assisted("attachment") private val attachmentId: Int,
    private val tasks: TaskRepository,
    private val files: TaskFileRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow(TaskFileUiState())
    val uiState: StateFlow<TaskFileUiState> = state.asStateFlow()

    private var file: File? = null
    private var pdf: PdfRenderer? = null
    private val pdfLock = Mutex()
    private var loading: Job? = null

    @Volatile
    private var closed = false

    init {
        load()
    }

    fun load() {
        if (loading?.isActive == true) return
        state.update { it.copy(phase = FilePhase.Loading) }
        release()
        loading = viewModelScope.launch {
            val attachment = tasks.attachments(taskId).getOrNull()?.firstOrNull { it.attachmentId == attachmentId }
            if (attachment == null) {
                state.update { it.copy(phase = FilePhase.Failed) }
                return@launch
            }
            state.update { it.copy(name = attachment.name, kind = attachment.kind) }
            val phase = files.open(taskId, attachment).fold(
                onSuccess = { downloaded ->
                    file = downloaded
                    runCatching { withContext(io) { read(attachment, downloaded) } }
                        .fold(onSuccess = { FilePhase.Ready(it) }, onFailure = {
                            release()
                            FilePhase.Failed
                        })
                },
                onFailure = { FilePhase.Failed },
            )
            state.update { it.copy(phase = phase) }
        }
    }

    /** One PDF page drawn [widthPx] wide on white, or null if the document has closed. */
    suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap? = withContext(io) {
        pdfLock.withLock {
            val renderer = pdf ?: return@withLock null
            val drawn = renderer.openPage(index).use { page ->
                val width = widthPx.coerceIn(1, MAX_PAGE_PX)
                val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                val bitmap = createBitmap(width, height)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
            if (closed) closePdf()
            drawn
        }
    }

    private suspend fun read(attachment: TaskAttachment, source: File): FileContent = when (attachment.kind) {
        FileKind.Text -> readText(source)
        FileKind.Image -> FileContent.Image(decodeImage(source))
        FileKind.Pdf -> pdfLock.withLock {
            val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            // The renderer owns the descriptor once made; if making it fails, close it here.
            val renderer = runCatching { PdfRenderer(descriptor) }.onFailure { descriptor.close() }.getOrThrow()
            pdf = renderer
            val aspects = (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { it.height.toFloat() / it.width.coerceAtLeast(1) }
            }
            // The viewer may have closed while the pages were read; nothing would close it later.
            if (closed) closePdf()
            FileContent.Pdf(aspects)
        }
        FileKind.Word -> {
            // Embedded pictures would arrive as huge data URLs the text view can't show anyway.
            val html = DocumentConverter().convertToHtml(source).value
                .replace(IMAGE_TAG, "")
                // Links in a document open nothing here: the viewer is read-only and the link's
                // target is unchecked, so its text stays and the link goes.
                .replace(LINK_TAG, "")
                // The text view adds no space above headings, so a blank line sets each section apart.
                .replace(LATER_HEADING, "<br><h$1>")
            FileContent.Word(html)
        }
        FileKind.Other -> error("Not viewable")
    }

    /** The first [MAX_TEXT_BYTES], cut back to a whole character, without reading the rest. */
    private fun readText(source: File): FileContent.Text {
        val bytes = source.inputStream().use { input ->
            val buffer = ByteArray(MAX_TEXT_BYTES + 1)
            var read = 0
            while (read < buffer.size) {
                val count = input.read(buffer, read, buffer.size - read)
                if (count < 0) break
                read += count
            }
            buffer.copyOf(read)
        }
        val cut = bytes.size > MAX_TEXT_BYTES
        var end = minOf(bytes.size, MAX_TEXT_BYTES)
        // Back off over UTF-8 continuation bytes so the last character isn't split.
        if (cut) while (end > 0 && (bytes[end].toInt() and UTF8_TAIL_MASK) == UTF8_TAIL) end--
        return FileContent.Text(String(bytes, 0, end, Charsets.UTF_8), cut)
    }

    /** Decoded at most [MAX_IMAGE_PX] on its long side, so a phone photo never runs memory out. */
    private fun decodeImage(source: File): ImageBitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_PX) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Not an image")
        return bitmap.asImageBitmap()
    }

    override fun onCleared() {
        closed = true
        // A page mid-render holds the lock and closes the renderer itself when it finishes.
        if (pdfLock.tryLock()) {
            try {
                closePdf()
            } finally {
                pdfLock.unlock()
            }
        }
        // Deleting the file is safe while the renderer still has it open; the space frees on close.
        file?.let(files::close)
        file = null
    }

    /** Before a retry: the last attempt's renderer and copy go, so retries never pile up. */
    private fun release() {
        if (pdfLock.tryLock()) {
            try {
                closePdf()
            } finally {
                pdfLock.unlock()
            }
        }
        file?.let(files::close)
        file = null
    }

    private fun closePdf() {
        runCatching { pdf?.close() }
        pdf = null
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("task") taskId: Int, @Assisted("attachment") attachmentId: Int): TaskFileViewModel
    }

    private companion object {
        const val MAX_TEXT_BYTES = 512 * 1024
        const val MAX_IMAGE_PX = 2048
        const val MAX_PAGE_PX = 2400
        val IMAGE_TAG = Regex("<img[^>]*>")
        val LINK_TAG = Regex("</?a(\\s[^>]*)?>")
        const val UTF8_TAIL_MASK = 0xC0
        const val UTF8_TAIL = 0x80
        val LATER_HEADING = Regex("(?<!^)<h([1-6])>")
    }
}
