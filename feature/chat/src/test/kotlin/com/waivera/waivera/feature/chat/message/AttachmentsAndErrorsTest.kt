package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.network.error.WaiveraError
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AttachmentsAndErrorsTest {
    private fun attachment(contentType: String? = null, type: String? = null, name: String? = null) =
        Attachment(url = "api/attachments/1", originalName = name, contentType = contentType, type = type)

    @Test
    fun imagesAreRecognisedByContentTypeOrType() {
        assertTrue(Attachments.isImage(attachment(contentType = "image/png")))
        assertTrue(Attachments.isImage(attachment(type = "jpeg")))
        assertTrue(Attachments.isImage(attachment(type = "image")))
        assertFalse(Attachments.isImage(attachment(contentType = "application/pdf")))
    }

    @Test
    fun fileNameFallsBackToTheMessageId() {
        assertEquals("Plan.pdf", Attachments.fileName(attachment(name = " Plan.pdf "), 9))
        assertEquals("waivera-attachment-9.pdf", Attachments.fileName(attachment(contentType = "application/pdf"), 9))
        assertEquals("waivera-attachment-0.bin", Attachments.fileName(attachment(), -4))
    }

    @Test
    fun mimeTypeIsTakenOrInferred() {
        assertEquals("application/pdf", Attachments.mimeType(attachment(contentType = "application/pdf"), 1))
        assertEquals("text/csv", Attachments.mimeType(attachment(type = "file", name = "hours.csv"), 1))
        assertEquals("application/octet-stream", Attachments.mimeType(attachment(name = "notes"), 1))
    }

    @Test
    fun fileKinds() {
        assertEquals(FileKind.Spreadsheet, Attachments.kind(attachment(name = "hours.xlsx"), 1))
        assertEquals(FileKind.Document, Attachments.kind(attachment(contentType = "application/pdf"), 1))
        assertEquals(FileKind.Other, Attachments.kind(attachment(name = "clip.mov"), 1))
    }

    @Test
    fun sendErrorsMapFromTypedErrors() {
        assertEquals(SendErrorKind.Archived, SendErrors.from(WaiveraError.ChannelArchived()))
        assertEquals(SendErrorKind.ReadOnly, SendErrors.from(WaiveraError.ChannelReadOnly()))
        assertEquals(SendErrorKind.ReadOnly, SendErrors.from(WaiveraError.Forbidden()))
        assertEquals(SendErrorKind.Offline, SendErrors.from(WaiveraError.Network()))
        assertEquals(SendErrorKind.Offline, SendErrors.from(WaiveraError.RealtimeUnavailable()))
        assertEquals(SendErrorKind.Session, SendErrors.from(WaiveraError.SessionExpired()))
        assertEquals(SendErrorKind.Server, SendErrors.from(WaiveraError.Server(500)))
    }

    @Test
    fun failedOutboxRowsInferTheLikeliestCause() {
        fun guess(online: Boolean, canWrite: Boolean, archived: Boolean) =
            SendErrors.forFailedSend(error = null, isOnline = online, canWrite = canWrite, isArchived = archived)
        assertEquals(SendErrorKind.Archived, guess(online = true, canWrite = false, archived = true))
        assertEquals(SendErrorKind.ReadOnly, guess(online = true, canWrite = false, archived = false))
        assertEquals(SendErrorKind.Offline, guess(online = false, canWrite = true, archived = false))
        assertEquals(SendErrorKind.Server, guess(online = true, canWrite = true, archived = false))
    }

    @Test
    fun theOutboxReasonWinsOverTheGuess() {
        val kind = SendErrors.forFailedSend(
            error = WaiveraError.ChannelArchived(),
            isOnline = false,
            canWrite = true,
            isArchived = false,
        )
        assertEquals(SendErrorKind.Archived, kind)
    }
}
