package com.waivera.waivera.feature.chat.message

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.SendState
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraLogoTile
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.bubbleArrival
import com.waivera.waivera.core.designsystem.component.rememberBubbleArrival
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.LocalWaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.core.designsystem.theme.onAccentFill
import com.waivera.waivera.feature.chat.R
import java.time.Instant
import java.time.ZoneId

/** Everything a row can ask for. A null callback turns that affordance off (read-only, threads). */
@Immutable
class MessageRowCallbacks(
    val onToggleTimestamp: (key: String) -> Unit,
    val onLongPress: ((Message) -> Unit)?,
    val onReact: ((messageId: Long, emoji: String) -> Unit)?,
    val onReactionDetails: ((messageId: Long, emoji: String) -> Unit)?,
    val onOpenThread: ((rootMessageId: Long) -> Unit)?,
    val onOpenAttachment: (Message) -> Unit,
    val onOpenPerson: (userId: String) -> Unit,
    val onLink: (url: String) -> Unit,
    val onRetry: (clientMessageId: String) -> Unit,
    val onEditFailed: (Message) -> Unit,
    val onDiscard: (clientMessageId: String) -> Unit,
) {
    companion object {
        val None = MessageRowCallbacks(
            onToggleTimestamp = {},
            onLongPress = null,
            onReact = null,
            onReactionDetails = null,
            onOpenThread = null,
            onOpenAttachment = {},
            onOpenPerson = {},
            onLink = {},
            onRetry = {},
            onEditFailed = {},
            onDiscard = {},
        )
    }
}

/** Time context for rows: "now" ticks every 30 seconds on the screen (03 §4). */
@Immutable
data class RowTime(val now: Instant, val zone: ZoneId)

@Composable
fun MessageRowView(
    row: TimelineItem.MessageRow,
    authorName: String,
    time: RowTime,
    timestampVisible: Boolean,
    highlighted: Boolean,
    callbacks: MessageRowCallbacks,
    modifier: Modifier = Modifier,
    showAuthorLine: Boolean = true,
) {
    val colors = WaiveraTheme.colors
    val arrival = rememberBubbleArrival(row.isLiveArrival, mine = row.isMine)
    val rowBackground = when {
        highlighted -> colors.accentWash
        row.isBot -> colors.accentWash.copy(alpha = colors.accentWash.alpha / 2)
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .bubbleArrival(progress = arrival, mine = row.isMine, offset = ChatDimens.ArrivalOffset)
            .background(rowBackground)
            .then(if (highlighted) Modifier.border(WaiveraSpacing.Hairline, colors.accentBorderStrong) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = WaiveraSpacing.Gutter,
                    end = WaiveraSpacing.Gutter,
                    top = if (row.groupedWithPrevious) WaiveraSpacing.Hairline else WaiveraSpacing.Small,
                    bottom = if (row.isBot) WaiveraSpacing.Small else WaiveraSpacing.Hairline,
                ),
            horizontalArrangement = if (row.isMine) Arrangement.End else Arrangement.Start,
        ) {
            if (!row.isMine) {
                AuthorAvatar(row, authorName, callbacks)
                Spacer(Modifier.width(WaiveraSpacing.Small))
            }
            Column(
                modifier = Modifier.fillMaxWidth(if (row.isBot) 1f else ChatDimens.BUBBLE_MAX_WIDTH_FRACTION),
                horizontalAlignment = if (row.isMine) Alignment.End else Alignment.Start,
            ) {
                if (row.startsIncomingRun() && (showAuthorLine || row.isBot)) AuthorLine(authorName, row.isBot)
                Bubble(row, authorName, time, callbacks, arrival.coerceAtMost(1f))
                Footer(row, time, timestampVisible, callbacks)
            }
        }
    }
}

/** The first bubble of someone else's run: where the author line goes. */
private fun TimelineItem.MessageRow.startsIncomingRun(): Boolean = !isMine && !groupedWithPrevious

@Composable
private fun AuthorAvatar(row: TimelineItem.MessageRow, authorName: String, callbacks: MessageRowCallbacks) {
    Box(Modifier.padding(top = if (row.groupedWithPrevious) 0.dp else ChatDimens.AvatarTopOffset)) {
        if (row.groupedWithPrevious) {
            Spacer(Modifier.size(WaiveraSizes.AvatarSmall))
        } else if (row.isBot) {
            // The bot is Waivera itself, so it wears the mark (UI8-08); the author line names it.
            WaiveraLogoTile(size = WaiveraSizes.AvatarSmall, logoHeight = WaiveraSizes.IconSmall)
        } else {
            val authorId = row.message.authorId
            val bitmap = rememberAvatar(authorId)
            val avatar = @Composable {
                WaiveraAvatar(
                    name = authorName,
                    painter = bitmap?.let { remember(it) { BitmapPainter(it) } },
                    size = WaiveraSizes.AvatarSmall,
                    contentDescription = null,
                )
            }
            if (authorId != null) {
                WaiveraPressable(
                    onClick = { callbacks.onOpenPerson(authorId) },
                    onClickLabel = stringResource(R.string.feature_chat_open_profile, authorName),
                    shape = WaiveraShapes.avatar(WaiveraSizes.AvatarSmall),
                    modifier = Modifier.semantics {
                        contentDescription = authorName
                    },
                ) { avatar() }
            } else {
                avatar()
            }
        }
    }
}

