package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R

/** Composer configuration; the draft text itself is hoisted to the screen. */
@Immutable
data class ComposerConfig(
    val isReply: Boolean,
    val allowImages: Boolean,
    /** False while the conversation's permissions are still loading. */
    val enabled: Boolean,
    val stagedImage: StagedImage? = null,
    val isEditing: Boolean = false,
)

@Composable
fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    config: ComposerConfig,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: () -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = WaiveraTheme.colors
    val canSend = config.enabled && (draft.isNotBlank() || config.stagedImage != null)
    Column(modifier = modifier.fillMaxWidth()) {
        if (config.isEditing) EditingBar(onCancelEdit)
        config.stagedImage?.let { StagedImageCard(it, onRemoveImage) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WaiveraSpacing.Small, vertical = WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        ) {
            if (config.allowImages && !config.isEditing) {
                WaiveraIconButton(
                    icon = ChatIcons.AttachImage,
                    contentDescription = stringResource(R.string.feature_chat_attach_image),
                    onClick = onAttach,
                    enabled = config.enabled,
                    tint = colors.accent,
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = config.enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ChatDimens.ComposerMinHeight, max = ChatDimens.ComposerMaxHeight)
                    .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = {
                    Text(
                        text = stringResource(
                            if (config.isReply) {
                                R.string.feature_chat_composer_reply_placeholder
                            } else {
                                R.string.feature_chat_composer_placeholder
                            },
                        ),
                        color = colors.textSecondary,
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = WaiveraShapes.Composer,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    disabledContainerColor = colors.surfaceMuted,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.border,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text,
                ),
            )
            // Disabled reads as "nothing to send yet": a muted fill with a visible outline, not a
            // faded accent that looks like a broken enabled button.
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier
                    .size(WaiveraSizes.IconButton)
                    .then(
                        if (canSend) {
                            Modifier
                        } else {
                            Modifier.border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Circle)
                        },
                    ),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.textSecondary,
                ),
            ) {
                Icon(
                    painterResource(ChatIcons.Send),
                    contentDescription = stringResource(
                        when {
                            config.isEditing -> R.string.feature_chat_save_edit
                            config.isReply -> R.string.feature_chat_send_reply
                            else -> R.string.feature_chat_send
                        },
                    ),
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            }
        }
    }
}

@Composable
private fun EditingBar(onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(ChatIcons.Edit),
            contentDescription = null,
            tint = WaiveraTheme.colors.accent,
            modifier = Modifier.size(WaiveraSizes.IconSmall),
        )
        Text(
            text = stringResource(R.string.feature_chat_editing),
            style = MaterialTheme.typography.labelMedium,
            color = WaiveraTheme.colors.accent,
            modifier = Modifier
                .weight(1f)
                .padding(start = WaiveraSpacing.Small),
        )
        WaiveraIconButton(
            icon = WaiveraIcons.Close,
            contentDescription = stringResource(R.string.feature_chat_cancel_edit),
            onClick = onCancel,
        )
    }
}

@Composable
private fun StagedImageCard(image: StagedImage, onRemove: () -> Unit) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall)
            .fillMaxWidth()
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Card)
            .padding(start = WaiveraSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val state = rememberChatImage(image.uri, maxSizePx = THUMBNAIL_PX)
        Box(
            modifier = Modifier
                .size(ChatDimens.StagedThumbnail)
                .background(colors.surfaceMuted, WaiveraShapes.Chip),
            contentAlignment = Alignment.Center,
        ) {
            if (state is ChatImageState.Ready) {
                androidx.compose.foundation.Image(
                    bitmap = state.bitmap,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(ChatDimens.StagedThumbnail),
                )
            } else {
                Icon(painterResource(ChatIcons.Image), contentDescription = null, tint = colors.textSecondary)
            }
        }
        Text(
            text = image.fileName ?: stringResource(R.string.feature_chat_selected_image),
            style = MaterialTheme.typography.bodySmall,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        WaiveraButton(
            text = stringResource(R.string.feature_chat_remove_image),
            onClick = onRemove,
            variant = WaiveraButtonVariant.Text,
        )
    }
}

