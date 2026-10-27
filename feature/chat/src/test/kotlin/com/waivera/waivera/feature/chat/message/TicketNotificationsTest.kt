package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.network.dto.MessageType
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class TicketNotificationsTest {
    private val payload = buildJsonObject {
        put("ticketId", "WAIV-2527")
        put("title", "Update care plan")
        put("url", "https://app.example.test/tickets/WAIV-2527")
        put("priority", "high")
        put("createdDate", "10/30/2026")
        put("dueDateLabel", "--")
        put("dueDate", "2026-11-04T23:00:00Z")
    }

    private fun bot(data: kotlinx.serialization.json.JsonElement?) = Message(
        id = 1,
        conversationId = 14,
        botId = "3",
        type = MessageType.SYSTEM,
        systemEventType = "ticket.update",
        systemEventData = data,
    )

    @Test
    fun aBotTicketUpdateBecomesACard() {
        val ticket = assertNotNull(TicketNotifications.from(bot(payload), botConversation = false))
        assertEquals("WAIV-2527", ticket.ticketId)
        assertEquals("Task", ticket.type)
        assertEquals("HIGH", ticket.priority)
        assertEquals(TicketPriority.High, ticket.priorityTone)
        assertNull(ticket.createdBy)
        assertEquals(TicketDate.OnDate(LocalDate.of(2026, 10, 30)), ticket.created)
        // "--" falls back to the ISO value, read as a UTC date.
        assertEquals(TicketDate.OnDate(LocalDate.of(2026, 11, 4)), ticket.due)
    }

    @Test
    fun stringifiedPayloadsAreUnwrapped() {
        val once = JsonPrimitive(payload.toString())
        val twice = JsonPrimitive(once.toString())
        assertNotNull(TicketNotifications.parse(once))
        assertNotNull(TicketNotifications.parse(twice))
    }

    @Test
    fun aPersonsMessageIsNeverATicket() {
        val human = bot(payload).copy(botId = null, authorId = "usr_priya_raman")
        assertNull(TicketNotifications.from(human, botConversation = false))
        assertNotNull(TicketNotifications.from(human, botConversation = true))
    }

    @Test
    fun missingRequiredFieldsOrUnsafeUrlsAreDropped() {
        val noTitle = buildJsonObject {
            put("ticketId", "1")
            put("url", "https://x.test")
        }
        val unsafe = buildJsonObject {
            put("ticketId", "1")
            put("title", "t")
            put("url", "javascript:alert(1)")
        }
        assertNull(TicketNotifications.parse(noTitle))
        assertNull(TicketNotifications.parse(unsafe))
    }

    @Test
    fun otherEventTypesAreNotTickets() {
        assertNull(
            TicketNotifications.from(bot(payload).copy(systemEventType = "ticket.created.v2"), botConversation = true),
        )
    }

    @Test
    fun ticketDates() {
        assertEquals(TicketDate.NotSet, TicketNotifications.date(null, null))
        assertEquals(TicketDate.NotSet, TicketNotifications.date("--", ""))
        assertEquals(TicketDate.OnDate(LocalDate.of(2026, 11, 4)), TicketNotifications.date(null, "2026-11-04"))
        assertEquals(TicketDate.Raw("next week"), TicketNotifications.date("next week", null))
        assertEquals(TicketDate.Raw("13/45/2026"), TicketNotifications.date("13/45/2026", null))
    }

    @Test
    fun botDetection() {
        assertTrue(
            TicketNotifications.isBot(Message(id = 1, conversationId = 1, authorId = "bot:3"), botConversation = false),
        )
        assertFalse(
            TicketNotifications.isBot(Message(id = 1, conversationId = 1, authorId = "usr_a"), botConversation = false),
        )
    }
}
