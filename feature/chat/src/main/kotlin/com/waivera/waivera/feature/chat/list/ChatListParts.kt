package com.waivera.waivera.feature.chat.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.component.SkeletonAvatar
import com.waivera.waivera.core.designsystem.component.SkeletonLine
import com.waivera.waivera.core.designsystem.component.WaiveraDivider
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R

/** Dot plus label under the title (spec 02 §1.2); colour is never the only signal. */
@Composable
fun ConnectionPill(connection: ConnectionUi, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val (dot: Color, textColor: Color, labelRes: Int) = when (connection) {
        ConnectionUi.Online -> Triple(colors.success, colors.successText, R.string.feature_chat_list_status_online)
        ConnectionUi.Reconnecting ->
            Triple(colors.warning, colors.warningText, R.string.feature_chat_list_status_reconnecting)
        ConnectionUi.Offline -> Triple(colors.danger, colors.dangerText, R.string.feature_chat_list_status_offline)
        ConnectionUi.Connecting ->
            Triple(colors.textSecondary, colors.textSecondary, R.string.feature_chat_list_status_connecting)
    }
    val label = stringResource(labelRes)
    val description = stringResource(R.string.feature_chat_list_status_description, label)
    Row(
        modifier = modifier
            .background(dot.copy(alpha = PILL_FILL_ALPHA), WaiveraShapes.Pill)
            .border(WaiveraSpacing.Hairline, dot.copy(alpha = PILL_BORDER_ALPHA), WaiveraShapes.Pill)
            .padding(horizontal = WaiveraSpacing.Small + WaiveraSpacing.XxSmall, vertical = WaiveraSpacing.ExtraSmall)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(WaiveraSpacing.Small).background(dot, WaiveraShapes.Circle))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

private const val PILL_FILL_ALPHA = 0.08f
private const val PILL_BORDER_ALPHA = 0.22f

/** Local search over titles and previews; no debounce and no server search (spec 02 §1.3). */
@Composable
fun ChatSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val label = stringResource(R.string.feature_chat_list_search_label)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .semantics { contentDescription = label },
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = {
            Text(
                text = stringResource(R.string.feature_chat_list_search_placeholder),
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                // One line like the query that replaces it, so the field keeps its height at 200%.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(WaiveraIcons.Search),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.Icon),
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                WaiveraIconButton(
                    icon = WaiveraIcons.Close,
                    contentDescription = stringResource(R.string.feature_chat_list_search_clear),
                    onClick = { onQueryChange("") },
                    tint = colors.textSecondary,
                )
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        shape = WaiveraShapes.Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.border,
            cursorColor = colors.accent,
            focusedTextColor = colors.text,
            unfocusedTextColor = colors.text,
        ),
    )
}

/** Six rows of avatar, two text bones of varying width and a time bone (spec 02 §8). */
@Composable
fun ChatListSkeleton(modifier: Modifier = Modifier, rows: Int = SKELETON_ROWS) {
    val pulse = rememberSkeletonPulse()
    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics { }) {
        repeat(rows) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WaiveraSizes.ListRowMinHeight)
                    .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonAvatar(size = WaiveraSizes.IconTile, pulse = pulse)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                    SkeletonLine(widthFraction = TITLE_WIDTHS[index % TITLE_WIDTHS.size], pulse = pulse)
                    SkeletonLine(widthFraction = PREVIEW_WIDTHS[index % PREVIEW_WIDTHS.size], pulse = pulse)
                }
                SkeletonLine(modifier = Modifier.width(WaiveraSpacing.ExtraLarge), pulse = pulse)
            }
        }
    }
}

private const val SKELETON_ROWS = 6
private val TITLE_WIDTHS = listOf(0.52f, 0.38f, 0.61f, 0.45f, 0.33f, 0.56f)
private val PREVIEW_WIDTHS = listOf(0.82f, 0.68f, 0.74f, 0.9f, 0.6f, 0.77f)

/** Divider inset to the text column, past the leading tile. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    WaiveraDivider(
        modifier = modifier.padding(
            start = WaiveraSpacing.Gutter + WaiveraSizes.IconTile + WaiveraSpacing.MediumSmall,
        ),
    )
}

/**
 * The button alternative to every swipe action, opened by long press or the TalkBack action. It
 * opens with the conversation's own avatar and title, as the inbox row shows them, so the sheet
 * says which conversation its actions touch (UI3-17), like the message sheet's preview.
 */
@Composable
fun RowActionsContent(
    row: ChatRowUi,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onJoinCall: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    avatar: ImageBitmap? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WaiveraListRow(
            headline = row.title,
            leading = { LeadingTile(row.leading, avatar) },
            modifier = Modifier.semantics { heading() },
        )
        WaiveraDivider()
        WaiveraListRow(
            headline = stringResource(R.string.feature_chat_list_open_action),
            onClick = onOpen,
            leading = { ActionIcon(WaiveraIcons.Chat) },
        )
        if (row.isUnread) {
            WaiveraListRow(
                headline = stringResource(R.string.feature_chat_list_action_mark_read),
                onClick = onMarkRead,
                leading = { ActionIcon(WaiveraIcons.MarkRead) },
            )
        }
        if (row.isCallLive) {
            WaiveraListRow(
                headline = stringResource(R.string.feature_chat_list_call_join_action),
                onClick = onJoinCall,
                leading = { ActionIcon(WaiveraIcons.Video) },
            )
        }
        row.profileUserId?.let { userId ->
            WaiveraListRow(
                headline = stringResource(R.string.feature_chat_list_action_profile),
                onClick = { onOpenProfile(userId) },
                leading = { ActionIcon(WaiveraIcons.Person) },
            )
        }
    }
}

/** The shared icon tile, so the row actions read like every other list of actions. */
@Composable
private fun ActionIcon(icon: Int) {
    WaiveraIconTile(icon = icon)
}

@WaiveraPreviews
@Composable
private fun ChatListPartsPreview() {
    WaiveraTheme {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
            ConnectionUi.entries.forEach { ConnectionPill(it) }
            ChatSearchField(query = "", onQueryChange = {})
            ChatListSkeleton(rows = 2)
            RowActionsContent(
                row = ChatListPreviewData.rows.first(),
                onOpen = {},
                onMarkRead = {},
                onJoinCall = {},
                onOpenProfile = {},
            )
            RowActionsContent(
                row = ChatListPreviewData.rows.last(),
                onOpen = {},
                onMarkRead = {},
                onJoinCall = {},
                onOpenProfile = {},
            )
        }
    }
}