private const val THUMBNAIL_PX = 200

/** Always reserves its height so the composer never jumps when someone starts typing (03 §11). */
@Composable
fun TypingSlot(label: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ChatDimens.TypingSlot)
            .padding(horizontal = WaiveraSpacing.Gutter)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = WaiveraTheme.colors.textSecondary)
        }
    }
}

@Composable
fun typingLabel(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> stringResource(R.string.feature_chat_typing_one, names[0])
    2 -> stringResource(R.string.feature_chat_typing_two, names[0], names[1])
    else -> stringResource(R.string.feature_chat_typing_many)
}

/** "Not sent" with the mapped copy; [isReply] switches the noun as the thread screen does. */
@Composable
fun SendErrorBanner(kind: SendErrorKind, isReply: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val message = stringResource(
        if (isReply) {
            when (kind) {
                SendErrorKind.Archived -> R.string.feature_chat_reply_error_archived
                SendErrorKind.ReadOnly -> R.string.feature_chat_reply_error_read_only
                SendErrorKind.Offline -> R.string.feature_chat_reply_error_offline
                SendErrorKind.Session -> R.string.feature_chat_reply_error_session
                SendErrorKind.Server -> R.string.feature_chat_reply_error_server
            }
        } else {
            when (kind) {
                SendErrorKind.Archived -> R.string.feature_chat_send_error_archived
                SendErrorKind.ReadOnly -> R.string.feature_chat_send_error_read_only
                SendErrorKind.Offline -> R.string.feature_chat_send_error_offline
                SendErrorKind.Session -> R.string.feature_chat_send_error_session
                SendErrorKind.Server -> R.string.feature_chat_send_error_server
            }
        },
    )
    WaiveraBanner(
        title = stringResource(R.string.feature_chat_not_sent),
        message = message,
        tone = BannerTone.Error,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/** The lock notice that replaces the composer (03 §18, 04 §7). */
@Composable
fun ReadOnlyNotice(reason: ReadOnlyReason, isThread: Boolean, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val (title, body) = when {
        reason == ReadOnlyReason.Automated ->
            R.string.feature_chat_read_only_bot_title to R.string.feature_chat_read_only_bot_reason
        isThread -> R.string.feature_chat_read_only_thread_title to R.string.feature_chat_read_only_thread_reason
        reason == ReadOnlyReason.ArchivedChannel ->
            R.string.feature_chat_read_only_archived_title to R.string.feature_chat_read_only_archived_reason
        else -> R.string.feature_chat_read_only_channel_title to R.string.feature_chat_read_only_channel_reason
    }
    Row(
        modifier = modifier
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
            .fillMaxWidth()
            .background(colors.accentSoft, WaiveraShapes.Composer)
            .border(WaiveraSpacing.Hairline, colors.accentBorderStrong, WaiveraShapes.Composer)
            .padding(WaiveraSpacing.MediumSmall)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(ChatDimens.ReadOnlyTile)
                .background(colors.accentWash, WaiveraShapes.Circle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(WaiveraIcons.Lock),
                contentDescription = null,
                tint = colors.onAccentSoft,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleSmall, color = colors.onAccentSoft)
            Text(text = stringResource(body), style = MaterialTheme.typography.bodySmall, color = colors.onAccentSoft)
        }
    }
}

/**
 * "Call in progress" strip under the header (03 §1), laid out in the screen column above the
 * messages so it pushes the list down instead of covering it. Join is a tonal 48dp button; at
 * large font it moves under the label so neither is squeezed.
 */
@Composable
fun CallBanner(onJoin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val stacked = LocalDensity.current.fontScale > CALL_BANNER_STACK_FONT_SCALE
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ChatDimens.CallBannerMinHeight)
                .background(colors.accentWash)
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallBannerLabel(modifier = Modifier.weight(1f))
                if (!stacked) CallJoinButton(onJoin)
            }
            if (stacked) CallJoinButton(onJoin, Modifier.fillMaxWidth())
        }
        WaiveraDivider()
    }
}

