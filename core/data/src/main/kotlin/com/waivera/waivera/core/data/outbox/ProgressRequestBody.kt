package com.waivera.waivera.core.data.outbox

import java.io.File
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/**
 * A file body that reports how much has been written, for the attachment upload bar (03 §8).
 * Progress is reported at most once per percent; a retried write starts again from zero.
 */
internal class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength().coerceAtLeast(1L)
        var written = 0L
        var lastPercent = -1L
        file.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, SEGMENT_BYTES)
                if (read == -1L) break
                written += read
                sink.emitCompleteSegments()
                val percent = written * PERCENT / total
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(written.toFloat() / total)
                }
            }
        }
        sink.flush()
    }

    private companion object {
        const val SEGMENT_BYTES = 8_192L
        const val PERCENT = 100L
    }
}
