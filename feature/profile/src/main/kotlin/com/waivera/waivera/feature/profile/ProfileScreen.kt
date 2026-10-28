package com.waivera.waivera.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.core.network.error.WaiveraError

/**
 * Entry point for the profile screen. Owned by `:feature:profile`; routed by `:app`.
 *
 * [onOpenSettings] adds a Settings row at the end; without it the row is hidden.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val viewModel: ProfileViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetryConnection = viewModel::retryConnection,
        onOpenPerson = onOpenPerson,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetryConnection: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        WaiveraTopBar(
            title = stringResource(R.string.feature_profile_profile_title),
            onBack = onBack,
        )
        when (state) {
            ProfileUiState.Unavailable -> WaiveraEmptyState(
                title = stringResource(R.string.feature_profile_unavailable_title),
                message = stringResource(R.string.feature_profile_unavailable_message),
                icon = WaiveraIcons.Person,
                modifier = Modifier.padding(top = WaiveraSpacing.Large),
            )
            is ProfileUiState.Ready -> {
                val details = state.details
                ConnectionBanner(
                    state = bannerFor(state.connection, showsLoadError = details is Section.Failed),
                    onRetry = onRetryConnection,
                    offlineMessage = stringResource(R.string.feature_profile_offline_saved),
                )
                if (details is Section.Failed) {
                    WaiveraErrorState(
                        title = stringResource(R.string.feature_profile_error_title),
                        message = stringResource(details.error.messageRes),
                        onRetry = onRefresh,
                        modifier = Modifier.padding(top = WaiveraSpacing.Large),
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        ProfileBody(state, onRefresh, onOpenPerson, onOpenSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileBody(
    state: ProfileUiState.Ready,
    onRefresh: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    val profile = (state.details as? Section.Ready)?.value
    val refreshLabel = stringResource(R.string.feature_profile_refresh)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .refreshAction(refreshLabel, onRefresh)
            .verticalScroll(rememberScrollState())
            .padding(top = WaiveraSpacing.MediumSmall),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        if (showsRefreshFailed(state.connection, state.detailsRefreshError != null)) {
            WaiveraBanner(
                message = stringResource(R.string.feature_profile_refresh_failed),
                tone = BannerTone.Warning,
                actionLabel = stringResource(R.string.feature_profile_try_again),
                onAction = onRefresh,
                actionIcon = WaiveraIcons.Refresh,
            )
        }
        IdentityCard(user = state.user, profile = profile, celebration = state.celebration)
        DetailsSections(profile = profile, state = state, onOpenPerson = onOpenPerson)
        TimeOffSection(section = state.timeOff, onRetry = onRefresh)
        FilesSection(section = state.files, zone = state.zone, onRetry = onRefresh)
        OrganizationSection(section = state.organization, onRetry = onRefresh)
        if (onOpenSettings != null) {
            ProfileCard {
                WaiveraListRow(
                    headline = stringResource(R.string.feature_profile_settings_row),
                    supporting = stringResource(R.string.feature_profile_settings_row_detail),
                    onClick = onOpenSettings,
                    showChevron = true,
                    leading = { WaiveraIconTile(icon = WaiveraIcons.Settings) },
                )
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun IdentityCard(user: CurrentUser, profile: EmployeeProfile?, celebration: Celebration?) {
    val colors = WaiveraTheme.colors
    val name = profile?.displayName?.takeIf(String::isNotBlank)
        ?: listOfNotNull(profile?.firstName, profile?.lastName).joinToString(" ").takeIf(String::isNotBlank)
        ?: user.displayName?.takeIf(String::isNotBlank)
        ?: listOfNotNull(user.firstName, user.lastName).joinToString(" ")
    val jobTitle = profile?.positions?.firstOrNull { it.isNotBlank() }
        ?: user.roles.firstOrNull { it.isNotBlank() }?.let { ProfileFormat.roleLabel(it) }
    val email = profile?.email ?: user.email
    // Above 130% font a text column beside the avatar is too narrow for a name or an email, so
    // the avatar moves above the text and the text gets the card's full width.
    val stacked = LocalDensity.current.fontScale > HEADER_STACK_FONT_SCALE
    val avatar: @Composable () -> Unit = {
        WaiveraAvatar(
            name = name,
            imageModel = AvatarModel.of(user.userId),
            size = WaiveraSizes.AvatarLarge,
            milestone = celebration?.milestoneKind,
            contentDescription = null,
        )
    }
    val text: @Composable (Modifier) -> Unit = { textModifier ->
        Column(
            modifier = textModifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
        ) {
            // Hyphenated names wrap after a hyphen, never mid-word (UI5-05).
            Text(
                text = name.withBreaksAfterHyphens(),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
            )
            if (jobTitle != null) {
                Text(text = jobTitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            if (!email.isNullOrBlank()) {
                Text(text = email, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
    }
    ProfileCard {
        if (stacked) {
            Column(
                modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            ) {
                avatar()
                text(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                avatar()
                text(Modifier.weight(1f))
            }
        }
        if (celebration != null) {
            Text(
                text = celebration.fullLabel(name),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onAccentSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WaiveraSpacing.Medium)
                    .background(colors.accentSoft, WaiveraShapes.Chip)
                    .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
            )
        }
    }
}

@Composable
private fun DetailsSections(profile: EmployeeProfile?, state: ProfileUiState.Ready, onOpenPerson: (String) -> Unit) {
    val skeleton = rememberDelayedSkeleton(loading = profile == null)
    ProfileCard(title = stringResource(R.string.feature_profile_personal_title)) {
        if (profile == null) {
            if (skeleton) SectionSkeleton(lines = 4)
        } else {
            InfoRow(stringResource(R.string.feature_profile_email), profile.email ?: state.user.email)
            InfoRow(stringResource(R.string.feature_profile_phone), profile.phoneNumber ?: state.user.phoneNumber)
            InfoRow(stringResource(R.string.feature_profile_address), profile.address)
            InfoRow(
                stringResource(R.string.feature_profile_time_zone),
                stringResource(
                    R.string.feature_profile_time_zone_value,
                    ProfileFormat.zoneName(state.zone),
                    state.zone.id,
                ),
            )
        }
    }
    ProfileCard(title = stringResource(R.string.feature_profile_professional_title)) {
        if (profile == null) {
            if (skeleton) SectionSkeleton(lines = 5)
        } else {
            InfoRow(
                stringResource(R.string.feature_profile_hire_date),
                profile.hireDate?.let { ProfileFormat.date(it) },
            )
            InfoRow(stringResource(R.string.feature_profile_positions), profile.positions.joinedOrNull())
            InfoRow(stringResource(R.string.feature_profile_office_locations), profile.regions.joinedOrNull())
            InfoRow(stringResource(R.string.feature_profile_agencies), profile.agencies.joinedOrNull())
            InfoRow(stringResource(R.string.feature_profile_waiver_families), profile.waiverFamilies.joinedOrNull())
            InfoRow(stringResource(R.string.feature_profile_scid), profile.scId?.takeIf(String::isNotBlank))
            InfoRow(
                stringResource(R.string.feature_profile_birthday),
                profile.birthday?.let { ProfileFormat.birthday(it) },
            )
            if (profile.managers.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.feature_profile_reports_to).uppercase(),
                    style = WaiveraTextStyles.Eyebrow,
                    color = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.padding(
                        horizontal = WaiveraSpacing.Medium,
                        vertical = WaiveraSpacing.ExtraSmall,
                    ),
                )
                profile.managers.forEach { manager ->
                    WaiveraListRow(
                        headline = manager.name.ifBlank {
                            stringResource(R.string.feature_profile_celebration_someone)
                        },
                        onClick = { onOpenPerson(manager.id) },
                        onClickLabel = stringResource(R.string.feature_profile_open_person),
                        showChevron = true,
                        leading = {
                            WaiveraAvatar(
                                name = manager.name,
                                imageModel = AvatarModel.of(manager.id),
                                size = WaiveraSizes.AvatarSmall,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Small error inside one section; the rest of the profile stays usable. Quiet, like Today's
 * section errors (UI2-18): a muted icon, secondary text and a text Retry, so red stays for
 * something the person did that failed.
 *
 * At large font "Try again" moves to its own row under the message, end-aligned, as banner
 * actions do: beside the text it took over half the card and left the reason a one-word column
 * that split "conne / ction" (UI5-03).
 */
