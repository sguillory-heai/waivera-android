package com.waivera.waivera.feature.chat.message

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.text.withNonBreakingHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

// Reactions ---------------------------------------------------------------------------------

/**
 * Reaction pills inside the bubble (03 §13). Tap toggles mine, long press opens the details.
 * When [onToggle] is null (read-only) the pills are inert. Each pill announces emoji, count and
 * whether I reacted, never colour alone (10 §7.4).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPills(
    reactions: List<ReactionSummary>,
    alignEnd: Boolean,
    onToggle: ((String) -> Unit)?,
    onDetails: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            WaiveraSpacing.ExtraSmall,
            if (alignEnd) Alignment.End else Alignment.Start,
        ),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
    ) {
        reactions.forEach { reaction -> ReactionPill(reaction, onToggle, onDetails) }
    }
}

@Composable
private fun ReactionPill(reaction: ReactionSummary, onToggle: ((String) -> Unit)?, onDetails: ((String) -> Unit)?) {
    val colors = WaiveraTheme.colors
    val label = if (reaction.mine) {
        stringResource(R.string.feature_chat_reaction_label_mine, reaction.emoji, reaction.count)
    } else {
        stringResource(R.string.feature_chat_reaction_label, reaction.emoji, reaction.count)
    }
    val content = @Composable {
        Row(
            modifier = Modifier
                .heightIn(min = ChatDimens.ReactionPillHeight)
                .background(if (reaction.mine) colors.accentSoft else colors.background, WaiveraShapes.Pill)
                .border(
                    if (reaction.mine) WaiveraSizes.SelectedOutline else WaiveraSpacing.Hairline,
                    if (reaction.mine) colors.accent else colors.border,
                    WaiveraShapes.Pill,
                )
                .padding(horizontal = WaiveraSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = reaction.emoji, style = MaterialTheme.typography.bodySmall)
            // Always shown, 1 included (UI2-28): a bare emoji reads as decoration, not a count.
            Text(
                text = reaction.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (reaction.mine) FontWeight.SemiBold else null,
                color = if (reaction.mine) colors.onAccentSoft else colors.textSecondary,
            )
        }
    }
    if (onToggle == null) {
        Box(Modifier.clearAndSetSemantics { contentDescription = label }) { content() }
    } else {
        val toggleLabel = stringResource(R.string.feature_chat_reaction_toggle)
        val detailsLabel = stringResource(R.string.feature_chat_reaction_details)
        val toggle = { onToggle(reaction.emoji) }
        val details = onDetails?.let { { it(reaction.emoji) } }
        WaiveraPressable(
            onClick = toggle,
            onClickLabel = toggleLabel,
            onLongClick = details,
            onLongClickLabel = detailsLabel,
            shape = WaiveraShapes.Pill,
            // The pill is one spoken label ("thumbs up, 3, you reacted"), so its children are
            // cleared. Clearing also drops the clickable's own actions (this modifier comes
            // first), so tap and long press are set again here for TalkBack.
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clearAndSetSemantics {
                    contentDescription = label
                    role = Role.Button
                    onClick(label = toggleLabel) {
                        toggle()
                        true
                    }
                    if (details != null) {
                        onLongClick(label = detailsLabel) {
                            details()
                            true
                        }
                    }
                },
        ) { Box(Modifier.align(Alignment.Center)) { content() } }
    }
}

// Attachments -------------------------------------------------------------------------------

@Composable
fun MessageAttachmentView(message: Message, attachment: Attachment, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    when {
        attachment.isBlocked -> AttachmentNotice(
            R.string.feature_chat_attachment_blocked,
            WaiveraIcons.Warning,
            modifier,
        )
        attachment.isPending && !message.isPending ->
            AttachmentNotice(R.string.feature_chat_attachment_scanning, WaiveraIcons.Info, modifier)
        Attachments.isImage(attachment) -> ImageAttachment(message, onOpen, modifier)
        else -> FileAttachment(message, attachment, onOpen, modifier)
    }
    if (message.isPending) UploadState(message.sendState, message.uploadProgress)
}

/** Determinate while the outbox reports how much has gone up (03 §8), indeterminate before that. */
@Composable
private fun UploadState(state: SendState, progress: Float?) {
    if (state == SendState.FAILED) {
        Text(
            text = stringResource(R.string.feature_chat_upload_failed),
            style = MaterialTheme.typography.labelMedium,
            color = WaiveraTheme.colors.dangerText,
        )
    } else {
        Column(
            modifier = Modifier.width(ChatDimens.ImageWidth),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
        ) {
            // Under reduced motion the indeterminate bar would freeze mid-sweep; an empty track
            // with the "Uploading…" label below says the same without looking stuck.
            val reduced = WaiveraTheme.motion.reduced
            val shownProgress = progress ?: if (reduced) 0f else null
            if (shownProgress != null) {
                LinearProgressIndicator(
                    progress = { shownProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = WaiveraTheme.colors.accent,
                    trackColor = WaiveraTheme.colors.surfaceMuted,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = WaiveraTheme.colors.accent,
                    trackColor = WaiveraTheme.colors.surfaceMuted,
                )
            }
            Text(
                text = if (progress != null) {
                    stringResource(R.string.feature_chat_uploading_percent, (progress * PERCENT).roundToInt())
                } else {
                    stringResource(R.string.feature_chat_uploading)
                },
                style = MaterialTheme.typography.labelMedium,
                color = WaiveraTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ImageAttachment(message: Message, onOpen: () -> Unit, modifier: Modifier) {
    val colors = WaiveraTheme.colors
    val maxPx = with(LocalDensity.current) { ChatDimens.ImageWidth.roundToPx() * 2 }
    val model = LocalChatImages.current.attachmentModel(message)
    when (val state = rememberChatImage(model, maxPx)) {
        ChatImageState.Loading -> SkeletonBlock(
            modifier = modifier
                .width(ChatDimens.ImageWidth)
                .height(ChatDimens.ImageLoadingHeight),
            shape = ChatDimens.ImageShape,
        )
        ChatImageState.Unavailable -> AttachmentNotice(
            R.string.feature_chat_image_unavailable,
            ChatIcons.Image,
            modifier,
        )
        is ChatImageState.Ready -> WaiveraPressable(
            onClick = onOpen,
            onClickLabel = stringResource(R.string.feature_chat_open_image),
            shape = ChatDimens.ImageShape,
            modifier = modifier.semantics { contentDescription = message.attachment?.originalName ?: "" },
        ) {
            Image(
                bitmap = state.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ChatDimens.ImageWidth, ChatDimens.ImageHeight)
                    .clip(ChatDimens.ImageShape)
                    .border(WaiveraSpacing.Hairline, colors.border, ChatDimens.ImageShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WaiveraSpacing.Small)
                    .size(ChatDimens.ImageBadge)
                    .background(colors.scrim, WaiveraShapes.Chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(ChatIcons.Maximize),
                    contentDescription = null,
                    tint = onScrimColor(),
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
            }
        }
    }
}

@Composable
private fun AttachmentNotice(text: Int, icon: Int, modifier: Modifier) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = modifier
            .widthIn(max = ChatDimens.FileCardWidth)
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .background(colors.surfaceMuted, WaiveraShapes.Card)
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
        Text(text = stringResource(text), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun FileAttachment(message: Message, attachment: Attachment, onOpen: () -> Unit, modifier: Modifier) {
    val colors = WaiveraTheme.colors
    val name = Attachments.fileName(attachment, message.id)
    val icon = when (Attachments.kind(attachment, message.id)) {
        FileKind.Spreadsheet -> ChatIcons.FileSheet
        FileKind.Document -> ChatIcons.FileText
        FileKind.Other -> ChatIcons.File
    }
    WaiveraPressable(
        onClick = onOpen,
        enabled = !message.isPending,
        onClickLabel = stringResource(R.string.feature_chat_file_caption),
        shape = WaiveraShapes.Card,
        modifier = modifier.widthIn(max = ChatDimens.FileCardWidth),
    ) {
        Row(
            modifier = Modifier
                .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Card)
                .background(colors.surface, WaiveraShapes.Card)
                .padding(WaiveraSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(WaiveraSizes.IconTile)
                    .background(colors.surfaceMuted, WaiveraShapes.Chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.feature_chat_file_caption),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
            Icon(
                painterResource(ChatIcons.Download),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
        }
    }
}

// Ticket card -------------------------------------------------------------------------------

@Composable
fun TicketCard(ticket: TicketNotification, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
            WaiveraStatusBadge(text = ticket.type, tone = BadgeTone.Accent)
            WaiveraStatusBadge(
                text = ticket.priority,
                tone = when (ticket.priorityTone) {
                    TicketPriority.Critical -> BadgeTone.Danger
                    TicketPriority.High, TicketPriority.Medium -> BadgeTone.Warning
                    TicketPriority.Other -> BadgeTone.Neutral
                },
            )
        }
        Text(
            text = ticket.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = ticket.ticketId.withNonBreakingHyphens(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            modifier = Modifier.semantics { contentDescription = ticket.ticketId },
        )
        WaiveraButton(
            text = stringResource(R.string.feature_chat_ticket_open),
            onClick = { onOpen(ticket.url) },
            variant = WaiveraButtonVariant.Secondary,
            leadingIcon = WaiveraIcons.OpenExternal,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Card),
        ) {
            TicketMeta(
                WaiveraIcons.Person,
                stringResource(R.string.feature_chat_ticket_created_by),
                ticket.createdBy ?: stringResource(R.string.feature_chat_ticket_unknown_user),
                muted = false,
            )
            WaiveraDivider()
            TicketMeta(
                WaiveraIcons.Calendar,
                stringResource(R.string.feature_chat_ticket_created),
                ticket.created.format() ?: stringResource(R.string.feature_chat_ticket_not_set),
                muted = ticket.created == TicketDate.NotSet,
            )
            WaiveraDivider()
            TicketMeta(
                WaiveraIcons.Calendar,
                stringResource(R.string.feature_chat_ticket_due),
                ticket.due.format() ?: stringResource(R.string.feature_chat_ticket_not_set),
                muted = ticket.due == TicketDate.NotSet,
            )
        }
    }
}

/**
 * Label and value side by side; at large font the value goes under the label, because an
 * unweighted value measured first would squeeze the label into mid-word breaks.
 */
@Composable
private fun TicketMeta(icon: Int, label: String, value: String, muted: Boolean) {
    val colors = WaiveraTheme.colors
    val stacked = LocalDensity.current.fontScale > TICKET_META_STACK_FONT_SCALE
    val valueColor = if (muted) colors.textSecondary else colors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = if (stacked) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(WaiveraSizes.IconSmall),
        )
        if (stacked) {
            Column(Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                Text(text = value, style = MaterialTheme.typography.labelMedium, color = valueColor)
            }
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = valueColor,
                textAlign = TextAlign.End,
            )
        }
    }
}

private const val TICKET_META_STACK_FONT_SCALE = 1.3f

// System rows, call logs and day separators ---------------------------------------------------

@Composable
fun SystemRowView(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
        color = WaiveraTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = WaiveraSpacing.MediumLarge,
                vertical =
                WaiveraSpacing.MediumSmall - WaiveraSpacing.XxSmall,
            ),
    )
}

