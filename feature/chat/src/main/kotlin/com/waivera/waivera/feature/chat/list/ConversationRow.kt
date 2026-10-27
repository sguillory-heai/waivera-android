package com.waivera.waivera.feature.chat.list

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBusyIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraCountBadge
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.feature.chat.R
import kotlinx.coroutines.delay

/**
 * One inbox row: leading tile or avatar, title (wraps to two lines at large font scales), time,
 * preview with sender prefix or the typing/call override, and the unread badge. Unread is two
 * signals only, a bold title and the purple count badge (which TalkBack reads as "N unread");
 * no row wash, avatar dot or coloured time on top. Swipe actions, the long-press action sheet and
 * TalkBack custom actions all offer the same actions.
 */
@Composable
fun ConversationRow(
    row: ChatRowUi,
    avatar: ImageBitmap?,
    onClick: () -> Unit,
    onShowActions: () -> Unit,
    onJoinCall: () -> Unit,
    onMarkRead: () -> Unit,
    onOpenProfile: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val markReadLabel = stringResource(R.string.feature_chat_list_action_mark_read)
    val profileLabel = stringResource(R.string.feature_chat_list_action_profile)
    val joinLabel = stringResource(R.string.feature_chat_list_call_join_action)
    val actionsLabel = stringResource(R.string.feature_chat_list_actions, row.title)
    val profileUserId = row.profileUserId
    SwipeActionsBox(
        start = if (row.isUnread) {
            SwipeAction(
                label = markReadLabel,
                icon = WaiveraIcons.MarkRead,
                container = colors.accent,
                content = colors.onAccent,
                onTrigger = onMarkRead,
            )
        } else {
            null
        },
        end = profileUserId?.let {
            SwipeAction(profileLabel, WaiveraIcons.Person, colors.accentSoft, colors.onAccentSoft) { onOpenProfile(it) }
        },
        modifier = modifier,
    ) {
        WaiveraPressable(
            onClick = onClick,
            onClickLabel = stringResource(R.string.feature_chat_list_open_action),
            onLongClick = onShowActions,
            onLongClickLabel = actionsLabel,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .semantics {
                    // The unread badge announces the count; these are the swipe actions as buttons.
                    customActions = buildList {
                        if (row.isUnread) {
                            add(
                                CustomAccessibilityAction(markReadLabel) {
                                    onMarkRead()
                                    true
                                },
                            )
                        }
                        if (row.isCallLive) {
                            add(
                                CustomAccessibilityAction(joinLabel) {
                                    onJoinCall()
                                    true
                                },
                            )
                        }
                        if (profileUserId != null) {
                            add(
                                CustomAccessibilityAction(profileLabel) {
                                    onOpenProfile(profileUserId)
                                    true
                                },
                            )
                        }
                    }
                },
        ) {
            ArrivalWash(row.arrivalKey, Modifier.matchParentSize())
            RowBody(row = row, avatar = avatar, onJoinCall = onJoinCall)
        }
    }
}

@Composable
private fun RowBody(row: ChatRowUi, avatar: ImageBitmap?, onJoinCall: () -> Unit) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.Top,
    ) {
        LeadingTile(row.leading, avatar)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (row.isUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.isReadOnly) {
                    Icon(
                        painter = painterResource(WaiveraIcons.Lock),
                        contentDescription = stringResource(R.string.feature_chat_list_read_only),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(WaiveraSizes.IconSmall),
                    )
                }
                if (row.timeLabel.isNotEmpty()) {
                    Text(
                        text = row.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        row.isCallLive -> CallJoin(opening = row.isOpeningCall, onJoin = onJoinCall)
                        row.isSendFailed -> NotSentLine()
                        row.typingLabel != null -> Text(
                            text = row.typingLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        else -> PreviewLine(row)
                    }
                }
                if (row.isUnread) WaiveraCountBadge(count = row.unreadCount)
            }
        }
    }
}

