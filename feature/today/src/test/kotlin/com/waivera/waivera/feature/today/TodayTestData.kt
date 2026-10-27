package com.waivera.waivera.feature.today

import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.data.model.MyInvitation
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.network.dto.ChatActivityType
import java.time.Instant
import java.time.LocalDate

const val ME = "usr_alex_rivera"

fun activity(
    id: Long,
    type: ChatActivityType = ChatActivityType.THREAD_REPLY,
    read: Boolean = false,
    createdAt: Instant? = TEST_NOW.minusSeconds(id * 60),
    conversationId: Long = 7,
    actor: String? = "Dana Ortiz",
    reaction: String? = null,
    summary: String? = "Dana Ortiz replied in a thread",
    threadRoot: Long? = null,
) = ActivityItem(
    id = id,
    type = type,
    actorUserId = "usr_dana",
    actorName = actor,
    conversationId = conversationId,
    messageId = 9_000 + id,
    threadRootMessageId = threadRoot,
    reactionType = reaction,
    summary = summary,
    createdAt = createdAt,
    read = read,
)

fun conversation(
    id: Long,
    kind: ConversationKind = ConversationKind.DIRECT,
    unread: Int = 0,
    text: String? = "Can you cover Thursday?",
    at: Instant = TEST_NOW.minusSeconds(300),
    otherName: String = "Priya Raman",
) = Conversation(
    id = id,
    kind = kind,
    participants = listOf(Person(ME, displayName = "Alex Rivera"), Person("usr_other_$id", displayName = otherName)),
    lastMessage = MessagePreview(messageId = id * 100, authorId = "usr_other_$id", text = text, createdAt = at),
    unreadCount = unread,
)

fun timedEvent(
    id: String,
    start: String,
    end: String,
    invitation: MyInvitation = MyInvitation.ACCEPTED,
    title: String = "Event $id",
) = ScheduleEvent(
    id = id,
    title = title,
    startAt = Instant.parse(start),
    endAt = Instant.parse(end),
    ownerName = "Priya Raman",
    myInvitation = invitation,
)

fun allDayEvent(id: String, start: LocalDate, endExclusive: LocalDate = start.plusDays(1)) = ScheduleEvent(
    id = id,
    title = "All day $id",
    allDayStart = start,
    allDayEndExclusive = endExclusive,
)