@Composable
private fun AuthorLine(name: String, isBot: Boolean) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = Modifier.padding(bottom = WaiveraSpacing.XxSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (isBot) colors.accent else colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isBot) WaiveraStatusBadge(text = stringResource(R.string.feature_chat_automated), tone = BadgeTone.Accent)
    }
}

@Composable
private fun Bubble(
    row: TimelineItem.MessageRow,
    authorName: String,
    time: RowTime,
    callbacks: MessageRowCallbacks,
    arrival: Float,
) {
    val colors = WaiveraTheme.colors
    val message = row.message
    val hasBody = message.isDeleted || row.ticket != null || row.rich != null || !message.text.isNullOrBlank()
    val shape = ChatDimens.bubbleShape(row.isMine, row.groupedWithPrevious, row.groupedWithNext)
    // v5: my own messages are the solid brand violet with white ink; everyone else's sit on the
    // surface. A deleted message stays quiet whoever wrote it.
    val solid = hasBody && row.isMine && !row.isBot && !message.isDeleted
    val (fill, border) = when {
        !hasBody -> Color.Transparent to Color.Transparent
        solid -> colors.accentFill to Color.Transparent
        row.isBot -> colors.surface to colors.accentBorder
        row.isMine -> colors.surface to colors.accentBorder
        else -> colors.surface to colors.border
    }
    val wash = colors.accentWash.copy(alpha = colors.accentWash.alpha * (1f - arrival))
    val description = rowDescription(row, authorName, time)
    WaiveraPressable(
        onClick = { callbacks.onToggleTimestamp(row.key) },
        onClickLabel = stringResource(R.string.feature_chat_show_time),
        onLongClick = callbacks.onLongPress?.takeIf {
            !message.isDeleted && !message.isPending
        }?.let { { it(message) } },
        onLongClickLabel = stringResource(R.string.feature_chat_open_actions),
        role = null,
        shape = shape,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .background(color = fill, shape = shape)
                .background(wash, shape)
                .then(if (hasBody && !solid) Modifier.border(WaiveraSpacing.Hairline, border, shape) else Modifier)
                .defaultMinSize(minWidth = if (hasBody) ChatDimens.BubbleMinWidth else 0.dp)
                .padding(
                    horizontal = if (hasBody) WaiveraSpacing.MediumSmall - WaiveraSpacing.Hairline else 0.dp,
                    vertical = if (hasBody) WaiveraSpacing.Small else 0.dp,
                ),
            horizontalAlignment = if (row.isMine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
        ) {
            OnBubbleColors(onFill = solid) { BubbleContent(row, callbacks) }
        }
    }
}

/** White inks and dark nested tints inside my violet bubble; the theme's own colours elsewhere. */
@Composable
private fun OnBubbleColors(onFill: Boolean, content: @Composable () -> Unit) {
    if (!onFill) {
        content()
        return
    }
    val onAccentFill = WaiveraTheme.colors.onAccentFill()
    CompositionLocalProvider(
        LocalWaiveraColors provides onAccentFill,
        LocalContentColor provides onAccentFill.text,
        content = content,
    )
}