@Composable
fun DaySeparatorView(label: DayLabel, modifier: Modifier = Modifier) {
    val text = when (label) {
        DayLabel.Today -> stringResource(R.string.feature_chat_day_today)
        DayLabel.Yesterday -> stringResource(R.string.feature_chat_day_yesterday)
        is DayLabel.OnDate -> ChatTime.shortDate(label.date, showYear = label.showYear)
    }
    SeparatorRule(text = text, heading = true, modifier = modifier)
}

/**
 * The clock line after an hour's pause inside a day ([TimelineItem.TimeSeparator]). It uses the
 * day separator's centred rule, so at 200% it can't be mistaken for the previous bubble's
 * footer (UI3-04).
 */
@Composable
fun TimeSeparatorView(at: Instant, zone: ZoneId, modifier: Modifier = Modifier) {
    val text = remember(at, zone) { ChatTime.clock(at, zone) }
    SeparatorRule(text = text, heading = false, modifier = modifier)
}

/** A label centred between two hairlines; day lines are TalkBack headings, clock lines are not. */
@Composable
private fun SeparatorRule(text: String, heading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall)
            .semantics(mergeDescendants = true) { if (heading) heading() },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaiveraDivider(Modifier.weight(1f))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = WaiveraTheme.colors.textSecondary)
        WaiveraDivider(Modifier.weight(1f))
    }
}

