package com.waivera.waivera.feature.chat.newchat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.SkeletonListRow
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R
import kotlinx.coroutines.flow.distinctUntilChanged

/** The new chat bottom sheet, wired to [NewChatViewModel]. */
@Composable
fun NewChatSheet(
    viewModel: NewChatViewModel,
    onDismiss: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val avatars by viewModel.avatars.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpenConversation)
    LaunchedEffect(viewModel) {
        viewModel.onOpened()
        viewModel.effects.collect { effect ->
            when (effect) {
                is NewChatEffect.OpenConversation -> open(effect.conversationId)
            }
        }
    }
    WaiveraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = stringResource(R.string.feature_chat_newchat_title),
    ) {
        NewChatSheetContent(
            state = state,
            avatars = avatars,
            onQueryChange = viewModel::onQueryChange,
            onToggle = viewModel::onToggle,
            onSubmit = viewModel::onSubmit,
            onRetryLoad = viewModel::onRetryLoad,
            onVisiblePeople = viewModel::onVisiblePeople,
        )
    }
}

/**
 * Sheet body: subtitle, staff search (names only, spec 02 §7), the selection count, the people
 * list with checkboxes, the error and the primary button. [WaiveraBottomSheet] owns the
 * keyboard and navigation bar insets, so nothing here pads for them again.
 */
@Composable
fun ColumnScope.NewChatSheetContent(
    state: NewChatUiState,
    avatars: Map<String, ImageBitmap>,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryLoad: () -> Unit,
    onVisiblePeople: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    skeletonDelayMillis: Long? = null,
) {
    val colors = WaiveraTheme.colors
    val showSkeleton = skeletonDelayMillis?.let { rememberSkeletonVisible(state.isLoading, delayMillis = it) }
        ?: rememberSkeletonVisible(state.isLoading)
    val listState = rememberLazyListState()
    val requestAvatars by rememberUpdatedState(onVisiblePeople)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .distinctUntilChanged()
            .collect { visible -> if (visible.isNotEmpty()) requestAvatars(visible) }
    }
    Column(
        modifier = modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        Text(
            text = stringResource(R.string.feature_chat_newchat_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        WaiveraTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.feature_chat_newchat_search_label),
            placeholder = stringResource(R.string.feature_chat_newchat_search_placeholder),
            leadingIcon = {
                Icon(
                    painter = painterResource(WaiveraIcons.Search),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            },
        )
        if (state.selectedCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.feature_chat_newchat_selected,
                    state.selectedCount,
                    state.selectedCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.accent,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Box(modifier = Modifier.weight(1f, fill = false)) {
            when {
                showSkeleton -> {
                    val pulse = rememberSkeletonPulse()
                    Column { repeat(SKELETON_ROWS) { SkeletonListRow(pulse = pulse) } }
                }
                state.loadErrorRes != null -> WaiveraErrorState(
                    title = stringResource(R.string.feature_chat_newchat_load_error_title),
                    message = stringResource(state.loadErrorRes),
                    onRetry = onRetryLoad,
                )
                !state.isLoading && state.people.isEmpty() -> WaiveraEmptyState(
                    title = stringResource(R.string.feature_chat_newchat_empty_title),
                    message = stringResource(R.string.feature_chat_newchat_empty),
                    icon = WaiveraIcons.People,
                )
                else -> LazyColumn(state = listState) {
                    items(state.people, key = { it.userId }) { person ->
                        PersonOption(
                            person = person,
                            avatar = avatars[person.userId],
                            onToggle = { onToggle(person.userId) },
                        )
                    }
                }
            }
        }
        state.errorRes?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        WaiveraButton(
            text = stringResource(
                when (state.action) {
                    NewChatAction.OpenChat -> R.string.feature_chat_newchat_open
                    NewChatAction.CreateGroup -> R.string.feature_chat_newchat_create_group
                },
            ),
            onClick = onSubmit,
            // Enabled while busy so the progress label is not dimmed; taps are ignored while loading.
            enabled = state.canSubmit || state.isCreating,
            loadingText = stringResource(
                when (state.action) {
                    NewChatAction.OpenChat -> R.string.feature_chat_newchat_opening
                    NewChatAction.CreateGroup -> R.string.feature_chat_newchat_creating
                },
            ).takeIf { state.isCreating },
            fullWidth = true,
            modifier = Modifier.padding(bottom = WaiveraSpacing.Small),
        )
    }
}

@Composable
private fun PersonOption(person: PersonOptionUi, avatar: ImageBitmap?, onToggle: () -> Unit) {
    val colors = WaiveraTheme.colors
    val painter = remember(avatar) { avatar?.let(::BitmapPainter) }
    val stateLabel = stringResource(
        if (person.selected) {
            R.string.feature_chat_newchat_selected_state
        } else {
            R.string.feature_chat_newchat_not_selected_state
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .toggleable(value = person.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = stateLabel }
            .padding(vertical = WaiveraSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaiveraAvatar(
            name = person.name,
            painter = painter,
            size = WaiveraSizes.AvatarMedium,
            contentDescription = null,
        )
        Text(
            text = person.name,
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(WaiveraSizes.Icon)
                .background(if (person.selected) colors.accent else colors.surface, WaiveraShapes.Chip)
                .border(
                    width = WaiveraSpacing.Hairline * 2,
                    color = if (person.selected) colors.accent else colors.borderStrong,
                    shape = WaiveraShapes.Chip,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (person.selected) {
                Icon(
                    painter = painterResource(WaiveraIcons.Check),
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
            }
        }
    }
}

private const val SKELETON_ROWS = 5

internal object NewChatPreviewData {
    val state = NewChatUiState(
        people = listOf(
            PersonOptionUi("usr_jordan_lee", "Jordan Lee", selected = true),
            PersonOptionUi("usr_mei_chen", "Mei Chen", selected = false),
            PersonOptionUi("usr_priya_raman", "Priya Raman", selected = true),
            PersonOptionUi("usr_sam_okafor", "Sam Okafor", selected = false),
        ),
        selectedCount = 2,
        isLoading = false,
    )
}

@WaiveraPreviews
@Composable
private fun NewChatSheetContentPreview() {
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.surface).padding(WaiveraSpacing.Gutter)) {
            NewChatSheetContent(
                state = NewChatPreviewData.state,
                avatars = emptyMap(),
                onQueryChange = {},
                onToggle = {},
                onSubmit = {},
                onRetryLoad = {},
                onVisiblePeople = {},
            )
        }
    }
}
