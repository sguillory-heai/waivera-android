package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCountBadge
import com.waivera.waivera.core.designsystem.component.WaiveraGradientButton
import com.waivera.waivera.core.designsystem.component.WaiveraGradientButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.tint
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withNonBreakingHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.ActivitySummary
import com.waivera.waivera.feature.today.model.MessageSnippet
import com.waivera.waivera.feature.today.model.NeedsYouItem
import com.waivera.waivera.feature.today.model.PastLabel
import com.waivera.waivera.feature.today.model.QuotedSummary
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
        is NeedsYouItem.NotSent -> NotSentRow(item, { callbacks.onOpenConversation(item.conversationId) }, modifier)
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
        leading = {
            WaiveraAvatar(
                name = name,
                imageModel = AvatarModel.of(item.otherUserId),
                size = WaiveraSizes.CardTile,
                contentDescription = null,
            )
        },
        trailing = { WaiveraCountBadge(count = item.unreadCount) },
    )
}

@Composable
private fun NotSentRow(item: NeedsYouItem.NotSent, onClick: () -> Unit, modifier: Modifier) {
    val eyebrow = stringResource(R.string.feature_today_not_sent)
    val headline = stringResource(R.string.feature_today_not_sent_headline, item.name)
    val supporting = stringResource(R.string.feature_today_not_sent_supporting)
    TodayRow(
        headline = headline,
        description = stringResource(R.string.feature_today_not_sent_a11y, eyebrow, headline, supporting),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_not_sent_action),
        eyebrow = eyebrow,
        eyebrowColor = WaiveraTheme.colors.dangerText,
        supporting = supporting,
        emphasized = true,
        modifier = modifier,
        leading = { TodayTile(icon = WaiveraIcons.Error, tone = WaiveraTileTone.Danger) },
    )
}

/**
 * A mention, as concept A draws it: who mentioned you as the headline, then what they said as a
 * quote on its own line behind a rose rule. A summary not in the server's `{sender} mentioned
 * you: "{snippet}"` shape is shown whole as the quote.
 */
@Composable
private fun MentionRow(item: NeedsYouItem.Mention, onClick: () -> Unit, modifier: Modifier) {
    val formats = rememberTodayFormats()
    val summary = summaryText(item.summary)
    val parts = remember(summary) { QuotedSummary.of(summary) }
    val headline = parts.lead
        ?: item.actorName?.let { stringResource(R.string.feature_today_brand_mentioned_you, it) }
        ?: stringResource(R.string.feature_today_brand_mentioned_you_unknown)
    val kind = stringResource(R.string.feature_today_kind_mention)
    val rose = WaiveraTileTone.Rose.tint(WaiveraTheme.colors)
    TodayRow(
        headline = headline,
        description = stringResource(
            R.string.feature_today_activity_unread_prefix,
            joined(joined(kind, summary), item.at?.let { pastSpokenText(it, formats) }),
        ),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_message),
        eyebrow = joined(kind, item.at?.let { pastText(it, formats) }),
        eyebrowColor = rose.content,
        quote = parts.quote,
        quoteRuleColor = rose.content,
        emphasized = true,
        modifier = modifier,
        // A single mention has no count, so bold text and the "Unread" announcement are enough;
        // a dot on top would be a third unread signal (UI review UI-09).
        leading = { TodayTile(icon = R.drawable.feature_today_ic_mention, tone = WaiveraTileTone.Rose) },
    )
}

@Composable
private fun TicketRow(item: NeedsYouItem.Ticket, onClick: () -> Unit, modifier: Modifier) {
    val formats = rememberTodayFormats()
    val title = item.ticketTitle ?: stringResource(R.string.feature_today_ticket_title)
    val snippet = item.ticketId?.let { stringResource(R.string.feature_today_ticket_number, it) }
        ?: snippetText(item.snippet)
    // The key wraps as one word on screen ("Ticket #WAIV-" / "2527" read as two things).
    val visibleSnippet = item.ticketId?.let {
        stringResource(R.string.feature_today_ticket_number, it.withNonBreakingHyphens())
    } ?: snippet
    val unread = pluralStringResource(R.plurals.feature_today_unread_messages_a11y, item.unreadCount, item.unreadCount)
    TodayRow(
        headline = title,
        description = joined(
            stringResource(R.string.feature_today_dm_a11y, title, unread, snippet),
            item.at?.let { pastSpokenText(it, formats) },
        ),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_conversation),
        // The age leads: the key is one long word, so at 200% it would push the age past the
        // ellipsis ("Ticket #WAIV-2527 · 50…"); this way the age always shows (UI3-11).
        supporting = item.at?.let { joined(pastText(it, formats), visibleSnippet) } ?: visibleSnippet,
        emphasized = true,
        modifier = modifier,
        leading = { TodayTile(icon = R.drawable.feature_today_ic_ticket, tone = WaiveraTileTone.Amber) },
        trailing = { WaiveraCountBadge(count = item.unreadCount) },
    )
}

/**
 * A pending invitation with one-tap answers. The body opens the event; Decline (tonal) and
 * Accept (the gradient) are separate 48dp buttons inside the card so TalkBack reaches each on
 * its own.
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
    val violet = WaiveraTileTone.Brand.tint(WaiveraTheme.colors)
    val declineLabel = stringResource(R.string.feature_today_invitation_decline_a11y, title)
    val acceptLabel = stringResource(R.string.feature_today_invitation_accept_a11y, title)
    TodayRow(
        headline = title,
        description = joined(joined(eyebrow, title), details),
        onClick = onOpen,
        onClickLabel = stringResource(R.string.feature_today_open_event),
        eyebrow = eyebrow,
        eyebrowColor = violet.content,
        supporting = details,
        emphasized = true,
        showChevron = false,
        modifier = modifier,
        leading = { TodayTile(icon = WaiveraIcons.Calendar, tone = WaiveraTileTone.Brand) },
        below = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            ) {
                WaiveraButton(
                    text = stringResource(R.string.feature_today_invitation_decline),
                    onClick = { onRespond(false) },
                    variant = WaiveraButtonVariant.Tonal,
                    fullWidth = true,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = declineLabel },
                )
                WaiveraGradientButton(
                    text = stringResource(R.string.feature_today_invitation_accept),
                    onClick = { onRespond(true) },
                    size = WaiveraGradientButtonSize.Compact,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = acceptLabel },
                )
            }
        },
    )
}

@WaiveraPreviews
@Composable
private fun NeedsYouRowsPreview() {
    val callbacks = NeedsYouCallbacks({}, {}, {}, { _, _ -> })
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background).padding(vertical = WaiveraSpacing.Small)) {
            NeedsYouRow(NeedsYouItem.NotSent(conversationId = 7, name = "Priya Raman"), callbacks)
            NeedsYouRow(
                NeedsYouItem.Mention(
                    activityId = 1,
                    conversationId = 7,
                    messageId = 70,
                    threadRootMessageId = null,
                    actorName = "Priya Raman",
                    summary = ActivitySummary.Server("Priya Raman mentioned you: \"the Hughes visit moved to 10\""),
                    at = PastLabel.MinutesAgo(5),
                ),
                callbacks,
            )
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
                NeedsYouItem.Ticket(
                    conversationId = 14,
                    snippet = MessageSnippet.Text("Ticket updated"),
                    unreadCount = 1,
                    at = PastLabel.Yesterday,
                    ticketId = "4821",
                    ticketTitle = "Replace the van's back tire",
                ),
                callbacks,
            )
        }
    }
}
