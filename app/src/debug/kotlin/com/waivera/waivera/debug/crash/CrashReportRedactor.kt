package com.waivera.waivera.debug.crash

/**
 * Turns an uncaught exception into a report a tester can paste into a ticket without leaking
 * anything: the exception class chain, each stack frame, and messages cut at 300 characters with
 * any decoded payload removed. kotlinx.serialization appends the offending JSON after
 * "JSON input:" (and some parsers after "input:"), which can hold message bodies, names and tokens,
 * so everything from that marker on is dropped. Bearer tokens, JWTs and email addresses that
 * survive in the rest are masked.
 */
object CrashReportRedactor {
    const val MAX_MESSAGE_LENGTH = 300
    private const val MAX_CAUSES = 8
    private const val MAX_FRAMES_PER_CAUSE = 60
    private const val MASK = "<redacted>"

    private val payloadMarkers = listOf("JSON input:", "input:")
    private val bearer = Regex("""(?i)bearer\s+[A-Za-z0-9._~+/=-]+""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    fun report(error: Throwable, thread: String, header: String): String = buildString {
        appendLine(header)
        appendLine("Thread: ${thread.take(MAX_MESSAGE_LENGTH)}")
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < MAX_CAUSES && seen.add(current)) {
            appendLine()
            append(if (depth == 0) "Exception: " else "Caused by: ")
            append(current.javaClass.name)
            redactMessage(current.message)?.let { append(": ").append(it) }
            appendLine()
            current.stackTrace.take(MAX_FRAMES_PER_CAUSE).forEach { appendLine("    at $it") }
            val hidden = current.stackTrace.size - MAX_FRAMES_PER_CAUSE
            if (hidden > 0) appendLine("    … $hidden more")
            current = current.cause
            depth++
        }
    }

    /** The message with any payload removed, secrets masked, cut to [MAX_MESSAGE_LENGTH]; null when empty. */
    fun redactMessage(message: String?): String? {
        if (message.isNullOrBlank()) return null
        val cut = payloadMarkers
            .mapNotNull { marker -> message.indexOf(marker, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull()
            ?.let { message.substring(0, it) }
            ?: message
        val masked = cut.replace(bearer, MASK).replace(jwt, MASK).replace(email, MASK).trim()
        if (masked.isEmpty()) return null
        return if (masked.length > MAX_MESSAGE_LENGTH) masked.take(MAX_MESSAGE_LENGTH) + "…" else masked
    }
}
