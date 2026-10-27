package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.network.dto.MessageType
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** A system line with its `{[TIMESTAMP- MM/DD/YYYY HH:MM:SS]}` token removed and parsed as UTC (11 §6). */
data class ParsedSystemMessage(val text: String, val embeddedTimestamp: Instant?)

object SystemMessages {
    private val TIMESTAMP = Regex(
        """\s*-?\s*\{\[\s*TIMESTAMP\s*[-·:]\s*(\d{1,2})/(\d{1,2})/(\d{4})\s+(\d{1,2}):(\d{2}):(\d{2})\s*]}\s*""",
        RegexOption.IGNORE_CASE,
    )

    // Capture groups of TIMESTAMP: month/day/year hour:minute:second.
    private const val MONTH = 1
    private const val DAY = 2
    private const val YEAR = 3
    private const val HOUR = 4
    private const val MINUTE = 5
    private const val SECOND = 6
    private val TRAILING_DASH = Regex("""\s*-\s*$""")
    private val WHITESPACE = Regex("""\s+""")

    fun parse(value: String?): ParsedSystemMessage {
        if (value.isNullOrEmpty()) return ParsedSystemMessage("", null)
        val timestamp = TIMESTAMP.find(value)?.groupValues?.let { g ->
            try {
                LocalDateTime.of(
                    g[YEAR].toInt(),
                    g[MONTH].toInt(),
                    g[DAY].toInt(),
                    g[HOUR].toInt(),
                    g[MINUTE].toInt(),
                    g[SECOND].toInt(),
                )
                    .toInstant(ZoneOffset.UTC)
            } catch (_: DateTimeException) {
                null
            }
        }
        val text = value.replaceFirst(TIMESTAMP, "").replace(TRAILING_DASH, "").replace(WHITESPACE, " ").trim()
        return ParsedSystemMessage(text, timestamp)
    }

    fun isSystem(message: Message): Boolean =
        message.type == MessageType.SYSTEM || message.type == MessageType.CHANNEL_EVENT

    /** System messages with a positive call record id collapse into one call card (11 §7). */
    fun callRecordId(message: Message): Long? = message.callRecordId?.takeIf { it > 0 && isSystem(message) }
}

enum class CallOutcome { Declined, Missed, Ended, InProgress }

@Immutable
data class CallLogSummary(
    val outcome: CallOutcome,
    /** Verbatim from the "ended in …" text, else computed ("45s", "12m 4s", "3 min"), else null. */
    val duration: String?,
    /** Embedded token of the newest message, else its createdAt. */
    val time: Instant?,
    val isMissed: Boolean,
)

object CallLogs {
    private val DURATION = Regex("""(?:in|duration:?) +([^-·]+?)(?:\s+-|\s+·|$)""", RegexOption.IGNORE_CASE)
    private const val SECONDS_PER_MINUTE = 60L

    /** [messages] must be one call record's messages, oldest first. */
    fun summarize(messages: List<Message>): CallLogSummary {
        val contents = messages.map { it.text.orEmpty().lowercase() }
        val outcome = when {
            contents.any { "rejected" in it || "declined" in it } -> CallOutcome.Declined
            contents.any { "missed" in it } -> CallOutcome.Missed
            contents.any { "ended" in it } -> CallOutcome.Ended
            else -> CallOutcome.InProgress
        }
        val last = messages.lastOrNull()
        return CallLogSummary(
            outcome = outcome,
            duration = duration(messages),
            time = SystemMessages.parse(last?.text).embeddedTimestamp ?: last?.createdAt,
            isMissed = outcome == CallOutcome.Declined || outcome == CallOutcome.Missed,
        )
    }

    @Suppress("ReturnCount")
    fun duration(messages: List<Message>): String? {
        val ended = messages.lastOrNull { it.text.orEmpty().lowercase().contains("ended") }
        ended?.text?.let { DURATION.find(it) }?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty)?.let {
            return it
        }
        if (messages.size < 2) return null
        val first = messages.first().createdAt ?: return null
        val lastAt = messages.last().createdAt ?: return null
        val millis = lastAt.toEpochMilli() - first.toEpochMilli()
        if (millis < 0) return null
        val seconds = Math.round(millis / MILLIS_PER_SECOND)
        if (seconds < SECONDS_PER_MINUTE) return "${seconds}s"
        val minutes = seconds / SECONDS_PER_MINUTE
        val remainder = seconds % SECONDS_PER_MINUTE
        return if (remainder != 0L) "${minutes}m ${remainder}s" else "$minutes min"
    }

    private const val MILLIS_PER_SECOND = 1000.0
}
