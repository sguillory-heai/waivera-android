package com.waivera.waivera.core.network.error

import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_BAD_REQUEST
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_CONFLICT
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_FORBIDDEN
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_LOCKED
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_NOT_FOUND
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_PAYLOAD_TOO_LARGE
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_SERVER_ERROR
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_TOO_MANY_REQUESTS
import com.waivera.waivera.core.network.error.WaiveraError.Companion.HTTP_UNAUTHORIZED

/**
 * Maps an HTTP failure to a [WaiveraError]. Reads the status first and uses the body's message
 * only to tell apart the 400/403 cases the UI words differently (archived, read-only, expired
 * attachment). 5xx text is ignored entirely because controller catch blocks leak `ex.Message`.
 */
object ErrorMapper {
    fun fromHttp(status: Int, body: String?): WaiveraError {
        val parsed = ApiErrorBody.parse(body)
        val message = parsed.message?.lowercase().orEmpty()
        return when {
            status >= HTTP_SERVER_ERROR -> WaiveraError.Server(status)
            status == HTTP_UNAUTHORIZED -> WaiveraError.SessionExpired(parsed.errorCode)
            status == HTTP_FORBIDDEN || status == HTTP_BAD_REQUEST -> classifyRejection(
                status,
                message,
                parsed.errorCode,
            )
            else -> fromOtherStatus(status)
        }
    }

    private fun classifyRejection(status: Int, message: String, errorCode: String?): WaiveraError = when {
        "archived" in message -> WaiveraError.ChannelArchived(status)
        "read-only" in message || "cannot post" in message -> WaiveraError.ChannelReadOnly(status)
        "attachment upload is missing or expired" in message -> WaiveraError.AttachmentExpired()
        status == HTTP_FORBIDDEN -> WaiveraError.Forbidden(errorCode)
        else -> WaiveraError.Validation(errorCode)
    }

    private fun fromOtherStatus(status: Int): WaiveraError = when (status) {
        HTTP_NOT_FOUND -> WaiveraError.NotFound()
        HTTP_CONFLICT -> WaiveraError.Conflict()
        HTTP_PAYLOAD_TOO_LARGE -> WaiveraError.AttachmentTooLarge()
        HTTP_LOCKED -> WaiveraError.AttachmentRejected()
        HTTP_TOO_MANY_REQUESTS -> WaiveraError.RateLimited()
        else -> WaiveraError.Unexpected(status)
    }

    /**
     * Hub methods fail with a `HubException` whose message is the server's English text. Classify
     * it the same way; anything unrecognised is [WaiveraError.Unexpected].
     */
    fun fromHubMessage(message: String?): WaiveraError {
        val text = message?.lowercase().orEmpty()
        return when {
            "archived" in text -> WaiveraError.ChannelArchived(null)
            "read-only" in text || "cannot post" in text -> WaiveraError.ChannelReadOnly(null)
            "not authenticated" in text || "unauthenticated" in text -> WaiveraError.SessionExpired()
            "not a participant" in text || "not an active participant" in text || "access denied" in text ->
                WaiveraError.Forbidden()
            "reaction must be" in text || "clientmessageid" in text -> WaiveraError.Validation()
            else -> WaiveraError.Unexpected()
        }
    }
}