/** In place of the preview: a message of mine never went out, so say so before the chat is opened. */
@Composable
private fun NotSentLine() {
    val colors = WaiveraTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(WaiveraIcons.Error),
            contentDescription = null,
            tint = colors.dangerText,
            modifier = Modifier.size(WaiveraSizes.IconSmall),
        )
        Text(
            text = stringResource(R.string.feature_chat_list_not_sent),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.dangerText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewLine(row: ChatRowUi) {
    val colors = WaiveraTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.hasAttachment) {
            Icon(
                painter = painterResource(WaiveraIcons.Attachment),
                contentDescription = stringResource(R.string.feature_chat_list_attachment),
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
        }
        val prefix = row.senderPrefix?.let { stringResource(R.string.feature_chat_list_preview_sender, it) }
        val text = remember(prefix, row.preview, row.previewKind) {
            buildAnnotatedString {
                if (prefix != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(prefix) }
                    append(' ')
                }
                if (row.previewKind == PreviewKind.Placeholder || row.previewKind == PreviewKind.System) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(row.preview) }
                } else {
                    append(row.preview)
                }
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (row.isUnread) colors.text else colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "Call in progress · Join" as a tonal pill with a 48dp target. Its own tap target, so tapping
 * it never opens the conversation.
 */
@Composable
private fun CallJoin(opening: Boolean, onJoin: () -> Unit) {
    val colors = WaiveraTheme.colors
    val label = stringResource(R.string.feature_chat_list_call_join)
    val openingLabel = stringResource(R.string.feature_chat_list_call_opening)
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(WaiveraShapes.Pill)
            .background(colors.accentSoft)
            .clickable(
                enabled = !opening,
                onClickLabel = stringResource(R.string.feature_chat_list_call_join_action),
                onClick = onJoin,
            )
            .semantics {
                role = Role.Button
                contentDescription = if (opening) openingLabel else label
            }
            .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(WaiveraSpacing.Small).background(colors.accent, WaiveraShapes.Circle))
        if (opening) {
            WaiveraBusyIndicator(color = colors.onAccentSoft, size = WaiveraSizes.IconSmall)
        } else {
            Icon(
                painter = painterResource(WaiveraIcons.Video),
                contentDescription = null,
                tint = colors.onAccentSoft,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
        }
        // At large font "· Join" would wrap onto its own line; the pill itself joins, and its
        // content description still says so (UI3-11).
        Text(
            text = if (isLargeFontScale()) stringResource(R.string.feature_chat_call_in_progress) else label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = colors.onAccentSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LeadingTile(leading: RowLeading, avatar: ImageBitmap?) {
    Box {
        when (leading) {
            is RowLeading.Channel -> IconTile(
                icon = if (leading.archived) {
                    WaiveraIcons.Archive
                } else {
                    WaiveraIcons.Hash
                },
                description = stringResource(
                    if (leading.archived) {
                        R.string.feature_chat_list_archived_icon
                    } else {
                        R.string.feature_chat_list_channel_icon
                    },
                ),
                accent = true,
            )
            RowLeading.Bot -> IconTile(
                icon = WaiveraIcons.Bot,
                description = stringResource(R.string.feature_chat_list_bot_icon),
                accent = true,
            )
            RowLeading.Group -> IconTile(
                icon = WaiveraIcons.People,
                description = stringResource(R.string.feature_chat_list_group_icon),
                accent = false,
            )
            is RowLeading.Direct -> {
                val painter = remember(avatar) { avatar?.let(::BitmapPainter) }
                // The row title already announces the name.
                WaiveraAvatar(
                    name = leading.name,
                    painter = painter,
                    size = WaiveraSizes.IconTile,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun IconTile(icon: Int, description: String, accent: Boolean) {
    val colors = WaiveraTheme.colors
    Box(
        modifier = Modifier
            .size(WaiveraSizes.IconTile)
            .background(if (accent) colors.accentWash else colors.surfaceMuted, WaiveraShapes.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (accent) colors.accent else colors.textSecondary,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
    }
}

/**
 * A brief accent wash when a new message moves this row (spec 02 §4 "Arrival pulse"), built
 * from the state motion token; with animations off it is a short cross-fade.
 */
@Composable
private fun ArrivalWash(arrivalKey: Long?, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(arrivalKey) {
        if (arrivalKey == null) return@LaunchedEffect
        alpha.animateTo(PULSE_ALPHA, motion.state())
        delay(PULSE_HOLD_MILLIS)
        alpha.animateTo(0f, motion.state())
    }
    Box(modifier = modifier.drawBehind { drawRect(colors.accent, alpha = alpha.value) })
}

private const val PULSE_ALPHA = 0.24f
private const val PULSE_HOLD_MILLIS = 360L

@WaiveraPreviews
@Composable
private fun ConversationRowPreview() {
    WaiveraTheme {
        Column {
            ChatListPreviewData.rows.forEach { row ->
                ConversationRow(
                    row = row,
                    avatar = null,
                    onClick = {},
                    onShowActions = {},
                    onJoinCall = {},
                    onMarkRead = {},
                    onOpenProfile = {},
                )
            }
        }
    }
}
