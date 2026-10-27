package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.Reaction
import com.waivera.waivera.core.data.model.SendState
import java.time.Instant
import java.time.ZoneId

/** Fixed sample messages for previews; screenshot tests use the demo graph instead. */
internal object ChatPreviewData {
    const val ME = "usr_preview_me"
    val zone: ZoneId = ZoneId.of("America/Chicago")
    val now: Instant = Instant.parse("2026-11-01T16:30:00Z")
    val time = RowTime(now, zone)

    private val priya = Person(id = "usr_preview_priya", firstName = "Priya", lastName = "Shah")
    private val me = Person(id = ME, firstName = "Alex", lastName = "Rivera")

    val messages: List<Message> = listOf(
        message(1, priya, "Morning! Are we still on for the 10:00 walkthrough?", minutesAgo = 75),
        message(2, priya, "I can bring the updated floor plan.", minutesAgo = 74),
        message(3, me, "Yes, see you there.", minutesAgo = 60).copy(
            reactions = listOf(Reaction(priya.id, "👍")),
        ),
        message(4, priya, "Here is the plan.", minutesAgo = 20).copy(
            attachment = Attachment(
                url = "preview/plan.pdf",
                originalName = "Floor plan.pdf",
                contentType = "application/pdf",
                size = 482_000,
            ),
            threadReplyCount = 2,
        ),
        message(5, me, "Thanks, I will review it before lunch.", minutesAgo = 2).copy(
            id = -5,
            clientMessageId = "preview-failed",
            sendState = SendState.FAILED,
        ),
    )

    val names: Map<String, String> = mapOf(priya.id to priya.name, me.id to me.name)

    fun items(): List<TimelineItem> = TimelineBuilder.build(messages, ME, zone, botConversation = false)

    private fun message(id: Long, author: Person, text: String, minutesAgo: Long) = Message(
        id = id,
        conversationId = 1,
        author = author,
        authorId = author.id,
        text = text,
        createdAt = now.minusSeconds(minutesAgo * SECONDS_PER_MINUTE),
    )

    private const val SECONDS_PER_MINUTE = 60L
}