/** The bubble's body, attachment, reactions and thread chip, top to bottom. */
@Composable
private fun BubbleContent(row: TimelineItem.MessageRow, callbacks: MessageRowCallbacks) {
    val message = row.message
    Column(
        horizontalAlignment = if (row.isMine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
    ) {
        BubbleBody(row, callbacks)
        message.attachment?.let {
            MessageAttachmentView(message, it, onOpen = { callbacks.onOpenAttachment(message) })
        }
        if (row.reactions.isNotEmpty()) {
            ReactionPills(
                reactions = row.reactions,
                alignEnd = row.isMine,
                onToggle = callbacks.onReact?.let { react -> { emoji: String -> react(message.id, emoji) } },
                onDetails = callbacks.onReactionDetails?.let { details ->
                    { emoji: String -> details(message.id, emoji) }
                },
            )
        }
        val threadRoot = message.navigationRootId?.takeIf { !message.isThreadReply && message.threadReplyCount > 0 }
        val openThread = callbacks.onOpenThread
        if (openThread != null && threadRoot != null) {
            ThreadChip(count = message.threadReplyCount, onClick = { openThread(threadRoot) })
        }
    }
}

@Composable
private fun BubbleBody(row: TimelineItem.MessageRow, callbacks: MessageRowCallbacks) {
    val colors = WaiveraTheme.colors
    val message = row.message
    when {
        message.isDeleted -> Text(
            text = stringResource(R.string.feature_chat_message_deleted),
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = colors.textSecondary,
        )
        row.ticket != null -> TicketCard(row.ticket, onOpen = callbacks.onLink)
        row.rich != null -> RichText(
            document = row.rich,
            onLink = callbacks.onLink,
            onAccentFill = row.isMine && !row.isBot,
        )
        !message.text.isNullOrBlank() -> Text(
            text = message.text.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
        )
    }
}

@Composable
private fun rowDescription(row: TimelineItem.MessageRow, authorName: String, time: RowTime): String {
    val message = row.message
    val body = when {
        message.isDeleted -> stringResource(R.string.feature_chat_message_deleted)
        row.ticket != null -> row.ticket.title
        else -> message.plainText().ifEmpty {
            message.attachment?.let {
                stringResource(R.string.feature_chat_row_attachment, it.originalName.orEmpty())
            }.orEmpty()
        }
    }
    val clock = message.createdAt?.let { ChatTime.clock(it, time.zone) }
    val base = if (row.isMine) {
        stringResource(R.string.feature_chat_row_mine, body)
    } else {
        stringResource(R.string.feature_chat_row_other, authorName, body)
    }
    return listOfNotNull(base, clock).joinToString(", ")
}

/**
 * Under the bubble. The last bubble of a run always shows its clock ("9:41 AM", "Sent · 9:41 AM"
 * on my own), so a glance tells when something was said and that a retried send went out
 * (UI review UI2-04). Bubbles inside a run show their time on tap only, as a relative time.
 */
@Composable
private fun Footer(
    row: TimelineItem.MessageRow,
    time: RowTime,
    timestampVisible: Boolean,
    callbacks: MessageRowCallbacks,
) {
    val message = row.message
    if (message.isPending) {
        PendingMeta(message, callbacks)
        return
    }
    val createdAt = message.createdAt
    val endOfRun = !row.groupedWithNext && !message.isDeleted
    val label = when {
        createdAt == null -> null
        // The clock line above already says this minute: keep only "Sent" / "Edited" (UI3-04).
        endOfRun && row.timeShownAbove -> untimedRunEndLabel(mine = row.isMine, edited = message.isEdited)
        endOfRun -> runEndLabel(createdAt, time, mine = row.isMine).withEdited(message.isEdited)
        timestampVisible -> relativeTimeText(ChatTime.relative(createdAt, time.now, time.zone))
            .withEdited(message.isEdited)
        else -> null
    }
    if (label == null && !message.isBookmarked) return
    Row(
        modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = WaiveraTheme.colors.textSecondary,
            )
        }
        if (message.isBookmarked) SavedPill()
    }
}

/** Large-font failed actions: the Retry primary, then Edit and Discard behind one 48dp "More" button. */
@Composable
private fun FailedActionsCompact(message: Message, clientId: String, callbacks: MessageRowCallbacks) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaiveraButton(
            text = stringResource(R.string.feature_chat_failed_retry),
            onClick = { callbacks.onRetry(clientId) },
            leadingIcon = WaiveraIcons.Refresh,
        )
        Box {
            WaiveraIconButton(
                icon = WaiveraIcons.More,
                contentDescription = stringResource(R.string.feature_chat_failed_more),
                onClick = { menuOpen = true },
                tint = WaiveraTheme.colors.accent,
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (message.attachment == null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feature_chat_failed_edit)) },
                        onClick = {
                            menuOpen = false
                            callbacks.onEditFailed(message)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_chat_failed_discard)) },
                    onClick = {
                        menuOpen = false
                        callbacks.onDiscard(clientId)
                    },
                )
            }
        }
    }
}

/** "9:41 AM", or "Sent · 9:41 AM" under my own run, which is how a send or a retry confirms. */
@Composable
private fun runEndLabel(createdAt: Instant, time: RowTime, mine: Boolean): String {
    val clock = remember(createdAt, time.zone) { ChatTime.clock(createdAt, time.zone) }
    return if (mine) stringResource(R.string.feature_chat_sent_at, clock) else clock
}

@Composable
private fun String.withEdited(edited: Boolean): String =
    if (edited) stringResource(R.string.feature_chat_timestamp_edited, this) else this

