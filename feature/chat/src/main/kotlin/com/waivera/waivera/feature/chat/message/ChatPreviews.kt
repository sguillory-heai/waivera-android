package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.dto.MessageType
import java.time.LocalDate

/*
 * Previews for the shared message pieces (light, dark and 200% font each). Sample copy is
 * preview data only; screens read every user-facing string from resources.
 */

@WaiveraPreviews
@Composable
private fun TimelineRowsPreview() {
    val context = TimelineContext(ChatPreviewData.time, ChatPreviewData.names, null, emptySet(), highlightKey = null)
    WaiveraTheme {
        Column {
            ChatPreviewData.items().forEach { item -> TimelineItemView(item, context, MessageRowCallbacks.None) }
        }
    }
}

@WaiveraPreviews
@Composable
private fun MessageStatesPreview() {
    val rows = ChatPreviewData.items().filterIsInstance<TimelineItem.MessageRow>()
    WaiveraTheme {
        Column {
            MessageRowView(
                row = rows.first().copy(message = rows.first().message.copy(isDeleted = true, text = null)),
                authorName = "Priya Shah",
                time = ChatPreviewData.time,
                timestampVisible = true,
                highlighted = false,
                callbacks = MessageRowCallbacks.None,
            )
            MessageRowView(
                row = rows[1],
                authorName = "Priya Shah",
                time = ChatPreviewData.time,
                timestampVisible = false,
                highlighted = true,
                callbacks = MessageRowCallbacks.None,
            )
            ReactionPills(
                reactions = listOf(ReactionSummary("👍", 2, mine = true, userIds = listOf("a", "b"))),
                alignEnd = false,
                onToggle = {},
                onDetails = {},
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun TicketCardPreview() {
    WaiveraTheme {
        TicketCard(
            ticket = TicketNotification(
                ticketId = "WAIV-2527",
                title = "Update care plan goals",
                url = "https://app.example.test/tickets/WAIV-2527",
                type = "Task",
                createdBy = "Priya Raman",
                priority = "HIGH",
                created = TicketDate.OnDate(LocalDate.of(2026, 10, 30)),
                due = TicketDate.NotSet,
            ),
            onOpen = {},
        )
    }
}

@WaiveraPreviews
@Composable
private fun CallAndSystemRowsPreview() {
    val started = Message(
        id = 1,
        conversationId = 1,
        type = MessageType.SYSTEM,
        text = "Video call started",
        createdAt = ChatPreviewData.now.minusSeconds(900),
        callRecordId = 5,
    )
    val ended = started.copy(
        id = 2,
        text = "Video call ended in 12m 4s",
        createdAt = ChatPreviewData.now.minusSeconds(176),
    )
    val call = TimelineItem.CallLog("call-5", 5, listOf(started, ended), CallLogs.summarize(listOf(started, ended)))
    WaiveraTheme {
        Column {
            DaySeparatorView(label = DayLabel.Today)
            CallLogCard(item = call, time = ChatPreviewData.time, highlighted = false)
            SystemRowView(text = "Jordan Lee joined the channel")
        }
    }
}

@WaiveraPreviews
@Composable
private fun ListChromePreview() {
    WaiveraTheme {
        Column {
            OlderMessagesRow(failed = true, onRetry = {})
            OlderMessagesRow(failed = false, onRetry = {})
            JumpToLatestPill(onClick = {})
            ReadOnlyNotice(reason = ReadOnlyReason.ReadOnlyChannel, isThread = true)
            DelayedListSkeleton(label = "Loading messages", delayMillis = 0)
        }
    }
}

@WaiveraPreviews
@Composable
private fun MessageFocusPreview() {
    val message = ChatPreviewData.messages[2]
    WaiveraTheme {
        Box(Modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
            MessageFocusLayer(
                model = ChatActions.model(message, ChatPreviewData.ME, canWrite = true, allowReply = true),
                mine = message.isMine(ChatPreviewData.ME),
                actions = MessageFocusActions.None,
            )
        }
    }
}