@Composable
internal fun SectionError(error: WaiveraError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val stacked = isLargeFontScale()
    val retry: @Composable () -> Unit = {
        WaiveraButton(
            text = stringResource(R.string.feature_profile_try_again),
            onClick = onRetry,
            variant = WaiveraButtonVariant.Text,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(start = WaiveraSpacing.Medium, end = WaiveraSpacing.Small)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (stacked) WaiveraSpacing.MediumSmall else WaiveraSpacing.None),
        ) {
            SectionErrorMessage(error = error, alignTop = stacked, modifier = Modifier.weight(1f))
            if (!stacked) retry()
        }
        if (stacked) {
            Box(modifier = Modifier.align(Alignment.End)) { retry() }
        }
    }
}

@Composable
private fun SectionErrorMessage(error: WaiveraError, alignTop: Boolean, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        // A wrapped reason keeps its icon by the first line, not floating mid-paragraph.
        verticalAlignment = if (alignTop) Alignment.Top else Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(WaiveraIcons.Error),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
        Text(
            text = stringResource(error.messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@WaiveraPreviews
@Composable
private fun ProfileScreenPreview() {
    WaiveraTheme {
        ProfileContent(
            state = ProfilePreviewData.ready(),
            onBack = {},
            onRefresh = {},
            onRetryConnection = {},
            onOpenPerson = {},
            onOpenSettings = {},
        )
    }
}

@WaiveraPreviews
@Composable
private fun ProfileUnavailablePreview() {
    WaiveraTheme {
        ProfileContent(
            state = ProfileUiState.Unavailable,
            onBack = {},
            onRefresh = {},
            onRetryConnection = {},
            onOpenPerson = {},
            onOpenSettings = null,
        )
    }
}

/** Sample state for previews only; screenshot tests build theirs from the demo fixtures. */
internal object ProfilePreviewData {
    fun ready(): ProfileUiState.Ready = ProfileUiState.Ready(
        user = CurrentUser(userId = "preview", displayName = "Alex Rivera", email = "alex.rivera@example.com"),
        details = Section.Ready(
            EmployeeProfile(
                employeeId = "preview",
                displayName = "Alex Rivera",
                email = "alex.rivera@example.com",
                positions = listOf("Support coordinator"),
                hireDate = java.time.LocalDate.parse("2022-03-14"),
                birthday = java.time.LocalDate.parse("1990-04-02"),
            ),
        ),
        detailsRefreshError = null,
        timeOff = Section.Ready(com.waivera.waivera.core.data.model.TimeOffSummary()),
        files = Section.Ready(emptyList()),
        organization = Section.Ready(OrganizationInfo(id = 1, name = "Waivera Care")),
        celebration = Celebration(birthday = true, anniversaryYears = null),
        isRefreshing = false,
        connection = ConnectionBannerState.Connected,
    )
}

private const val HEADER_STACK_FONT_SCALE = 1.3f
