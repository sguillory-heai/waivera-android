package com.waivera.waivera.feature.chat.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.SkeletonListRow
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraBusyIndicator
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.rememberSkeletonVisible
import com.waivera.waivera.core.designsystem.component.waiveraCardGroupItem
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.exit
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.message.ConnectionUi

/**
 * Entry point for a group's members (v7, item 4). Owned by `:feature:chat`; routed by `:app`,
 * which opens a person's page and a direct chat through its own routes.
 *
 * A full screen rather than a sheet: a row opens the person's page and Message opens a chat,
 * both pushed screens, and back from either has to land on this list again. A sheet would close
 * under them and lose the search; a screen keeps its place in the tab's back stack, gets
 * predictive back like every other screen, and leaves the conversation's own sheets alone.
 */
@Composable
fun GroupMembersScreen(
    conversationId: Long,
    onBack: () -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        hiltViewModel<GroupMembersViewModel, GroupMembersViewModel.Factory>(key = "members-$conversationId") {
            it.create(conversationId)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpenConversation)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GroupMembersEffect.OpenConversation -> open(effect.conversationId)
            }
        }
    }
    GroupMembersContent(
        state = state,
        actions = GroupMembersActions(
            onBack = onBack,
            onQueryChange = viewModel::onQueryChange,
            onRetry = viewModel::refresh,
            onRetryConnection = viewModel::retryConnection,
            onOpenPerson = onOpenPerson,
            onMessage = viewModel::onMessage,
            onDismissMessageError = viewModel::dismissMessageError,
        ),
        modifier = modifier,
    )
}

/** What the members screen's controls do, so [GroupMembersContent] stays previewable. */
@Immutable
class GroupMembersActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryConnection: () -> Unit = {},
    val onOpenPerson: (String) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onDismissMessageError: () -> Unit = {},
)

/**
 * The members screen without its ViewModel: "Members" with the count under it, a search on a long
 * list, and everyone on one card, me first. Loading settles in over row skeletons after ~300 ms;
 * a failed load and an empty list get the shared states.
 */
@Composable
fun GroupMembersContent(
    state: GroupMembersUiState,
    actions: GroupMembersActions,
    modifier: Modifier = Modifier,
    skeletonDelayMillis: Long? = null,
) {
    val loading = state.phase == MembersPhase.Loading
    val showSkeleton = skeletonDelayMillis?.let { rememberSkeletonVisible(loading, delayMillis = it) }
        ?: rememberSkeletonVisible(loading)
    Column(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_chat_members_title),
            subtitle = state.totalCount.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.feature_chat_members, it, it)
            },
            onBack = actions.onBack,
        )
        // One connection signal: a failed load already explains itself.
        ConnectionBanner(
            state = if (state.phase == MembersPhase.Error) ConnectionBannerState.Connected else state.connection.banner,
            onRetry = actions.onRetryConnection,
            offlineMessage = stringResource(R.string.feature_chat_members_offline_saved),
        )
        if (state.showsSearch) {
            MembersSearchField(query = state.query, onQueryChange = actions.onQueryChange)
        }
        state.messageErrorRes?.let { res ->
            WaiveraBanner(
                message = stringResource(res),
                tone = BannerTone.Error,
                onDismiss = actions.onDismissMessageError,
            )
        }
        // What loads settles in over the skeleton instead of cutting (M1-16).
        WaiveraLoadSwap(loading = loading, modifier = Modifier.weight(1f).fillMaxSize()) { showsLoading ->
            when {
                showsLoading -> if (showSkeleton) MembersSkeleton()
                state.phase == MembersPhase.Error -> WaiveraErrorState(
                    title = stringResource(R.string.feature_chat_members_error_title),
                    message = stringResource(state.errorRes ?: R.string.feature_chat_members_error_title),
                    onRetry = actions.onRetry,
                    modifier = Modifier.padding(top = WaiveraSpacing.Large),
                )
                state.phase == MembersPhase.Empty -> MembersEmpty(
                    noMatch = state.isNoMatch,
                    onClear = { actions.onQueryChange("") },
                )
                else -> MembersList(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun MembersSearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = WaiveraTheme.colors
    WaiveraTextField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.feature_chat_members_search_label),
        placeholder = stringResource(R.string.feature_chat_members_search_placeholder),
        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.Small),
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
                    contentDescription = stringResource(R.string.feature_chat_members_search_clear),
                    onClick = { onQueryChange("") },
                    tint = colors.textSecondary,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun MembersList(state: GroupMembersUiState, actions: GroupMembersActions) {
    val colors = WaiveraTheme.colors
    val motion = WaiveraTheme.motion
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(MEMBERS_LIST_TAG),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        // The members sit on one card, as concept A groups rows; a divider runs between them.
        itemsIndexed(state.rows, key = { _, row -> row.userId }) { index, row ->
            Column(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = motion.state(),
                        placementSpec = motion.layout(),
                        fadeOutSpec = motion.exit(),
                    )
                    .waiveraCardGroupItem(index = index, count = state.rows.size, surface = colors.surface),
            ) {
                MemberRow(
                    row = row,
                    opening = state.openingChatWith == row.userId,
                    busy = state.openingChatWith != null,
                    onOpen = { actions.onOpenPerson(row.userId) },
                    onMessage = { actions.onMessage(row.userId) },
                )
                if (index < state.rows.lastIndex) WaiveraInsetDivider()
            }
        }
    }
}

