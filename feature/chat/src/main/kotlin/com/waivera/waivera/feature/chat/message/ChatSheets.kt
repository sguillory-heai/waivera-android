package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.designsystem.component.LocalSignedInAccount
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBusyIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R

/** The sheet or dialog on top of a message list. One at a time. */
@Immutable
sealed interface ChatSheet {
    data class Actions(val model: MessageActionsModel) : ChatSheet

    data class ReactionDetails(val messageId: Long, val initialEmoji: String) : ChatSheet

    data class FileActions(val messageId: Long, val fileName: String, val busy: Boolean = false) : ChatSheet

    data class ImagePreview(val messageId: Long) : ChatSheet

    data class ConfirmDelete(val messageId: Long, val inProgress: Boolean = false) : ChatSheet
}

/** What the action sheet offers for one message (03 §14; threads get the same, 04 §1). */
@Immutable
data class MessageActionsModel(
    val message: Message,
    val rich: RichDocument?,
    val canReact: Boolean,
    val canReply: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val canCopy: Boolean,
    val copied: Boolean = false,
)

@Composable
fun MessageActionsContent(
    model: MessageActionsModel,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onBookmark: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = WaiveraSpacing.Medium),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = WaiveraSpacing.Gutter)
                .fillMaxWidth()
                // The message being acted on, on the sheet's muted step at the sheet card radius.
                .background(colors.surfaceMuted, WaiveraShapes.SheetCard)
                .padding(start = WaiveraSpacing.Medium, bottom = WaiveraSpacing.Medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.feature_chat_preview_label),
                    style = WaiveraTextStyles.Eyebrow,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                WaiveraIconButton(
                    icon = WaiveraIcons.Close,
                    contentDescription = stringResource(R.string.feature_chat_action_close),
                    onClick = onClose,
                )
            }
            Column(
                Modifier
                    .heightIn(max = ChatDimens.PreviewMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(end = WaiveraSpacing.MediumSmall),
            ) {
                when {
                    model.rich != null -> RichText(document = model.rich, onLink = onLink)
                    else -> Text(
                        text = model.message.plainText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                }
            }
        }
        if (model.canReact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = WaiveraSpacing.Gutter),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall, Alignment.CenterHorizontally),
            ) {
                Reactions.QUICK.forEach { emoji ->
                    WaiveraPressable(
                        onClick = { onReact(emoji) },
                        onClickLabel = stringResource(R.string.feature_chat_react_with, emoji),
                        // The emoji in the icon tile's shape and muted fill, like every other choice.
                        shape = WaiveraShapes.Tile,
                        modifier = Modifier
                            .size(ChatDimens.EmojiTarget)
                            .background(colors.surfaceMuted, WaiveraShapes.Tile)
                            .semantics { contentDescription = emoji },
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
        Column {
            if (model.canCopy) {
                ActionRow(
                    icon = ChatIcons.Copy,
                    label = stringResource(
                        if (model.copied) R.string.feature_chat_action_copied else R.string.feature_chat_action_copy,
                    ),
                    onClick = onCopy,
                    accent = model.copied,
                )
            }
            if (model.canReply) {
                ActionRow(WaiveraIcons.Reply, stringResource(R.string.feature_chat_action_reply), onReply)
            }
            if (model.canEdit) ActionRow(ChatIcons.Edit, stringResource(R.string.feature_chat_action_edit), onEdit)
            ActionRow(
                icon = if (model.message.isBookmarked) ChatIcons.BookmarkFilled else WaiveraIcons.Bookmark,
                label = stringResource(
                    if (model.message.isBookmarked) {
                        R.string.feature_chat_action_bookmarked
                    } else {
                        R.string.feature_chat_action_bookmark
                    },
                ),
                onClick = onBookmark,
                tone = WaiveraTileTone.Rose,
            )
            if (model.canDelete) {
                ActionRow(
                    ChatIcons.Delete,
                    stringResource(R.string.feature_chat_action_delete),
                    onDelete,
                    danger = true,
                )
            }
        }
    }
}

/**
 * One message action, led by the shared icon tile like every other row: brand for chat
 * actions, rose for bookmarks (about you), danger for delete.
 */
@Composable
private fun ActionRow(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    accent: Boolean = false,
    tone: WaiveraTileTone = WaiveraTileTone.Brand,
) {
    val colors = WaiveraTheme.colors
    val textColor = when {
        danger -> colors.dangerText
        accent -> colors.accent
        else -> colors.text
    }
    WaiveraPressable(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSizes.ListRowMinHeight)
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraIconTile(icon = icon, tone = if (danger) WaiveraTileTone.Danger else tone)
            Text(text = label, style = MaterialTheme.typography.titleSmall, color = textColor)
        }
    }
}

