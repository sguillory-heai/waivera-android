package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class TicketPriority { Critical, High, Medium, Other }

/** A bot `ticket.update` card (03 §15). Only built when the id, title and an http(s) url are present. */
@Immutable
data class TicketNotification(
    val ticketId: String,
    val title: String,
    val url: String,
    /** "Task" when absent. */
    val type: String,
    /** Null means "Unknown user". */
    val createdBy: String?,
    /** Upper-cased; "UNSET" when absent. */
    val priority: String,
    val created: TicketDate,
    val due: TicketDate,
) {
    val priorityTone: TicketPriority
        get() = when (priority) {
            "CRITICAL", "URGENT" -> TicketPriority.Critical
            "HIGH" -> TicketPriority.High
            "MEDIUM" -> TicketPriority.Medium
            else -> TicketPriority.Other
        }
}

/** A ticket date as shown: a UTC calendar date, the raw value when unparsable, or not set. */
@Immutable
sealed interface TicketDate {
    data object NotSet : TicketDate

    data class OnDate(val date: LocalDate) : TicketDate

    data class Raw(val text: String) : TicketDate

    fun format(locale: Locale = Locale.getDefault()): String? = when (this) {
        NotSet -> null
        is OnDate -> DateTimeFormatter.ofPattern("MMM d, yyyy", locale).format(date)
        is Raw -> text
    }
}

object TicketNotifications {
    private const val MAX_UNWRAP = 3
    private const val TICKET_EVENT = "ticket.update"
    private val US_DATE = Regex("""^(\d{2})/(\d{2})/(\d{4})$""")
    private val json = Json { isLenient = true }

    /** A bot message: flagged by bot id, a `bot:` author, or a bot conversation. */
    fun isBot(message: Message, botConversation: Boolean): Boolean =
        botConversation || message.botId != null || message.authorId?.startsWith("bot:") == true

    fun from(message: Message, botConversation: Boolean): TicketNotification? {
        if (!isBot(message, botConversation) || message.systemEventType != TICKET_EVENT) return null
        return parse(message.systemEventData)
    }

    @Suppress("ReturnCount")
    fun parse(payload: JsonElement?): TicketNotification? {
        var candidate: JsonElement? = payload
        repeat(MAX_UNWRAP) {
            val primitive = candidate as? JsonPrimitive
            if (primitive != null && primitive.isString) {
                candidate = runCatching { json.parseToJsonElement(primitive.content) }.getOrNull() ?: return null
            }
        }
        val value = candidate as? JsonObject ?: return null
        val ticketId = value.text("ticketId") ?: return null
        val title = value.text("title") ?: return null
        val url = value.text("url")?.takeIf(SafeLinks::isAllowed) ?: return null
        return TicketNotification(
            ticketId = ticketId,
            title = title,
            url = url,
            type = value.text("type") ?: "Task",
            createdBy = value.text("createdBy"),
            priority = value.text("priority")?.uppercase() ?: "UNSET",
            created = date(value.rawString("createdDate"), value.rawString("createdAt")),
            due = date(value.rawString("dueDateLabel"), value.rawString("dueDate")),
        )
    }

    /** Prefers the pre-formatted label unless it is "--". `MM/DD/YYYY` and ISO values are read as UTC. */
    fun date(preferred: String?, iso: String?): TicketDate {
        val value = preferred?.takeIf { it.isNotEmpty() && it != "--" } ?: iso?.takeIf(String::isNotEmpty)
            ?: return TicketDate.NotSet
        US_DATE.matchEntire(value)?.groupValues?.let { g ->
            return runCatching { TicketDate.OnDate(LocalDate.of(g[3].toInt(), g[1].toInt(), g[2].toInt())) }
                .getOrDefault(TicketDate.Raw(value))
        }
        WaiveraTime.parseLocalDate(value)?.takeIf { value.length == ISO_DATE_LENGTH }?.let { return TicketDate.OnDate(it) }
        return WaiveraTime.parseInstant(value)?.let { TicketDate.OnDate(it.atZone(ZoneOffset.UTC).toLocalDate()) }
            ?: TicketDate.Raw(value)
    }

    private const val ISO_DATE_LENGTH = 10

    private fun JsonObject.rawString(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** Any primitive, as text, trimmed; null when blank (the reference app uses `String(value ?? '')`). */
    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}
