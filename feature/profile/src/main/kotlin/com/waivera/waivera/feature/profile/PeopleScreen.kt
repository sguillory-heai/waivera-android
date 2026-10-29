package com.waivera.waivera.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraLoadSwap
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.component.waiveraCardGroupItem
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Entry point for the people screen. Owned by `:feature:profile`; routed by `:app`. */
@Composable
fun PeopleScreen(onBack: () -> Unit, onOpenPerson: (userId: String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PeopleViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PeopleContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onRefresh = viewModel::refresh,
        onRetryConnection = viewModel::retryConnection,
        onOpenPerson = onOpenPerson,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleContent(
    state: PeopleUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenPerson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshLabel = stringResource(R.string.feature_profile_people_refresh)
    Column(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_profile_people_title),
            onBack = onBack,
        )
        ConnectionBanner(
            state = bannerFor(state.connection, showsLoadError = state.phase == ListPhase.Error),
            onRetry = onRetryConnection,
            offlineMessage = stringResource(R.string.feature_profile_people_offline_saved),
        )
        ProfileSearchField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.feature_profile_people_search),
            clearDescription = stringResource(R.string.feature_profile_people_search_clear),
        )
        if (showsRefreshFailed(state.connection, state.error != null && state.phase == ListPhase.Content)) {
            WaiveraBanner(
                message = stringResource(R.string.feature_profile_people_refresh_failed),
                tone = BannerTone.Warning,
                actionLabel = stringResource(R.string.feature_profile_try_again),
                onAction = onRefresh,
                actionIcon = WaiveraIcons.Refresh,
            )
        }
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.phase != ListPhase.Loading,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .refreshAction(refreshLabel, onRefresh),
        ) {
            // What loads settles in over the skeleton instead of cutting (M1-16).
            WaiveraLoadSwap(loading = state.phase == ListPhase.Loading, modifier = Modifier.fillMaxSize()) { loading ->
                when {
                    loading -> if (rememberDelayedSkeleton(loading = true)) PeopleSkeleton()
                    state.phase == ListPhase.Error -> WaiveraErrorState(
                        title = stringResource(R.string.feature_profile_people_error_title),
                        message = stringResource(requireNotNull(state.error).messageRes),
                        onRetry = onRefresh,
                        modifier = Modifier.padding(top = WaiveraSpacing.Large),
                    )
                    state.phase == ListPhase.Empty -> PeopleEmpty(
                        searching = state.totalCount > 0,
                        onClear = { onQueryChange("") },
                    )
                    else -> PeopleList(state, onOpenPerson)
                }
            }
        }
    }
}

@Composable
private fun PeopleEmpty(searching: Boolean, onClear: () -> Unit) {
    // Wrapped in a scrolling list so pull-to-refresh still works on an empty screen.
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (searching) {
                WaiveraEmptyState(
                    title = stringResource(R.string.feature_profile_people_no_match_title),
                    message = stringResource(R.string.feature_profile_people_no_match_message),
                    illustration = WaiveraIllustration.Search,
                    actionLabel = stringResource(R.string.feature_profile_clear_search),
                    onAction = onClear,
                    modifier = Modifier.padding(top = WaiveraSpacing.Large),
                )
            } else {
                WaiveraEmptyState(
                    title = stringResource(R.string.feature_profile_people_empty_title),
                    message = stringResource(R.string.feature_profile_people_empty_message),
                    illustration = WaiveraIllustration.Connections,
                    modifier = Modifier.padding(top = WaiveraSpacing.Large),
                )
            }
        }
    }
}

@Composable
private fun PeopleList(state: PeopleUiState, onOpenPerson: (String) -> Unit) {
    val colors = WaiveraTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
    ) {
        item(key = "meta") {
            Text(
                text = if (state.query.isBlank()) {
                    pluralStringResource(R.plurals.feature_profile_people_count, state.totalCount, state.totalCount)
                } else {
                    pluralStringResource(
                        R.plurals.feature_profile_people_count_filtered,
                        state.totalCount,
                        state.rows.size,
                        state.totalCount,
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(
                    start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
                    end = WaiveraSpacing.Gutter,
                    top = WaiveraSpacing.ExtraSmall,
                    bottom = WaiveraSpacing.Small,
                ),
            )
        }
        // The people sit on one card, as concept A groups rows; a divider runs between them.
        itemsIndexed(state.rows, key = { _, row -> row.userId }) { index, row ->
            Column(
                modifier = Modifier.waiveraCardGroupItem(
                    index = index,
                    count = state.rows.size,
                    surface = colors.surface,
                ),
            ) {
                PersonListRow(row = row, onClick = { onOpenPerson(row.userId) })
                if (index < state.rows.lastIndex) WaiveraInsetDivider()
            }
        }
    }
}

@Composable
private fun PersonListRow(row: PersonRow, onClick: () -> Unit) {
    // The milestone gets its own line and the email one unbroken line, so an address never
    // splits at a dot next to "5-year anniversary" (UI3-14).
    WaiveraListRow(
        headline = row.name,
        supporting = row.celebration?.compactLabel(),
        detail = row.detail,
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_profile_open_person),
        showChevron = true,
        leading = {
            WaiveraAvatar(
                name = row.name,
                imageModel = AvatarModel.of(row.userId),
                size = WaiveraSizes.AvatarMedium,
                milestone = row.celebration?.milestoneKind,
                contentDescription = null,
            )
        },
    )
}

@WaiveraPreviews
@Composable
private fun PeopleScreenPreview() {
    WaiveraTheme {
        PeopleContent(
            state = PeopleUiState(
                rows = listOf(
                    PersonRow("1", "Priya Raman", "priya.raman@example.com", Celebration(true, null)),
                    PersonRow("2", "Jordan Lee", "jordan.lee@example.com", null),
                ),
                totalCount = 2,
                phase = ListPhase.Content,
                connection = ConnectionBannerState.Connected,
            ),
            onBack = {},
            onQueryChange = {},
            onRefresh = {},
            onRetryConnection = {},
            onOpenPerson = {},
        )
    }
}

@WaiveraPreviews
@Composable
private fun PeopleEmptyPreview() {
    WaiveraTheme {
        PeopleContent(
            state = PeopleUiState(phase = ListPhase.Empty),
            onBack = {},
            onQueryChange = {},
            onRefresh = {},
            onRetryConnection = {},
            onOpenPerson = {},
        )
    }
}
