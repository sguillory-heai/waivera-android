package com.waivera.waivera.feature.chat.message

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.data.model.Attachment
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.popScale
import com.waivera.waivera.core.designsystem.component.rememberPopScale
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.text.fileNameHeadAndTail
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.text.withNonBreakingHyphens
import com.waivera.waivera.core.designsystem.text.withUnbreakableExtension
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.chat.R
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

// Reactions and replies ----------------------------------------------------------------------

/**
 * The engagement row under a bubble, as on the web: reaction chips first, then "N replies", side
 * by side, wrapping to another line when they do not fit, and lined up with the bubble's side
 * ([alignEnd] for mine). Every chip is a soft rounded square ([WaiveraShapes.Chip]) sized from its
 * text, so it grows evenly with the font instead of clipping or ballooning.
 *
 * A reaction chip toggles mine on tap and opens the details on long press (03 §13); when
 * [onToggle] is null (read-only) the chips are inert. Each one announces emoji, count and whether
 * I reacted, never colour alone (10 §7.4). The replies chip shows only with [onOpenThread] and a
 * [replyCount] above 0.
 *
 * The chips are drawn at their own size and take their 48dp touch target from Compose's minimum
 * touch target expansion (ViewConfiguration.minimumTouchTargetSize), so the target never pushes
 * the row away from the bubble.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageEngagementRow(
    reactions: List<ReactionSummary>,
    alignEnd: Boolean,
    onToggle: ((String) -> Unit)?,
    onDetails: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
    replyCount: Int = 0,
    onOpenThread: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            ChatDimens.EngagementGap,
            if (alignEnd) Alignment.End else Alignment.Start,
        ),
        verticalArrangement = Arrangement.spacedBy(ChatDimens.EngagementGap),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.forEach { reaction -> ReactionChip(reaction, onToggle, onDetails) }
        if (onOpenThread != null && replyCount > 0) RepliesChip(count = replyCount, onClick = onOpenThread)
    }
}

/** The inside of every engagement chip: padded from its text, on the chip shape. */
@Composable
private fun EngagementChipBody(
    fill: Color,
    outline: Color,
    outlineWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .background(fill, WaiveraShapes.Chip)
            .border(outlineWidth, outline, WaiveraShapes.Chip)
            .padding(horizontal = ChatDimens.EngagementChipPaddingX, vertical = ChatDimens.EngagementChipPaddingY),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ReactionChip(reaction: ReactionSummary, onToggle: ((String) -> Unit)?, onDetails: ((String) -> Unit)?) {
    val colors = WaiveraTheme.colors
    val label = if (reaction.mine) {
        stringResource(R.string.feature_chat_reaction_label_mine, reaction.emoji, reaction.count)
    } else {
        stringResource(R.string.feature_chat_reaction_label, reaction.emoji, reaction.count)
    }
    // Adding or taking back a reaction, or someone else's landing, pops the chip.
    val pop = rememberPopScale(key = reaction.count to reaction.mine)
    val content = @Composable {
        EngagementChipBody(
            fill = if (reaction.mine) colors.accentSoft else colors.surface,
            outline = if (reaction.mine) colors.accent else colors.border,
            outlineWidth = if (reaction.mine) WaiveraSizes.SelectedOutline else WaiveraSpacing.Hairline,
            modifier = Modifier.popScale(pop),
        ) {
            Text(text = reaction.emoji, style = MaterialTheme.typography.bodySmall)
            // Always shown, 1 included (UI2-28): a bare emoji reads as decoration, not a count.
            Text(
                text = reaction.count.toString(),
                style = MaterialTheme.typography.labelMedium,
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
            shape = WaiveraShapes.Chip,
            // The chip is one spoken label ("thumbs up, 3, you reacted"), so its children are
            // cleared. Clearing also drops the clickable's own actions (this modifier comes
            // first), so tap and long press are set again here for TalkBack.
            modifier = Modifier.clearAndSetSemantics {
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
        ) { content() }
    }
}

/** "1 reply" / "4 replies": opens the thread. The reply icon is decoration; the text is the label. */
@Composable
private fun RepliesChip(count: Int, onClick: () -> Unit) {
    val colors = WaiveraTheme.colors
    WaiveraPressable(
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_chat_open_thread),
        shape = WaiveraShapes.Chip,
    ) {
        EngagementChipBody(
            fill = colors.accentWash,
            outline = colors.accentBorder,
            outlineWidth = WaiveraSpacing.Hairline,
        ) {
            Icon(
                painterResource(WaiveraIcons.Reply),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
            Text(
                text = pluralStringResource(R.plurals.feature_chat_thread_replies, count, count),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )
        }
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
            depth = PressDepth.Card,
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
        depth = PressDepth.Card,
        onClick = onOpen,
        enabled = !message.isPending,
        onClickLabel = stringResource(R.string.feature_chat_file_caption),
        shape = WaiveraShapes.Card,
        modifier = modifier.widthIn(max = ChatDimens.FileCardWidth),
    ) {
        val card = Modifier
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Card)
            .background(colors.surface, WaiveraShapes.Card)
            .padding(WaiveraSpacing.Small)
        // At large font the helper line would wrap into a column of words; the name gets the
        // room instead, and the tap's label still says what it does (UI3-11). At 100% the name
        // is one line cut in the middle, so the extension stays visible ("north-ros…wk43.pdf",
        // UI4-08). At large font one line left almost none of it ("north…3.pdf", UI5-11), and
        // two wrapping lines with an end ellipsis dropped the week number and the extension
        // ("north- / roster-wk4…", UI6-06): see FileCardName. Beside the tile and the download
        // glyph, concept A's wider face then cut even the tail ("wk43.pdf"), so at large font
        // the tile and glyph share a top row and the name takes the card's whole width.
        if (isLargeFontScale()) {
            Column(modifier = card, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaiveraIconTile(icon = icon, tone = WaiveraTileTone.Neutral)
                    DownloadGlyph()
                }
                FileCardName(name = name, color = colors.text)
            }
        } else {
            Row(
                modifier = card,
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraIconTile(icon = icon, tone = WaiveraTileTone.Neutral)
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                    Text(
                        text = stringResource(R.string.feature_chat_file_caption),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
                DownloadGlyph()
            }
        }
    }
}

@Composable
private fun DownloadGlyph() {
    Icon(
        painterResource(ChatIcons.Download),
        contentDescription = null,
        tint = WaiveraTheme.colors.textSecondary,
        modifier = Modifier.size(WaiveraSizes.Icon),
    )
}

/**
 * The file card's name at large font: the head of the name, then the tail with the extension
 * ("wk43.pdf"), which is never cut while it fits a line. The head wraps after its hyphens onto
 * up to [FILE_NAME_HEAD_MAX_LINES] lines when every piece fits a line ("north-" / "roster-"),
 * and only otherwise is it one line cut in the middle ("nor…ter-"), so it keeps every letter it
 * has room for (UI7-05). The head and tail share a line when they fit together. The description
 * gives TalkBack the whole name in one piece instead of the parts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FileCardName(name: String, color: Color, modifier: Modifier = Modifier) {
    val (head, tail) = remember(name) { name.fileNameHeadAndTail() }
    val style = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.semantics { contentDescription = name }) {
        val maxWidth = constraints.maxWidth
        val wrappedHead = remember(head) { head.withBreaksAfterHyphens() }
        val headWraps = remember(wrappedHead, style, maxWidth, measurer) {
            head.isNotEmpty() &&
                measurer.measure(
                    text = wrappedHead,
                    style = style,
                    maxLines = FILE_NAME_HEAD_MAX_LINES,
                    constraints = Constraints(maxWidth = maxWidth),
                ).wrapsOnlyAtBreaks()
        }
        FlowRow {
            if (headWraps) {
                Text(
                    text = wrappedHead,
                    style = style,
                    color = color,
                    maxLines = FILE_NAME_HEAD_MAX_LINES,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.testTag(FILE_NAME_HEAD_TAG),
                )
            } else if (head.isNotEmpty()) {
                Text(
                    text = head,
                    style = style,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.testTag(FILE_NAME_HEAD_TAG),
                )
            }
            Text(
                text = tail.withUnbreakableExtension(),
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.testTag(FILE_NAME_TAIL_TAG),
            )
        }
    }
}

/** Whether the text fits its lines whole, with every line but the last ending at a break point. */
private fun TextLayoutResult.wrapsOnlyAtBreaks(): Boolean {
    if (hasVisualOverflow) return false
    val text = layoutInput.text.text
    return (0 until lineCount - 1).all { line ->
        val end = getLineEnd(line, visibleEnd = false)
        end > 0 && text[end - 1] == BREAK_AFTER_HYPHEN
    }
}

/** What `withBreaksAfterHyphens` puts after a hyphen inside a name. */
private const val BREAK_AFTER_HYPHEN = '\u200B'
private const val FILE_NAME_HEAD_MAX_LINES = 2

/** Test tag for the head of the file card's name at large font. */
internal const val FILE_NAME_HEAD_TAG = "chat_file_name_head"

/** Test tag for the never-cut tail of the file card's name. */
internal const val FILE_NAME_TAIL_TAG = "chat_file_name_tail"

// Ticket card -------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketCard(ticket: TicketNotification, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
        // The badges wrap, so a long type at large font never pushes the priority off the card.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        ) {
            WaiveraStatusBadge(text = ticket.type, tone = BadgeTone.Accent, overflow = TextOverflow.Ellipsis)
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
 * Label and value side by side, the label always at its full one-line width and a long value
 * wrapping beside it. When the label would leave the value less than
 * [TICKET_META_VALUE_MIN_FRACTION] of the row, and at large font, the value goes under the label
 * instead: an unweighted value measured first used to squeeze the label into one letter per line.
 */
@Composable
private fun TicketMeta(icon: Int, label: String, value: String, muted: Boolean) {
    val colors = WaiveraTheme.colors
    val style = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val largeFont = density.fontScale > TICKET_META_STACK_FONT_SCALE
    val valueColor = if (muted) colors.textSecondary else colors.text
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val maxWidth = constraints.maxWidth
        val valueMaxWidth = remember(label, value, style, maxWidth, largeFont, density, measurer) {
            if (largeFont) {
                null
            } else {
                with(density) {
                    ticketMetaValueMaxWidth(
                        pairWidth = maxWidth - 2 * WaiveraSpacing.MediumSmall.roundToPx() -
                            WaiveraSizes.IconSmall.roundToPx() - 2 * WaiveraSpacing.Small.roundToPx(),
                        gap = WaiveraSpacing.Small.roundToPx(),
                        labelWidth = measurer.measure(text = label, style = style).size.width,
                        valueWidth = measurer.measure(text = value, style = style).size.width,
                    )
                }
            }
        }
        val stacked = valueMaxWidth == null
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
            if (valueMaxWidth == null) {
                Column(Modifier.weight(1f)) {
                    Text(text = label, style = style, color = colors.textSecondary)
                    Text(text = value, style = style, color = valueColor)
                }
            } else {
                Text(
                    text = label,
                    style = style,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                // Capped so the label keeps its width; a value that fits is narrower than the cap.
                Text(
                    text = value,
                    style = style,
                    color = valueColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = with(density) { valueMaxWidth.toDp() }),
                )
            }
        }
    }
}

/**
 * The widest a ticket meta value may be beside a label, in pixels, for a label and value of
 * these one-line widths in [pairWidth] with [gap] between them; null when the row should stack.
 * Side by side, the value either fits whole beside the label or wraps in the room the label
 * leaves, as long as that room is at least [TICKET_META_VALUE_MIN_FRACTION] of the row.
 */
internal fun ticketMetaValueMaxWidth(pairWidth: Int, gap: Int, labelWidth: Int, valueWidth: Int): Int? {
    val room = pairWidth - gap - labelWidth
    val fits = valueWidth <= room
    return if (fits || room >= pairWidth * TICKET_META_VALUE_MIN_FRACTION) room else null
}

private const val TICKET_META_STACK_FONT_SCALE = 1.3f
internal const val TICKET_META_VALUE_MIN_FRACTION = 0.5f

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
            depth = PressDepth.Card,
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
                WaiveraIconTile(
                    icon = if (summary.isMissed) ChatIcons.PhoneMissed else WaiveraIcons.Video,
                    tone = if (summary.isMissed) WaiveraTileTone.Danger else WaiveraTileTone.Brand,
                )
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
                        // Read in the layer, so the turn never recomposes the conversation's rows.
                        .graphicsLayer { rotationZ = rotation },
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
