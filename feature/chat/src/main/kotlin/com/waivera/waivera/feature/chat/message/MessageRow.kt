package com.waivera.waivera.feature.chat.message

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.waivera.waivera.core.designsystem.component.WaiveraExpandVisibility
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraLogoTile
import com.waivera.waivera.core.designsystem.component.WaiveraPopVisibility
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraSwap
import com.waivera.waivera.core.designsystem.component.bubbleArrival
import com.waivera.waivera.core.designsystem.component.rememberBubbleArrival
import com.waivera.waivera.core.designsystem.component.rememberLatestNonNull
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.LocalWaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.core.designsystem.theme.onMyBubble
import com.waivera.waivera.core.designsystem.theme.swapEnterFade
import com.waivera.waivera.feature.chat.R
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

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
    /** These callbacks, with [onToggle] in place of [onToggleTimestamp]. */
    fun withToggleTimestamp(onToggle: (key: String) -> Unit): MessageRowCallbacks = MessageRowCallbacks(
        onToggleTimestamp = onToggle,
        onLongPress = onLongPress,
        onReact = onReact,
        onReactionDetails = onReactionDetails,
        onOpenThread = onOpenThread,
        onOpenAttachment = onOpenAttachment,
        onOpenPerson = onOpenPerson,
        onLink = onLink,
        onRetry = onRetry,
        onEditFailed = onEditFailed,
        onDiscard = onDiscard,
    )

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
                Bubble(row, authorName, time, callbacks, arrival)
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
    arrival: State<Float>,
) {
    val colors = WaiveraTheme.colors
    val message = row.message
    val hasBody = message.isDeleted || row.ticket != null || row.rich != null || !message.text.isNullOrBlank()
    val shape = ChatDimens.bubbleShape(row.isMine, row.groupedWithPrevious, row.groupedWithNext)
    // v5 decision 3, option B: my own messages are the soft violet tint (light) or the lifted plum
    // (dark) with the theme's ink, and read as mine by the tint and the right alignment; everyone
    // else's sit on the surface with a hairline. A deleted message stays quiet whoever wrote it.
    val tinted = hasBody && row.isMine && !row.isBot && !message.isDeleted
    val (fill, border) = when {
        !hasBody -> Color.Transparent to Color.Transparent
        tinted -> colors.myBubble to Color.Transparent
        row.isBot -> colors.surface to colors.accentBorder
        row.isMine -> colors.surface to colors.accentBorder
        else -> colors.surface to colors.border
    }
    val description = rowDescription(row, authorName, time)
    // Where the bubble is when its long press fires, so the focus overlay lifts it in place.
    val anchors = LocalMessageFocusAnchors.current
    val view = LocalView.current
    val anchor = remember { AnchorCoordinates() }
    WaiveraPressable(
        onClick = { callbacks.onToggleTimestamp(row.key) },
        onClickLabel = stringResource(R.string.feature_chat_show_time),
        onLongClick = callbacks.onLongPress?.takeIf {
            !message.isDeleted && !message.isPending
        }?.let { open ->
            {
                anchors?.record(message.id, anchor.screenBounds(view))
                open(message)
            }
        },
        onLongClickLabel = stringResource(R.string.feature_chat_open_actions),
        role = null,
        shape = shape,
        modifier = Modifier
            .then(if (anchors != null) Modifier.onPlaced { anchor.coordinates = it } else Modifier)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.background(color = fill, shape = shape)) {
            // A live arrival's wash fades in a layer of its own, read in the layer, so the fade
            // never recomposes or redraws the bubble (M1-08). At rest it is fully transparent.
            if (row.isLiveArrival) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 1f - arrival.value.coerceIn(0f, 1f) }
                        .background(colors.accentWash, shape),
                )
            }
            Column(
                modifier = Modifier
                    .then(if (hasBody && !tinted) Modifier.border(WaiveraSpacing.Hairline, border, shape) else Modifier)
                    .defaultMinSize(minWidth = if (hasBody) ChatDimens.BubbleMinWidth else 0.dp)
                    .padding(
                        horizontal = if (hasBody) WaiveraSpacing.MediumSmall - WaiveraSpacing.Hairline else 0.dp,
                        vertical = if (hasBody) WaiveraSpacing.Small else 0.dp,
                    ),
                horizontalAlignment = if (row.isMine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
            ) {
                InMyBubbleColors(inMyBubble = tinted) { BubbleContent(row, callbacks) }
            }
        }
    }
}

