package com.waivera.waivera.core.data.uploads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.waivera.waivera.core.data.model.UploadProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/** A picked file, checked and copied into app storage, ready to send. */
data class StagedUpload(val file: File, val fileName: String, val mimeType: String)

/** Reads a picked file, checks it with [UploadChecks] and copies (or re-encodes) it for sending. */
interface UploadStager {
    /** The staged copy, or why it can't be sent. Runs on the caller's dispatcher; call it off the main thread. */
    fun stage(uri: String, directory: File): Result<StagedUpload>
}

/** Thrown inside [UploadStager.stage] results; carries the app's reason. */
class UploadRefused(val problem: UploadProblem, val pickedName: String? = null) : Exception()

class AndroidUploadStager @Inject constructor(@ApplicationContext private val context: Context) : UploadStager {
    override fun stage(uri: String, directory: File): Result<StagedUpload> {
        var pickedName: String? = null
        return runCatching { stageChecked(uri.toUri(), directory) { pickedName = it } }
            .recoverCatching { error ->
                val problem = (error as? UploadRefused)?.problem ?: UploadProblem.Unreadable
                throw UploadRefused(problem, pickedName)
            }
    }

    private fun stageChecked(parsed: Uri, directory: File, onName: (String?) -> Unit): StagedUpload {
        val (name, reportedSize) = describe(parsed)
        onName(name)
        val header = read(parsed) { it.readNBytesCompat(UploadChecks.headerSize) }
        val type = UploadChecks.sniff(header)
        directory.mkdirs()
        val target = File(directory, UUID.randomUUID().toString())
        // Some providers report no size; the copy's length is the truth either way.
        val size = reportedSize ?: copy(parsed, target).also { target.delete() }
        return when (val plan = UploadChecks.plan(type, size)) {
            is UploadChecks.Plan.Refuse -> throw UploadRefused(plan.problem)
            UploadChecks.Plan.Send -> {
                copy(parsed, target)
                val problem = when {
                    target.length() <= 0L -> UploadProblem.Empty
                    target.length() > UploadChecks.MAX_BYTES -> UploadProblem.TooLarge
                    else -> null
                }
                if (problem != null) {
                    target.delete()
                    throw UploadRefused(problem)
                }
                StagedUpload(target, UploadChecks.fileName(name, type, FALLBACK_NAME), type.mimeType)
            }
            UploadChecks.Plan.Reencode -> {
                runCatching { reencode(parsed, target) }.onFailure { target.delete() }.getOrThrow()
                StagedUpload(target, UploadChecks.fileName(name, Sniffed.Jpeg, FALLBACK_NAME), Sniffed.Jpeg.mimeType)
            }
        }
    }

    private fun describe(uri: Uri): Pair<String?, Long?> {
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path))
            return file.name to file.length()
        }
        var name: String? = null
        var size: Long? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                    name =
                        cursor.getString(it)
                }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { size = cursor.getLong(it) }
            }
        }
        return name to size
    }

    private fun <T> read(uri: Uri, block: (InputStream) -> T): T =
        (context.contentResolver.openInputStream(uri) ?: throw UploadRefused(UploadProblem.Unreadable)).use(block)

    /** Copies at most one byte past the limit, so a wrong size report can't fill the phone. */
    private fun copy(uri: Uri, target: File): Long = read(uri) { input ->
        target.outputStream().use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            var total = 0L
            while (total <= UploadChecks.MAX_BYTES * REENCODE_SOURCE_FACTOR) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                total += count
            }
            total
        }
    }

    /** Decodes at most [MAX_SIDE] pixels a side, turned upright, and saves a JPG under the limit. */
    private fun reencode(uri: Uri, target: File) {
        val bitmap = decode(uri) ?: throw UploadRefused(UploadProblem.Unsupported)
        try {
            var quality = START_QUALITY
            while (true) {
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                if (target.length() <= UploadChecks.MAX_BYTES) return
                if (quality <= MIN_QUALITY) throw UploadRefused(UploadProblem.TooLarge)
                quality -= QUALITY_STEP
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(uri: Uri): Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder reads HEIC and applies the photo's rotation itself.
        val source = if (uri.scheme == "file") {
            ImageDecoder.createSource(File(requireNotNull(uri.path)))
        } else {
            ImageDecoder.createSource(context.contentResolver, uri)
        }
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
    } else {
        decodeLegacy(uri)
    }

    private fun decodeLegacy(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read(uri) { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val bitmap =
            read(uri) { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        return bitmap?.let { upright(it, read(uri) { stream -> ExifInterface(stream).rotationDegrees() }) }
    }

    /** Turns [bitmap] by the photo's EXIF rotation; ImageDecoder does this itself on newer Androids. */
    private fun upright(bitmap: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return bitmap
        val turned = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true,
        )
        if (turned != bitmap) bitmap.recycle()
        return turned
    }

    private fun ExifInterface.rotationDegrees(): Int =
        when (getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> DEGREES_90
            ExifInterface.ORIENTATION_ROTATE_180 -> DEGREES_180
            ExifInterface.ORIENTATION_ROTATE_270 -> DEGREES_270
            else -> 0
        }

    private fun InputStream.readNBytesCompat(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = read(buffer, filled, count - filled)
            if (read < 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }

    private companion object {
        const val FALLBACK_NAME = "upload"
        const val BUFFER_BYTES = 64 * 1024

        /** A photo up to this many times the limit is still read whole to shrink it. */
        const val REENCODE_SOURCE_FACTOR = 8
        const val MAX_SIDE = 4096
        const val START_QUALITY = 88
        const val MIN_QUALITY = 58
        const val QUALITY_STEP = 10
        const val DEGREES_90 = 90
        const val DEGREES_180 = 180
        const val DEGREES_270 = 270
    }
}