/** "Sent", "Sent · edited" or "Edited" when the time is already on the clock line above. */
@Composable
private fun untimedRunEndLabel(mine: Boolean, edited: Boolean): String? = when {
    mine && edited -> stringResource(R.string.feature_chat_timestamp_edited, stringResource(R.string.feature_chat_sent))
    mine -> stringResource(R.string.feature_chat_sent)
    edited -> stringResource(R.string.feature_chat_edited)
    else -> null
}

@Composable
private fun SavedPill() {
    val motion = WaiveraTheme.motion
    val scale = remember { Animatable(if (motion.reduced) 1f else SAVED_POP_START) }
    LaunchedEffect(Unit) { if (scale.value != 1f) scale.animateTo(1f, motion.celebrate()) }
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(WaiveraTheme.colors.accentSoft, WaiveraShapes.Chip)
            .border(WaiveraSpacing.Hairline, WaiveraTheme.colors.accentBorder, WaiveraShapes.Chip)
            .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.XxSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(ChatIcons.BookmarkFilled),
            contentDescription = null,
            tint = WaiveraTheme.colors.onAccentSoft,
            modifier = Modifier.size(WaiveraSizes.IconSmall),
        )
        Text(
            text = stringResource(R.string.feature_chat_saved),
            style = WaiveraTextStyles.Badge,
            color = WaiveraTheme.colors.onAccentSoft,
        )
    }
}

private const val SAVED_POP_START = 0.75f

/**
 * Discard, Edit and Retry, end-aligned, as on the v5 board: Discard and Edit are the raised
 * secondary and Retry, the one way forward, the primary with its refresh glyph at the end (v5
 * decision 2, option C). At large font the three would wrap and leave one on a line of its own,
 * so Retry stays the primary and Edit and Discard move into a "More" menu (UI3-11).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingMeta(message: Message, callbacks: MessageRowCallbacks) {
    val colors = WaiveraTheme.colors
    val clientId = message.clientMessageId
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(top = WaiveraSpacing.XxSmall),
    ) {
        Text(
            text = stringResource(
                when (message.sendState) {
                    SendState.FAILED -> R.string.feature_chat_failed
                    SendState.QUEUED -> R.string.feature_chat_queued
                    else -> R.string.feature_chat_sending
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (message.sendState == SendState.FAILED) colors.dangerText else colors.textSecondary,
            textAlign = TextAlign.End,
        )
        if (message.sendState == SendState.FAILED && clientId != null && isLargeFontScale()) {
            FailedActionsCompact(message = message, clientId = clientId, callbacks = callbacks)
        } else if (message.sendState == SendState.FAILED && clientId != null) {
            FlowRow(
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            ) {
                WaiveraButton(
                    text = stringResource(R.string.feature_chat_failed_discard),
                    onClick = { callbacks.onDiscard(clientId) },
                    variant = WaiveraButtonVariant.Secondary,
                )
                if (message.attachment == null) {
                    WaiveraButton(
                        text = stringResource(R.string.feature_chat_failed_edit),
                        onClick = { callbacks.onEditFailed(message) },
                        variant = WaiveraButtonVariant.Secondary,
                    )
                }
                WaiveraButton(
                    text = stringResource(R.string.feature_chat_failed_retry),
                    onClick = { callbacks.onRetry(clientId) },
                    leadingIcon = WaiveraIcons.Refresh,
                )
            }
        }
    }
}

@Composable
private fun ThreadChip(count: Int, onClick: () -> Unit) {
    val colors = WaiveraTheme.colors
    WaiveraPressable(
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_chat_open_thread),
        shape = WaiveraShapes.Chip,
        modifier = Modifier.defaultMinSize(minHeight = WaiveraSpacing.MinTouchTarget),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .background(colors.accentWash, WaiveraShapes.Chip)
                .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
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
                color = colors.accent,
            )
        }
    }
}

@Composable
fun relativeTimeText(value: RelativeTime): String = when (value) {
    RelativeTime.JustNow -> stringResource(R.string.feature_chat_time_just_now)
    is RelativeTime.Seconds -> pluralStringResource(R.plurals.feature_chat_time_seconds, value.value, value.value)
    is RelativeTime.Minutes -> pluralStringResource(R.plurals.feature_chat_time_minutes, value.value, value.value)
    is RelativeTime.Hours -> pluralStringResource(R.plurals.feature_chat_time_hours, value.value, value.value)
    is RelativeTime.Days -> pluralStringResource(R.plurals.feature_chat_time_days, value.value, value.value)
    is RelativeTime.OnDate -> ChatTime.shortDate(value.date, value.showYear)
}
