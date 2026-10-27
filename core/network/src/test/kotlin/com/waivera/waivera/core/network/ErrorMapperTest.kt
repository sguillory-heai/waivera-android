package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.error.ApiErrorBody
import com.waivera.waivera.core.network.error.ErrorMapper
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ErrorMapperTest {
    @Test
    fun parsesAllSixBodyShapes() {
        // (a) empty, e.g. Forbid() or the token middleware
        assertEquals(ApiErrorBody(null, null), ApiErrorBody.parse(""))
        // (b) bare JSON string
        assertEquals("No uid found in HttpContext.", ApiErrorBody.parse("\"No uid found in HttpContext.\"").message)
        // (c) bare text/plain
        assertEquals("Invalid ticketId format.", ApiErrorBody.parse("Invalid ticketId format.").message)
        // (d) camelCase ApiResponse
        val camel = ApiErrorBody.parse(Fixtures.text("rest-error-apiresponse-unauthorized.json"))
        assertEquals("USER_CONTEXT_MISSING", camel.errorCode)
        // (e) PascalCase ApiResponse from ExceptionMiddleware
        val pascal = ApiErrorBody.parse(Fixtures.text("rest-error-exception-middleware-500.json"))
        assertEquals("Something went wrong. Please try again later.", pascal.message)
        assertNull(pascal.errorCode)
        // (f) { success, message }
        assertEquals(
            "FCM registrations require an FCM registration token.",
            ApiErrorBody.parse(Fixtures.text("rest-mobile-push-register-error.json")).message,
        )
    }

    @Test
    fun serverErrorsNeverCarryServerText() {
        val error = ErrorMapper.fromHttp(500, Fixtures.text("rest-error-controller-500.json"))
        assertIs<WaiveraError.Server>(error)
        assertEquals(500, error.httpStatus)
        assertNull(error.message)
        assertIs<WaiveraError.Server>(
            ErrorMapper.fromHttp(500, Fixtures.text("rest-error-exception-middleware-500.json")),
        )
        assertIs<WaiveraError.Server>(ErrorMapper.fromHttp(503, null))
    }

    @Test
    fun mapsStatusesToTypedErrors() {
        val unauthorized = ErrorMapper.fromHttp(401, Fixtures.text("rest-error-apiresponse-unauthorized.json"))
        assertIs<WaiveraError.SessionExpired>(unauthorized)
        assertEquals("USER_CONTEXT_MISSING", unauthorized.errorCode)
        assertIs<WaiveraError.SessionExpired>(
            ErrorMapper.fromHttp(401, "Session expired. Logged in from another device."),
        )
        assertIs<WaiveraError.Forbidden>(ErrorMapper.fromHttp(403, ""))
        assertIs<WaiveraError.NotFound>(ErrorMapper.fromHttp(404, "\"Thread root message not found.\""))
        assertIs<WaiveraError.Conflict>(ErrorMapper.fromHttp(409, """{"Message":"conflict","StatusCode":409}"""))
        assertIs<WaiveraError.AttachmentRejected>(
            ErrorMapper.fromHttp(423, "Attachment was rejected by security validation."),
        )
        assertIs<WaiveraError.RateLimited>(ErrorMapper.fromHttp(429, null))
        assertIs<WaiveraError.Unexpected>(ErrorMapper.fromHttp(418, null))
    }

    @Test
    fun classifiesSendRejections() {
        assertIs<WaiveraError.ChannelArchived>(ErrorMapper.fromHttp(403, "\"This channel is archived.\""))
        assertIs<WaiveraError.ChannelReadOnly>(
            ErrorMapper.fromHttp(403, "\"This channel is read-only for your account.\""),
        )
        assertIs<WaiveraError.AttachmentExpired>(
            ErrorMapper.fromHttp(400, "\"The attachment upload is missing or expired.\""),
        )
        assertIs<WaiveraError.Validation>(ErrorMapper.fromHttp(400, "\"You cannot send a message to yourself.\""))
    }

    @Test
    fun classifiesHubExceptions() {
        assertIs<WaiveraError.ChannelArchived>(ErrorMapper.fromHubMessage("This channel is archived."))
        assertIs<WaiveraError.ChannelReadOnly>(ErrorMapper.fromHubMessage("You cannot post in this conversation."))
        assertIs<WaiveraError.Validation>(
            ErrorMapper.fromHubMessage("Reaction must be a valid emoji value no longer than 32 characters."),
        )
        assertIs<WaiveraError.Forbidden>(ErrorMapper.fromHubMessage("You are not a participant in this conversation."))
        assertIs<WaiveraError.Unexpected>(ErrorMapper.fromHubMessage(null))
    }

    @Test
    fun transientErrorsAreMarked() {
        assertEquals(true, WaiveraError.Network().isTransient)
        assertEquals(true, WaiveraError.Server(502).isTransient)
        assertEquals(false, WaiveraError.ChannelArchived().isTransient)
    }
}
