package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.TicketUpdate
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonElement

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
    private val US_DATE = Regex("""^(\d{2})/(\d{2})/(\d{4})$""")

    /** A bot message: flagged by bot id, a `bot:` author, or a bot conversation. */
    fun isBot(message: Message, botConversation: Boolean): Boolean =
        botConversation || message.botId != null || message.authorId?.startsWith("bot:") == true

    fun from(message: Message, botConversation: Boolean): TicketNotification? {
        if (!isBot(message, botConversation)) return null
        return TicketUpdate.from(message.systemEventType, message.systemEventData)?.let(::from)
    }

    /** The payload unwrapping and required fields live in core (`TicketUpdate.parse`). */
    fun parse(payload: JsonElement?): TicketNotification? = TicketUpdate.parse(payload)?.let(::from)

    /** Display defaults over the payload as sent; the url must also pass [SafeLinks]. */
    fun from(update: TicketUpdate): TicketNotification? {
        if (!SafeLinks.isAllowed(update.url)) return null
        return TicketNotification(
            ticketId = update.ticketId,
            title = update.title,
            url = update.url,
            type = update.type ?: "Task",
            createdBy = update.createdBy,
            priority = update.priority?.uppercase() ?: "UNSET",
            created = date(update.createdDate, update.createdAt),
            due = date(update.dueDateLabel, update.dueDate),
        )
    }

    /** Prefers the pre-formatted label unless it is "--". `MM/DD/YYYY` and ISO values are read as UTC. */
    fun date(preferred: String?, iso: String?): TicketDate {
        val value = preferred?.takeIf { it.isNotEmpty() && it != "--" } ?: iso?.takeIf(String::isNotEmpty)
            ?: return TicketDate.NotSet
        return usDate(value) ?: isoDate(value) ?: TicketDate.Raw(value)
    }

    /** `MM/DD/YYYY`; an impossible date stays as the raw text. */
    private fun usDate(value: String): TicketDate? {
        val (month, day, year) = US_DATE.matchEntire(value)?.destructured ?: return null
        return runCatching { TicketDate.OnDate(LocalDate.of(year.toInt(), month.toInt(), day.toInt())) }
            .getOrDefault(TicketDate.Raw(value))
    }

    /** A bare ISO date, else an instant read as a UTC day. */
    private fun isoDate(value: String): TicketDate? =
        WaiveraTime.parseLocalDate(value)?.takeIf { value.length == ISO_DATE_LENGTH }?.let { TicketDate.OnDate(it) }
            ?: WaiveraTime.parseInstant(value)?.let { TicketDate.OnDate(it.atZone(ZoneOffset.UTC).toLocalDate()) }

    private const val ISO_DATE_LENGTH = 10
}