/**
 * One member: a soft rounded-square avatar, the name on one line ([WaiveraListRow]'s one-line
 * name, v7 item 3) and Message at the end. My own row says "You" there instead: there is no
 * chat with myself to open. The row opens the person's page.
 */
@Composable
internal fun MemberRow(
    row: MemberRowUi,
    opening: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    val name = row.name.ifBlank { stringResource(R.string.feature_chat_members_unknown) }
    WaiveraListRow(
        headline = name,
        oneLineName = true,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.feature_chat_members_open_person),
        modifier = modifier.testTag(MEMBER_ROW_TAG_PREFIX + row.userId),
        leading = {
            WaiveraAvatar(
                name = name,
                imageModel = AvatarModel.of(row.userId),
                size = WaiveraSizes.AvatarMedium,
                contentDescription = null,
            )
        },
        trailing = {
            when {
                row.isMe -> WaiveraStatusBadge(
                    text = stringResource(R.string.feature_chat_members_you),
                    tone = BadgeTone.Accent,
                )
                opening -> {
                    val label = stringResource(R.string.feature_chat_members_opening, name)
                    Box(
                        modifier = Modifier
                            .size(WaiveraSizes.IconButton)
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { WaiveraBusyIndicator() }
                }
                else -> WaiveraIconButton(
                    icon = WaiveraIcons.Chat,
                    contentDescription = stringResource(R.string.feature_chat_members_message, name),
                    onClick = onMessage,
                    enabled = !busy,
                    tint = colors.accent,
                )
            }
        },
    )
}

@Composable
private fun MembersSkeleton() {
    val pulse = rememberSkeletonPulse()
    val caption = stringResource(R.string.feature_chat_members_loading)
    val colors = WaiveraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = caption },
    ) {
        repeat(SKELETON_ROWS) { index ->
            SkeletonListRow(
                pulse = pulse,
                modifier = Modifier.waiveraCardGroupItem(
                    index = index,
                    count = SKELETON_ROWS,
                    surface = colors.surface,
                ),
            )
        }
    }
}

@Composable
private fun MembersEmpty(noMatch: Boolean, onClear: () -> Unit) {
    if (noMatch) {
        WaiveraEmptyState(
            title = stringResource(R.string.feature_chat_members_no_match_title),
            message = stringResource(R.string.feature_chat_members_no_match_message),
            illustration = WaiveraIllustration.Search,
            actionLabel = stringResource(R.string.feature_chat_members_search_clear),
            onAction = onClear,
            modifier = Modifier.padding(top = WaiveraSpacing.Large),
        )
    } else {
        WaiveraEmptyState(
            title = stringResource(R.string.feature_chat_members_empty_title),
            message = stringResource(R.string.feature_chat_members_empty_message),
            illustration = WaiveraIllustration.Connections,
            modifier = Modifier.padding(top = WaiveraSpacing.Large),
        )
    }
}

private const val SKELETON_ROWS = 6

/** Test tags: the list, and each member's row by user id. */
internal const val MEMBERS_LIST_TAG = "group_members_list"
internal const val MEMBER_ROW_TAG_PREFIX = "group_member_"

/** Sample states for previews and screenshot tests, with the long-name fixtures' shapes. */
internal object GroupMembersPreviewData {
    val rows = listOf(
        MemberRowUi("usr_alex_rivera", "Alex Rivera", isMe = true),
        MemberRowUi("usr_jordan_lee", "Jordan Lee", isMe = false),
        MemberRowUi("usr_mf", "Maria-Fernanda Alejandra Castellanos-Whitaker de la Vega Rui", isMe = false),
        MemberRowUi("usr_priya_raman", "Priya Raman", isMe = false),
        MemberRowUi("usr_w40", "Wolfeschlegelsteinhausenbergerdorffvoral", isMe = false),
    )

    val content = GroupMembersUiState(phase = MembersPhase.Content, rows = rows, totalCount = rows.size)

    /** Twelve members: past the search threshold. */
    val long = GroupMembersUiState(
        phase = MembersPhase.Content,
        rows = rows + (1..7).map { MemberRowUi("usr_$it", "Care worker $it", isMe = false) },
        totalCount = rows.size + 7,
    )

    val loading = GroupMembersUiState()
    val empty = GroupMembersUiState(phase = MembersPhase.Empty)
    val error = GroupMembersUiState(
        phase = MembersPhase.Error,
        errorRes = R.string.feature_chat_members_error_title,
        connection = ConnectionUi(ConnectionBannerState.Offline),
    )
}

@WaiveraPreviews
@Composable
private fun GroupMembersPreview() {
    WaiveraTheme { GroupMembersContent(state = GroupMembersPreviewData.content, actions = GroupMembersActions()) }
}

@WaiveraPreviews
@Composable
private fun GroupMembersSearchPreview() {
    WaiveraTheme { GroupMembersContent(state = GroupMembersPreviewData.long, actions = GroupMembersActions()) }
}

@WaiveraPreviews
@Composable
private fun GroupMembersLoadingPreview() {
    WaiveraTheme {
        GroupMembersContent(
            state = GroupMembersPreviewData.loading,
            actions = GroupMembersActions(),
            skeletonDelayMillis = 0,
        )
    }
}

@WaiveraPreviews
@Composable
private fun GroupMembersEmptyPreview() {
    WaiveraTheme { GroupMembersContent(state = GroupMembersPreviewData.empty, actions = GroupMembersActions()) }
}

@WaiveraPreviews
@Composable
private fun GroupMembersErrorPreview() {
    WaiveraTheme { GroupMembersContent(state = GroupMembersPreviewData.error, actions = GroupMembersActions()) }
}
