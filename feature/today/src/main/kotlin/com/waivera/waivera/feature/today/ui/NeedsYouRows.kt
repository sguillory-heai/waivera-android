package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCountBadge
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.MessageSnippet
import com.waivera.waivera.feature.today.model.NeedsYouItem
import com.waivera.waivera.feature.today.model.PastLabel
import com.waivera.waivera.feature.today.model.UpcomingLabel
import java.time.LocalTime

/** Callbacks for the Needs you rows. Each row deep-links to its exact target. */
internal class NeedsYouCallbacks(
    val onOpenConversation: (conversationId: Long) -> Unit,
    val onOpenMention: (NeedsYouItem.Mention) -> Unit,
    val onOpenEvent: (eventId: String) -> Unit,
    val onRespond: (eventId: String, accept: Boolean) -> Unit,
)

@Composable
internal fun NeedsYouRow(item: NeedsYouItem, callbacks: NeedsYouCallbacks, modifier: Modifier = Modifier) {
    when (item) {
        is NeedsYouItem.DirectMessage ->
            DirectMessageRow(item, { callbacks.onOpenConversation(item.conversationId) }, modifier)
        is NeedsYouItem.Mention -> MentionRow(item, { callbacks.onOpenMention(item) }, modifier)
        is NeedsYouItem.Invitation -> InvitationRow(
            item = item,
            onOpen = { callbacks.onOpenEvent(item.eventId) },
            onRespond = { accept -> callbacks.onRespond(item.eventId, accept) },
            modifier = modifier,
        )
        is NeedsYouItem.Ticket -> TicketRow(item, { callbacks.onOpenConversation(item.conversationId) }, modifier)
    }
}

@Composable
private fun DirectMessageRow(item: NeedsYouItem.DirectMessage, onClick: () -> Unit, modifier: Modifier) {
    val formats = rememberTodayFormats()
    val name = item.name ?: stringResource(R.string.feature_today_dm_fallback_name)
    val snippet = snippetText(item.snippet)
    val unread = pluralStringResource(R.plurals.feature_today_unread_messages_a11y, item.unreadCount, item.unreadCount)
    val spokenTime = item.at?.let { pastSpokenText(it, formats) }
    TodayRow(
        headline = name,
        description = joined(stringResource(R.string.feature_today_dm_a11y, name, unread, snippet), spokenTime),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_conversation),
        supporting = joined(snippet, item.at?.let { pastText(it, formats) }),
        emphasized = true,
        modifier = modifier,
        leading = { WaiveraAvatar(name = name, size = WaiveraSizes.IconTile, contentDescription = null) },
        trailing = { WaiveraCountBadge(count = item.unreadCount) },
    )
}

@Composable
private fun MentionRow(item: NeedsYouItem.Mention, onClick: () -> Unit, modifier: Modifier) {
    val formats = rememberTodayFormats()
    val summary = summaryText(item.summary)
    val kind = stringResource(R.string.feature_today_kind_mention)
    TodayRow(
        headline = summary,
        description = stringResource(
            R.string.feature_today_activity_unread_prefix,
            joined(joined(kind, summary), item.at?.let { pastSpokenText(it, formats) }),
        ),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_message),
        supporting = joined(kind, item.at?.let { pastText(it, formats) }),
        emphasized = true,
        modifier = modifier,
        leading = { TodayIconTile(icon = R.drawable.feature_today_ic_mention) },
        trailing = { UnreadDot() },
    )
}

@Composable
private fun TicketRow(item: NeedsYouItem.Ticket, onClick: () -> Unit, modifier: Modifier) {
    val formats = rememberTodayFormats()
    val title = stringResource(R.string.feature_today_ticket_title)
    val snippet = snippetText(item.snippet)
    val unread = pluralStringResource(R.plurals.feature_today_unread_messages_a11y, item.unreadCount, item.unreadCount)
    TodayRow(
        headline = title,
        description = joined(
            stringResource(R.string.feature_today_dm_a11y, title, unread, snippet),
            item.at?.let { pastSpokenText(it, formats) },
        ),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_conversation),
        supporting = joined(snippet, item.at?.let { pastText(it, formats) }),
        emphasized = true,
        modifier = modifier,
        leading = { TodayIconTile(icon = R.drawable.feature_today_ic_ticket) },
        trailing = { WaiveraCountBadge(count = item.unreadCount) },
    )
}

/**
 * A pending invitation with one-tap answers. The body opens the event; Accept and Decline are
 * separate 48dp buttons so TalkBack reaches each on its own.
 */
@Composable
private fun InvitationRow(
    item: NeedsYouItem.Invitation,
    onOpen: () -> Unit,
    onRespond: (accept: Boolean) -> Unit,
    modifier: Modifier,
) {
    val formats = rememberTodayFormats()
    val title = item.title ?: stringResource(R.string.feature_today_invitation_untitled)
    val time = item.time?.let { upcomingText(it, formats) }
    val organizer = item.organizer?.let { stringResource(R.string.feature_today_invitation_from, it) }
    val eyebrow = stringResource(R.string.feature_today_invitation_label)
    val details = if (time != null && organizer != null) joined(time, organizer) else time ?: organizer
    Column(modifier = modifier.fillMaxWidth()) {
        TodayRow(
            headline = title,
            description = joined(joined(eyebrow, title), details),
            onClick = onOpen,
            onClickLabel = stringResource(R.string.feature_today_open_event),
            eyebrow = eyebrow,
            eyebrowColor = WaiveraTheme.colors.accent,
            supporting = details,
            emphasized = true,
            leading = { TodayIconTile(icon = WaiveraIcons.Calendar) },
        )
        val declineLabel = stringResource(R.string.feature_today_invitation_decline_a11y, title)
        val acceptLabel = stringResource(R.string.feature_today_invitation_accept_a11y, title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = WaiveraSpacing.Gutter + WaiveraSizes.IconTile + WaiveraSpacing.MediumSmall,
                    end = WaiveraSpacing.Gutter,
                    bottom = WaiveraSpacing.MediumSmall,
                ),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraButton(
                text = stringResource(R.string.feature_today_invitation_decline),
                onClick = { onRespond(false) },
                variant = WaiveraButtonVariant.Secondary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = declineLabel },
            )
            WaiveraButton(
                text = stringResource(R.string.feature_today_invitation_accept),
                onClick = { onRespond(true) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = acceptLabel },
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun NeedsYouRowsPreview() {
    val callbacks = NeedsYouCallbacks({}, {}, {}, { _, _ -> })
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            NeedsYouRow(
                NeedsYouItem.DirectMessage(
                    conversationId = 7,
                    name = "Priya Raman",
                    snippet = MessageSnippet.Text("Can you cover Thursday?"),
                    unreadCount = 2,
                    at = PastLabel.MinutesAgo(5),
                ),
                callbacks,
            )
            NeedsYouRow(
                NeedsYouItem.Invitation(
                    eventId = "e1",
                    title = "Care plan review",
                    organizer = "Priya Raman",
                    time = UpcomingLabel.TomorrowAt(LocalTime.of(9, 0)),
                ),
                callbacks,
            )
            NeedsYouRow(
                NeedsYouItem.Ticket(14, MessageSnippet.Text("Ticket updated"), 1, PastLabel.Yesterday),
                callbacks,
            )
        }
    }
}
