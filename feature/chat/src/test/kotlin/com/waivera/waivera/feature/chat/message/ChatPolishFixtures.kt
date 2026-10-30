package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.demo.DemoData

/**
 * Rows for the chat polish checks (owner's device review, 2026-10-30): reactions and replies under
 * the bubble, runs with and without their time lines, in a DM with Priya. Test data only.
 */
internal object ChatPolishFixtures {
    const val SHORT = "okay yeah"
    const val REPLIES_TWO = "2 replies"
    val time = RowTime(DemoData.NOW, DemoData.ZONE)

    val reactions: List<ReactionSummary> = listOf(
        ReactionSummary("👍", 1, mine = true, userIds = listOf(DemoData.ME)),
        ReactionSummary("❤️", 2, mine = false, userIds = listOf(DemoData.PRIYA, DemoData.JORDAN)),
    )

    /** More reactions than one line of a small phone's bubble column holds. */
    val manyReactions: List<ReactionSummary> = listOf("👍", "❤️", "😂", "🎉", "🙏", "👀", "🔥", "✅", "💯", "👏")
        .mapIndexed { index, emoji ->
            ReactionSummary(emoji, index + 1, mine = index == 0, userIds = List(index + 1) { "usr_$it" })
        }

    fun message(id: Long, mine: Boolean, text: String, minutesAgo: Long = 1, replies: Int = 0) = Message(
        id = id,
        conversationId = DemoData.DM_PRIYA,
        authorId = if (mine) DemoData.ME else DemoData.PRIYA,
        text = text,
        createdAt = DemoData.NOW.minusSeconds(minutesAgo * SECONDS_PER_MINUTE),
        threadReplyCount = replies,
    )

    fun row(
        message: Message,
        groupedWithPrevious: Boolean = false,
        groupedWithNext: Boolean = false,
        reactions: List<ReactionSummary> = emptyList(),
    ) = TimelineItem.MessageRow(
        key = "m-${message.id}",
        message = message,
        isMine = message.authorId == DemoData.ME,
        isBot = false,
        groupedWithPrevious = groupedWithPrevious,
        groupedWithNext = groupedWithNext,
        rich = null,
        ticket = null,
        reactions = reactions,
    )

    /** Every affordance on, as in a writable conversation. */
    fun callbacks(onReact: (Long, String) -> Unit = { _, _ -> }, onOpenThread: (Long) -> Unit = {}) =
        MessageRowCallbacks(
            onToggleTimestamp = {},
            onLongPress = {},
            onReact = onReact,
            onReactionDetails = { _, _ -> },
            onOpenThread = onOpenThread,
            onOpenAttachment = {},
            onOpenPerson = {},
            onLink = {},
            onRetry = {},
            onEditFailed = {},
            onDiscard = {},
        )

    /** An incoming message and one of mine, each with reactions and two replies. */
    fun engagementRows(reactions: List<ReactionSummary> = this.reactions): List<TimelineItem.MessageRow> = listOf(
        row(message(1, mine = false, text = SHORT, minutesAgo = 3, replies = 2), reactions = reactions),
        row(message(2, mine = true, text = "On it", minutesAgo = 2, replies = 2), reactions = reactions),
    )

    /** Priya's run of three, then my run of two: every bubble but the last of a run hides its time. */
    fun runs(): List<TimelineItem.MessageRow> = listOf(
        row(message(11, mine = false, text = "They don't normally", minutesAgo = 6), groupedWithNext = true),
        row(
            message(12, mine = false, text = "but some do", minutesAgo = 5),
            groupedWithPrevious = true,
            groupedWithNext = true,
        ),
        row(message(13, mine = false, text = "why?", minutesAgo = 4), groupedWithPrevious = true),
        row(message(14, mine = true, text = "Just reviewing some data", minutesAgo = 3), groupedWithNext = true),
        row(message(15, mine = true, text = "and was curious", minutesAgo = 2), groupedWithPrevious = true),
    )

    /** [rows] as the list draws them, oldest first, with the time line shown on [revealedKeys]. */
    @Composable
    fun Rows(
        rows: List<TimelineItem.MessageRow>,
        revealedKeys: Set<String> = emptySet(),
        callbacks: MessageRowCallbacks = callbacks(),
    ) {
        Column {
            rows.forEach { row ->
                MessageRowView(
                    row = row,
                    authorName = if (row.isMine) "Alex Rivera" else "Priya Raman",
                    time = time,
                    timestampVisible = row.key in revealedKeys,
                    highlighted = false,
                    callbacks = callbacks,
                    showAuthorLine = false,
                )
            }
        }
    }

    private const val SECONDS_PER_MINUTE = 60L
}
