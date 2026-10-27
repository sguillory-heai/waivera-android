package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Attachment

enum class FileKind { Spreadsheet, Document, Other }

/** Attachment rules from 03 §8, independent of Android so they can be unit tested. */
object Attachments {
    private val MIME_BY_EXTENSION = mapOf(
        "pdf" to "application/pdf",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xls" to "application/vnd.ms-excel",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "doc" to "application/msword",
        "txt" to "text/plain",
        "csv" to "text/csv",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "heic" to "image/heic",
    )

    private val IMAGE_TYPES = setOf("image", "png", "jpg", "jpeg", "gif", "webp", "heic", "heif")
    private const val DEFAULT_MIME = "application/octet-stream"

    fun isImage(attachment: Attachment): Boolean {
        val contentType = attachment.contentType?.lowercase()?.trim().orEmpty()
        val type = attachment.type?.lowercase()?.trim().orEmpty()
        return contentType.startsWith("image/") ||
            type.startsWith("image/") ||
            type in IMAGE_TYPES ||
            contentType in IMAGE_TYPES
    }

    /** The name shown and saved: the original name, else `waivera-attachment-<id>.<ext>`. */
    fun fileName(attachment: Attachment, messageId: Long): String =
        attachment.originalName?.trim()?.takeIf(String::isNotEmpty)
            ?: "waivera-attachment-${messageId.coerceAtLeast(0)}.${extensionFor(typeHint(attachment))}"

    /** The type if it is a MIME type, else inferred from the file name, else octet-stream. */
    fun mimeType(attachment: Attachment, messageId: Long): String =
        attachment.contentType?.takeIf { '/' in it }
            ?: attachment.type?.takeIf { '/' in it }
            ?: MIME_BY_EXTENSION[fileName(attachment, messageId).substringAfterLast('.', "").lowercase()]
            ?: DEFAULT_MIME

    fun kind(attachment: Attachment, messageId: Long): FileKind {
        val hint = (typeHint(attachment) + " " + fileName(attachment, messageId)).lowercase()
        return when {
            "xls" in hint || "excel" in hint || "spreadsheet" in hint || "csv" in hint -> FileKind.Spreadsheet
            "pdf" in hint || "doc" in hint || "word" in hint -> FileKind.Document
            else -> FileKind.Other
        }
    }

    private fun typeHint(attachment: Attachment): String =
        listOfNotNull(attachment.contentType, attachment.type).joinToString(" ").lowercase()

    private fun extensionFor(type: String): String = when {
        "pdf" in type -> "pdf"
        "excel" in type || "spreadsheet" in type -> "xlsx"
        "word" in type || "document" in type -> "docx"
        "text" in type -> "txt"
        else -> "bin"
    }
}