/** The inks that keep 4.5:1 on my tinted bubble ([onMyBubble]); the theme's own colours elsewhere. */
@Composable
internal fun InMyBubbleColors(inMyBubble: Boolean, content: @Composable () -> Unit) {
    if (!inMyBubble) {
        content()
        return
    }
    val onMyBubble = WaiveraTheme.colors.onMyBubble()
    CompositionLocalProvider(
        LocalWaiveraColors provides onMyBubble,
        LocalContentColor provides onMyBubble.text,
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
            inMyBubble = row.isMine && !row.isBot,
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
    // A row first seen while sending is a live send: its line swaps "Sending…" for "Sent · 9:41"
    // (or for the failed actions) on the shared swap motion, which is how a send confirms, with no
    // vibration. History never pays for the swap.
    val live = remember(row.key) { message.isPending }
    if (!live && !message.isPending) {
        SettledFooter(row, time, timestampVisible)
        return
    }
    // "Sending…" waits a beat: a good connection acks inside it, so the line goes straight to
    // "Sent · 9:41" in one swap instead of flashing twice (M1-09).
    val sendingLabelDue by produceState(initialValue = false, row.key) {
        delay(WaiveraMotionScheme.SENDING_LABEL_DELAY_MILLIS)
        value = true
    }
    // "Sending…" fades into the quiet slot in place instead of swapping in. A swap side that is
    // still arriving when the ack lands leaves on Compose's default spring, not the quick exit,
    // so "Sent" would draw over a fading "Sending…" for about 100 ms.
    // Each side draws from the snapshot it was swapped in with, never from the live message: on
    // Retry the leaving side used to re-render as "Sending…" at full strength with its actions
    // gone, then fade, while the real "Sending…" faded in (M3-01). Now "Not sent" and its actions
    // fade out as they were, and the slot closes on the swap's size spring.
    WaiveraSwap(
        targetState = FooterSnapshot(footerPhase(message, sendingLabelDue), message),
        contentAlignment = Alignment.TopEnd,
        contentKey = { it.swapKey },
    ) { snapshot ->
        when (snapshot.phase) {
            FooterPhase.Settled -> SettledFooter(row, time, timestampVisible)
            // The slot keeps the line's height, so nothing jumps when the line arrives.
            FooterPhase.Quiet, FooterPhase.Sending ->
                SendingMeta(snapshot.message, callbacks, shown = snapshot.phase == FooterPhase.Sending)
            FooterPhase.Failed -> PendingMeta(snapshot.message, callbacks)
        }
    }
}

/**
 * What one side of the footer swap shows: its [phase] and the [message] as it was then. A side
 * that is leaving keeps its own snapshot, so it never re-renders with the next state's data.
 */
@Immutable
internal data class FooterSnapshot(val phase: FooterPhase, val message: Message) {
    /** The quiet slot and "Sending…" are one side, so the label fades in place (e1f7fe0). */
    val swapKey: FooterPhase get() = if (phase == FooterPhase.Quiet) FooterPhase.Sending else phase
}

/** "Sending…" in the footer slot: invisible and silent until [shown], then faded in. */
@Composable
private fun SendingMeta(message: Message, callbacks: MessageRowCallbacks, shown: Boolean) {
    val alpha = animateFloatAsState(if (shown) 1f else 0f, WaiveraTheme.motion.swapEnterFade(), label = "sending")
    PendingMeta(
        message,
        callbacks,
        Modifier
            .graphicsLayer { this.alpha = alpha.value }
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {}),
    )
}

/** Where my own message's line is: going out but not saying so yet, going out, failed, or sent. */
internal enum class FooterPhase { Quiet, Sending, Failed, Settled }

/**
 * The footer for [message]. A send stays [FooterPhase.Quiet] until [sendingLabelDue]; a failure
 * shows at once, and so does a send queued while offline, which no quick ack will follow.
 */
internal fun footerPhase(message: Message, sendingLabelDue: Boolean): FooterPhase = when {
    !message.isPending -> FooterPhase.Settled
    message.sendState == SendState.FAILED -> FooterPhase.Failed
    message.sendState == SendState.SENDING && !sendingLabelDue -> FooterPhase.Quiet
    else -> FooterPhase.Sending
}

@Composable
private fun SettledFooter(row: TimelineItem.MessageRow, time: RowTime, timestampVisible: Boolean) {
    val message = row.message
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
    if (endOfRun || createdAt == null) {
        if (label == null && !message.isBookmarked) return
        FooterLine(label, message.isBookmarked)
        return
    }
    // Inside a run the time shows on tap. The line grows and folds on the layout token, and keeps
    // its text while it folds, so the list moves instead of jumping (M1-13).
    val shownLabel = rememberLatestNonNull(label)
    WaiveraExpandVisibility(visible = label != null || message.isBookmarked) {
        FooterLine(label = if (message.isBookmarked) label else shownLabel, bookmarked = message.isBookmarked)
    }
}

@Composable
private fun FooterLine(label: String?, bookmarked: Boolean) {
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
        // Bookmarking pops the pill in; it is simply there on rows that were saved before.
        WaiveraPopVisibility(visible = bookmarked) { SavedPill() }
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
    Row(
        modifier = Modifier
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

/**
 * Discard, Edit and Retry, end-aligned on one line, as on the v5 board ([FailedActionsRow]).
 * At large font the three would wrap and leave one on a line of its own, so Retry stays the
 * primary and Edit and Discard move into a "More" menu (UI3-11).
 */
@Composable
private fun PendingMeta(message: Message, callbacks: MessageRowCallbacks, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val clientId = message.clientMessageId
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier.padding(top = WaiveraSpacing.XxSmall),
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
            FailedActionsRow(
                showEdit = message.attachment == null,
                onDiscard = { callbacks.onDiscard(clientId) },
                onEdit = { callbacks.onEditFailed(message) },
                onRetry = { callbacks.onRetry(clientId) },
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
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
