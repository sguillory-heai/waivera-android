package com.waivera.waivera.debug.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class CrashReportRedactorTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun dropsEverythingFromJsonInputOn() {
        // The shape kotlinx.serialization uses when a server payload does not decode.
        val message = "Unexpected JSON token at offset 42: Expected string literal but 'null' literal was found\n" +
            "JSON input: {\"messageContent\":\"See you at 5, Priya\",\"author\":{\"email\":\"priya@example.com\"}}"

        val redacted = CrashReportRedactor.redactMessage(message)

        assertEquals(
            "Unexpected JSON token at offset 42: Expected string literal but 'null' literal was found",
            redacted,
        )
    }

    @Test
    fun dropsEverythingFromPlainInputOn() {
        val redacted = CrashReportRedactor.redactMessage("Field 'id' is required, input: {\"displayName\":\"Alex\"}")

        assertEquals("Field 'id' is required,", redacted)
    }

    @Test
    fun cutsLongMessagesAt300Characters() {
        val redacted = CrashReportRedactor.redactMessage("x".repeat(1_000))

        assertEquals(CrashReportRedactor.MAX_MESSAGE_LENGTH + 1, redacted?.length)
        assertTrue(redacted.orEmpty().endsWith("…"))
    }

    @Test
    fun masksTokensAndEmailsThatAreNotBehindAMarker() {
        val redacted = CrashReportRedactor.redactMessage(
            "401 for Bearer abc.def-123 sent by alex@example.com with eyJhbGciOi.eyJzdWIiOiIx.c2ln",
        ).orEmpty()

        assertFalse("abc.def-123" in redacted)
        assertFalse("alex@example.com" in redacted)
        assertFalse("eyJhbGciOi" in redacted)
    }

    @Test
    fun blankMessagesAreLeftOut() {
        assertNull(CrashReportRedactor.redactMessage(null))
        assertNull(CrashReportRedactor.redactMessage("   "))
        assertNull(CrashReportRedactor.redactMessage("JSON input: {\"text\":\"secret\"}"))
    }

    @Test
    fun reportHasTheCauseChainAndFramesButNoPayload() {
        val cause = IllegalArgumentException("Bad id. JSON input: {\"firstName\":\"Jordan\"}")
        val error = IllegalStateException("Could not load the inbox", cause)

        val report = CrashReportRedactor.report(error, "main", "header")

        assertTrue(report.startsWith("header"))
        assertTrue("Exception: java.lang.IllegalStateException: Could not load the inbox" in report)
        assertTrue("Caused by: java.lang.IllegalArgumentException: Bad id." in report)
        assertTrue("    at com.waivera.waivera.debug.crash.CrashReportRedactorTest" in report)
        assertFalse("Jordan" in report)
    }

    @Test
    fun aCauseCycleEndsTheChain() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        val report = CrashReportRedactor.report(first, "worker", "header")

        assertEquals(1, Regex("Exception: java.lang.RuntimeException: first").findAll(report).count())
    }

    @Test
    fun storeKeepsOneReportUntilCleared() {
        val store = CrashReportStore(folder.root)
        assertNull(store.read())

        store.save("older")
        store.save("newer")
        assertEquals("newer", store.read())

        store.clear()
        assertNull(store.read())
    }
}