@Composable
fun CallLogCard(item: TimelineItem.CallLog, time: RowTime, highlighted: Boolean, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (expanded) HALF_TURN else 0f,
        WaiveraTheme.motion.layout(),
        label = "chevron",
    )
    val summary = item.summary
    val label = stringResource(
        when (summary.outcome) {
            CallOutcome.Declined -> R.string.feature_chat_call_declined
            CallOutcome.Missed -> R.string.feature_chat_call_missed
            CallOutcome.Ended -> R.string.feature_chat_call_ended
            CallOutcome.InProgress -> R.string.feature_chat_call_live
        },
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
            .background(if (highlighted) colors.accentWash else colors.surface, WaiveraShapes.ListCard)
            .border(
                WaiveraSpacing.Hairline,
                if (highlighted) colors.accentBorderStrong else colors.border,
                WaiveraShapes.ListCard,
            ),
    ) {
        WaiveraPressable(
            onClick = { expanded = !expanded },
            onClickLabel = stringResource(
                if (expanded) R.string.feature_chat_call_collapse else R.string.feature_chat_call_expand,
            ),
            shape = WaiveraShapes.ListCard,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = WaiveraSizes.ListRowMinHeight)
                    .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(WaiveraSizes.IconTile)
                        .background(
                            if (summary.isMissed) colors.dangerContainer else colors.accentWash,
                            WaiveraShapes.Chip,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (summary.isMissed) ChatIcons.PhoneMissed else WaiveraIcons.Video),
                        contentDescription = null,
                        tint = if (summary.isMissed) colors.onDangerContainer else colors.accent,
                        modifier = Modifier.size(WaiveraSizes.Icon),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.titleSmall, color = colors.text)
                    summary.duration?.let {
                        Text(text = it, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    }
                }
                summary.time?.let {
                    Text(
                        text = ChatTime.clock(it, time.zone),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
                Icon(
                    painterResource(ChatIcons.ChevronDown),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(WaiveraSizes.Icon)
                        .rotate(rotation),
                )
            }
        }
        if (expanded) {
            WaiveraDivider()
            Column(Modifier.padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small)) {
                item.messages.forEach { message ->
                    val parsed = SystemMessages.parse(message.text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = WaiveraSpacing.ExtraSmall)
                            .semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                    ) {
                        Text(
                            text = parsed.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        (parsed.embeddedTimestamp ?: message.createdAt)?.let {
                            Text(
                                text = ChatTime.clock(it, time.zone),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val HALF_TURN = 180f

private const val PERCENT = 100