/** Who reacted, one tab per emoji present (03 §13). */
@Composable
fun ReactionDetailsContent(
    reactions: List<ReactionSummary>,
    initialEmoji: String,
    nameFor: (String) -> String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    myUserId: String? = null,
) {
    val account = LocalSignedInAccount.current
    var selected by rememberSaveable(initialEmoji) {
        mutableStateOf(reactions.firstOrNull { it.emoji == initialEmoji }?.emoji ?: reactions.firstOrNull()?.emoji)
    }
    val current = reactions.firstOrNull { it.emoji == selected } ?: reactions.firstOrNull()
    Column(modifier = modifier.fillMaxWidth().padding(bottom = WaiveraSpacing.Medium)) {
        Row(
            modifier = Modifier.padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feature_chat_reactions_title),
                style = MaterialTheme.typography.titleLarge,
                color = WaiveraTheme.colors.text,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.feature_chat_reactions_close),
                onClick = onClose,
            )
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = WaiveraSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            reactions.forEach { reaction ->
                WaiveraFilterChip(
                    label = "${reaction.emoji} ${reaction.count}",
                    selected = reaction.emoji == current?.emoji,
                    onClick = { selected = reaction.emoji },
                    modifier = Modifier.widthIn(min = ChatDimens.ReactionTabMinWidth),
                )
            }
        }
        current?.userIds?.forEach { userId ->
            val name = nameFor(userId)
            val avatar = rememberAvatar(userId)
            // "You" is my row: show my own picture or initials, the same as the top bar, not a "Y".
            val me = account?.takeIf { userId == myUserId && it.name.isNotBlank() }
            WaiveraListRow(
                headline = name,
                leading = {
                    WaiveraAvatar(
                        name = me?.name ?: name,
                        imageModel = me?.imageModel,
                        painter = avatar?.let { remember(it) { BitmapPainter(it) } },
                        size = WaiveraSizes.IconTile,
                        contentDescription = null,
                    )
                },
                trailing = { Text(text = current.emoji, style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
}

/** "Save to device" and "Open / Share" for a file attachment (03 §8). */
@Composable
fun FileActionsContent(
    fileName: String,
    busy: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter)
            .padding(bottom = WaiveraSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleLarge,
                color = WaiveraTheme.colors.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.feature_chat_action_close),
                onClick = onClose,
            )
        }
        Text(
            text = stringResource(R.string.feature_chat_file_sheet_message),
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.textSecondary,
        )
        if (busy) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraBusyIndicator()
                Text(
                    text = stringResource(R.string.feature_chat_file_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WaiveraTheme.colors.textSecondary,
                )
            }
        }
        WaiveraButton(
            text = stringResource(R.string.feature_chat_file_save),
            onClick = onSave,
            enabled = !busy,
            fullWidth = true,
            leadingIcon = ChatIcons.Download,
        )
        WaiveraButton(
            text = stringResource(R.string.feature_chat_file_share),
            onClick = onShare,
            enabled = !busy,
            fullWidth = true,
            variant = WaiveraButtonVariant.Secondary,
            leadingIcon = WaiveraIcons.OpenExternal,
        )
    }
}

/**
 * Full-screen image preview: pinch zoom clamped to 1×–5×, double tap toggles 1× and 2.5×, pan
 * while zoomed. Zoom resets whenever the preview closes because the state lives in the dialog.
 */
@Composable
fun ImagePreviewDialog(message: Message, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = WaiveraTheme.colors
        val maxPx = with(LocalDensity.current) { ChatDimens.ImageWidth.roundToPx() * PREVIEW_SCALE }
        val state = rememberChatImage(LocalChatImages.current.attachmentModel(message), maxPx)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, ChatDimens.MAX_ZOOM)
            offset = if (scale > 1f) offset + pan else Offset.Zero
        }
        LaunchedEffect(Unit) {
            scale = 1f
            offset = Offset.Zero
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim.copy(alpha = PREVIEW_SCRIM_ALPHA))
                .safeDrawingPadding(),
        ) {
            when (state) {
                is ChatImageState.Ready -> Image(
                    bitmap = state.bitmap,
                    contentDescription = message.attachment?.originalName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else ChatDimens.DOUBLE_TAP_ZOOM
                                offset = Offset.Zero
                            })
                        }
                        .transformable(transform)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
                ChatImageState.Loading -> WaiveraBusyIndicator(
                    color = onScrimColor(),
                    // On the dark scrim the lilac track would glare; the arc alone reads there.
                    trackColor = Color.Transparent,
                    size = WaiveraSizes.ProgressLarge,
                    strokeWidth = WaiveraSizes.ProgressStrokeLarge,
                    modifier = Modifier.align(Alignment.Center),
                )
                ChatImageState.Unavailable -> Text(
                    text = stringResource(R.string.feature_chat_image_unavailable),
                    color = onScrimColor(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            WaiveraIconButton(
                icon = WaiveraIcons.Close,
                contentDescription = stringResource(R.string.feature_chat_image_close),
                onClick = onClose,
                tint = onScrimColor(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(WaiveraSpacing.Small)
                    .background(colors.scrim, WaiveraShapes.Circle),
            )
            Text(
                text = stringResource(R.string.feature_chat_image_zoom_hint),
                style = MaterialTheme.typography.labelMedium,
                color = onScrimColor(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(WaiveraSpacing.Medium)
                    .clearAndSetSemantics {},
            )
        }
    }
}

private const val PREVIEW_SCALE = 4
private const val PREVIEW_SCRIM_ALPHA = 0.94f

@WaiveraPreviews
@Composable
private fun FileActionsPreview() {
    WaiveraTheme {
        FileActionsContent(fileName = "care-plan.pdf", busy = true, onSave = {}, onShare = {}, onClose = {})
    }
}

@WaiveraPreviews
@Composable
private fun ReactionDetailsPreview() {
    WaiveraTheme {
        ReactionDetailsContent(
            reactions = listOf(
                ReactionSummary("👍", 2, mine = true, userIds = listOf("me", "jordan")),
                ReactionSummary("❤️", 1, mine = false, userIds = listOf("priya")),
            ),
            initialEmoji = "❤️",
            nameFor = { if (it == "me") "You" else it.replaceFirstChar(Char::uppercase) },
            onClose = {},
        )
    }
}
