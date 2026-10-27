package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/** Per-list context every row reads; one instance per list composition. */
@Immutable
data class TimelineContext(
    val time: RowTime,
    val names: Map<String, String>,
    val botName: String?,
    val visibleTimestamps: Set<String>,
    val highlightKey: String?,
    /** One-to-one chat: the header names the other person, so rows skip the author line. */
    val isDirect: Boolean = false,
)

/** Draws any [TimelineItem]; shared by the conversation and the thread. */
@Composable
fun TimelineItemView(
    item: TimelineItem,
    context: TimelineContext,
    callbacks: MessageRowCallbacks,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is TimelineItem.DaySeparator -> DaySeparatorView(
            label = DayLabel.of(item.day, ChatTime.day(context.time.now, context.time.zone)),
            modifier = modifier,
        )
        is TimelineItem.TimeSeparator -> TimeSeparatorView(at = item.at, zone = context.time.zone, modifier = modifier)
        is TimelineItem.MessageRow -> MessageRowView(
            row = item,
            authorName = authorNameFor(item.message, context.names, context.botName),
            showAuthorLine = !context.isDirect,
            time = context.time,
            timestampVisible = item.key in context.visibleTimestamps,
            highlighted = item.key == context.highlightKey,
            callbacks = callbacks,
            modifier = modifier,
        )
        is TimelineItem.SystemRow -> SystemRowView(text = item.text, modifier = modifier)
        is TimelineItem.CallLog -> CallLogCard(
            item = item,
            time = context.time,
            highlighted = item.key == context.highlightKey,
            modifier = modifier,
        )
    }
}
