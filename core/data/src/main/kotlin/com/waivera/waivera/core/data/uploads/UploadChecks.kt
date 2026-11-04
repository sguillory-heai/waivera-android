package com.waivera.waivera.core.data.uploads

import com.waivera.waivera.core.data.model.UploadProblem

/** What a file's first bytes say it is, as sprout_web `fileSignatures` and the API read them. */
enum class Sniffed(val extension: String, val mimeType: String) {
    Pdf("pdf", "application/pdf"),
    Png("png", "image/png"),
    Jpeg("jpg", "image/jpeg"),
    Gif("gif", "image/gif"),

    /** A photo format the server refuses (HEIC, WebP); the phone turns it into a JPG. */
    OtherImage("jpg", "image/jpeg"),
    Unknown("", "application/octet-stream"),
    ;

    /** Sent as it is: a type the server takes. */
    val accepted: Boolean get() = this == Pdf || this == Png || this == Jpeg || this == Gif
}

/**
 * The web's upload rules (sprout_web `validateUploadFile`, sprout_api `TaskFileUploadSafetyPolicy`):
 * PDF, PNG, JPG or GIF by content, not empty, at most [MAX_BYTES]. Photos the server can't take are
 * re-encoded as JPG, and so are photos over the limit, so a phone picture is never refused for size.
 */
object UploadChecks {
    const val MAX_BYTES = 10L * 1024 * 1024
    const val MAX_FILES = 10
    private const val HEADER_BYTES = 16

    val headerSize: Int get() = HEADER_BYTES

    fun sniff(header: ByteArray): Sniffed = when {
        header.startsWith(PDF_SIGNATURE) -> Sniffed.Pdf
        header.startsWith(PNG_SIGNATURE) -> Sniffed.Png
        header.startsWith(JPEG_SIGNATURE) -> Sniffed.Jpeg
        header.startsWith(GIF_SIGNATURE) -> Sniffed.Gif
        isHeif(header) || isWebp(header) -> Sniffed.OtherImage
        else -> Sniffed.Unknown
    }

    /** What happens to a file of [size] bytes that sniffed as [type]. */
    fun plan(type: Sniffed, size: Long): Plan = when {
        size <= 0L -> Plan.Refuse(UploadProblem.Empty)
        type == Sniffed.Unknown -> Plan.Refuse(UploadProblem.Unsupported)
        type == Sniffed.OtherImage -> Plan.Reencode
        // A big photo shrinks to fit; a PDF or animated GIF can't, so the web's message applies.
        size > MAX_BYTES && (type == Sniffed.Jpeg || type == Sniffed.Png) -> Plan.Reencode
        size > MAX_BYTES -> Plan.Refuse(UploadProblem.TooLarge)
        else -> Plan.Send
    }

    /**
     * The picked name with the extension the content has, so the server's name check passes:
     * "scan" or "scan.jpeg" from a PNG becomes "scan.png". Path parts and reserved characters go.
     */
    fun fileName(picked: String?, type: Sniffed, fallback: String): String {
        val base = picked?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.replace(Reserved, "-")
            ?.trim()
            ?.trim('.')
            .orEmpty()
            .ifEmpty { fallback }
        val dot = base.lastIndexOf('.')
        val stem = (if (dot > 0) base.substring(0, dot) else base).take(MAX_STEM).trim().ifEmpty { fallback }
        val extension = if (dot > 0) base.substring(dot + 1).lowercase() else ""
        val keeps = extension == type.extension || (type == Sniffed.Jpeg && extension == "jpeg")
        return if (keeps) "$stem.$extension" else "$stem.${type.extension}"
    }

    /**
     * A template's name with the file's extension added, so "Signed plan" saves as "Signed plan.pdf"
     * and the server's name check passes. A name that already ends in it is kept.
     */
    fun templateName(template: String, mimeType: String): String {
        val type = Sniffed.entries.firstOrNull { it.accepted && it.mimeType == mimeType } ?: return template
        val name = template.replace(Reserved, "-").trim().trim('.').take(MAX_STEM).ifEmpty { return template }
        val lower = name.lowercase()
        val keeps = lower.endsWith(".${type.extension}") || (type == Sniffed.Jpeg && lower.endsWith(".jpeg"))
        return if (keeps) name else "$name.${type.extension}"
    }

    sealed interface Plan {
        data object Send : Plan

        data object Reencode : Plan

        data class Refuse(val problem: UploadProblem) : Plan
    }

    /** [signature] is one byte per character (Latin-1), as file signatures are usually written. */
    private fun ByteArray.startsWith(signature: String): Boolean {
        val bytes = signature.toByteArray(Charsets.ISO_8859_1)
        return size >= bytes.size && bytes.indices.all { this[it] == bytes[it] }
    }

    private fun isHeif(header: ByteArray): Boolean {
        if (header.size < BRAND_END || String(header, BOX_TYPE_START, BOX_TYPE_LENGTH, Charsets.US_ASCII) != "ftyp") {
            return false
        }
        val brand = String(header, BRAND_START, BOX_TYPE_LENGTH, Charsets.US_ASCII)
        return brand in HeifBrands
    }

    private fun isWebp(header: ByteArray): Boolean = header.size >= WEBP_END &&
        String(header, 0, BOX_TYPE_LENGTH, Charsets.US_ASCII) == "RIFF" &&
        String(header, WEBP_START, BOX_TYPE_LENGTH, Charsets.US_ASCII) == "WEBP"

    private const val PDF_SIGNATURE = "%PDF"
    private const val PNG_SIGNATURE = "\u0089PNG\r\n\u001A\n"
    private const val JPEG_SIGNATURE = "\u00FF\u00D8\u00FF"
    private const val GIF_SIGNATURE = "GIF8"
    private val Reserved = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val HeifBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif")
    private const val MAX_STEM = 120
    private const val BOX_TYPE_START = 4
    private const val BOX_TYPE_LENGTH = 4
    private const val BRAND_START = 8
    private const val BRAND_END = 12
    private const val WEBP_START = 8
    private const val WEBP_END = 12
}