@Composable
private fun CallBannerLabel(modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(ChatDimens.LiveDot)
                .background(colors.accent, WaiveraShapes.Circle),
        )
        Icon(
            painterResource(WaiveraIcons.Video),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(WaiveraSizes.IconSmall),
        )
        Text(
            text = stringResource(R.string.feature_chat_call_in_progress),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onAccentSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CallJoinButton(onJoin: () -> Unit, modifier: Modifier = Modifier) {
    WaiveraButton(
        text = stringResource(R.string.feature_chat_call_join_on_web),
        onClick = onJoin,
        variant = WaiveraButtonVariant.Tonal,
        leadingIcon = WaiveraIcons.OpenExternal,
        modifier = modifier,
    )
}

private const val CALL_BANNER_STACK_FONT_SCALE = 1.3f

/** Five bubble bones, alternating sides, sharing one pulse; announced once as loading (03 §21). */
@Composable
fun MessageListSkeleton(modifier: Modifier = Modifier, rows: Int = ChatDimens.SKELETON_FRACTIONS_COUNT) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_chat_messages_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small)
            .clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small, Alignment.Bottom),
    ) {
        repeat(rows) { index ->
            val mine = index % 2 == 1
            val fraction = ChatDimens.SkeletonWidths[index % ChatDimens.SkeletonWidths.size]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .heightIn(min = ChatDimens.SkeletonBubbleHeight),
                    shape = if (mine) ChatDimens.SkeletonMineShape else ChatDimens.SkeletonOtherShape,
                    pulse = pulse,
                )
            }
        }
    }
}

@Composable
fun JumpToLatestPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    WaiveraPressable(
        onClick = onClick,
        shape = WaiveraShapes.Pill,
        modifier = modifier.heightIn(min = WaiveraSpacing.MinTouchTarget),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .background(colors.surface, WaiveraShapes.Pill)
                .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Pill)
                .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(ChatIcons.ChevronDown),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
            Text(
                text = stringResource(R.string.feature_chat_jump_to_latest),
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
            )
        }
    }
}

/**
 * The first-load skeleton, shown only once loading has taken longer than the design-system delay
 * (~300 ms, 10 §3) so fast loads never flash it. [delayMillis] overrides it for screenshots.
 */
@Composable
fun DelayedListSkeleton(label: String, modifier: Modifier = Modifier, delayMillis: Long? = null) {
    val visible = if (delayMillis == null) {
        rememberSkeletonVisible(loading = true)
    } else {
        rememberSkeletonVisible(loading = true, delayMillis = delayMillis)
    }
    if (visible) {
        MessageListSkeleton(
            modifier = modifier
                .fillMaxSize()
                .semantics { contentDescription = label }
                .padding(vertical = WaiveraSpacing.Medium),
        )
    }
}

/** Shown at the top of the list while an older page loads, or with Retry when it failed. */
@Composable
fun OlderMessagesRow(failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (failed) {
        WaiveraBanner(
            message = stringResource(R.string.feature_chat_older_failed),
            tone = BannerTone.Warning,
            actionLabel = stringResource(R.string.feature_chat_retry),
            onAction = onRetry,
            modifier = modifier,
        )
    } else {
        MessageListSkeleton(modifier = modifier, rows = 2)
    }
}

/** A small inline spinner, for the call button while the lobby opens. */
@Composable
internal fun InlineSpinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(WaiveraSizes.ProgressSmall),
        strokeWidth = WaiveraSizes.ProgressStroke,
        color = WaiveraTheme.colors.accent,
    )
}

@WaiveraPreviews
@Composable
private fun ComposerPreview() {
    WaiveraTheme {
        Column {
            TypingSlot(label = "Priya is typing…")
            SendErrorBanner(kind = SendErrorKind.Offline, isReply = false, onDismiss = {})
            Composer(
                draft = "On my way",
                onDraftChange = {},
                config = ComposerConfig(isReply = false, allowImages = true, enabled = true),
                onSend = {},
                onAttach = {},
                onRemoveImage = {},
                onCancelEdit = {},
            )
            ReadOnlyNotice(reason = ReadOnlyReason.Automated, isThread = false)
            CallBanner(onJoin = {})
            MessageListSkeleton()
        }
    }
}
