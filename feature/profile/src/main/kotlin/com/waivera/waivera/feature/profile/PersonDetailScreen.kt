package com.waivera.waivera.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.ConnectionBanner
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Entry point for the person screen. Owned by `:feature:profile`; routed by `:app`. */
@Composable
fun PersonDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory>(
        key = userId,
        creationCallback = { factory -> factory.create(userId) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberHaptics()
    val resources = LocalResources.current
    val openConversation by rememberUpdatedState(onOpenConversation)
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PersonDetailEffect.OpenConversation -> openConversation(effect.conversationId)
                is PersonDetailEffect.MessageFailed -> {
                    haptics.error()
                    snackbar.showSnackbar(
                        resources.getString(
                            R.string.feature_profile_person_message_failed,
                            resources.getString(effect.error.messageRes),
                        ),
                    )
                }
            }
        }
    }
    PersonDetailContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onMessage = viewModel::message,
        onRetry = viewModel::load,
        onRetryConnection = viewModel::retryConnection,
        modifier = modifier,
    )
}

@Composable
internal fun PersonDetailContent(
    state: PersonDetailUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onRetry: () -> Unit,
    onRetryConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            WaiveraTopBar(
                title = state.name.ifBlank { stringResource(R.string.feature_profile_person_detail_title) },
                onBack = onBack,
            )
            ConnectionBanner(
                state = bannerFor(state.connection, showsLoadError = state.details is Section.Failed),
                onRetry = onRetryConnection,
                offlineMessage = stringResource(R.string.feature_profile_person_offline_saved),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = WaiveraSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            ) {
                PersonHero(state)
                if (state.canMessage) {
                    WaiveraButton(
                        text = stringResource(R.string.feature_profile_person_message),
                        onClick = onMessage,
                        loadingText = stringResource(R.string.feature_profile_person_opening_chat)
                            .takeIf { state.isOpeningChat },
                        fullWidth = true,
                        leadingIcon = WaiveraIcons.Chat,
                        modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter),
                    )
                }
                PersonDetailsCard(state.details, onRetry)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WaiveraSpacing.Medium),
        )
    }
}

@Composable
private fun PersonHero(state: PersonDetailUiState) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = WaiveraSpacing.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        WaiveraAvatar(
            name = state.name,
            imageModel = AvatarModel.of(state.userId),
            size = WaiveraSizes.AvatarHero,
            milestone = state.celebration?.milestoneKind,
            contentDescription = null,
        )
        if (state.name.isNotBlank()) {
            // Hyphenated names wrap after a hyphen, never mid-word (UI5-05).
            Text(
                text = state.name.withBreaksAfterHyphens(),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        state.email?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        state.celebration?.let { party ->
            Text(
                text = party.fullLabel(state.name),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onAccentSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(colors.accentSoft, WaiveraShapes.Chip)
                    .padding(horizontal = WaiveraSpacing.MediumSmall, vertical = WaiveraSpacing.Small),
            )
        }
    }
}

@Composable
private fun PersonDetailsCard(section: Section<PersonDetails>, onRetry: () -> Unit) {
    when (section) {
        Section.Loading -> if (rememberDelayedSkeleton(loading = true)) {
            ProfileCard { SectionSkeleton(lines = 3) }
        }
        is Section.Failed -> ProfileCard(title = stringResource(R.string.feature_profile_person_error_title)) {
            SectionError(section.error, onRetry)
        }
        is Section.Ready -> {
            val details = section.value
            val roles = details.roles.map { ProfileFormat.roleLabel(it) }.joinedOrNull()
            // Absent values are left out here: this card describes someone else, not a form to fill.
            if (details.organization != null || roles != null || details.phoneNumber != null) {
                ProfileCard {
                    details.organization?.let {
                        InfoRow(stringResource(R.string.feature_profile_organization_title), it)
                    }
                    roles?.let { InfoRow(stringResource(R.string.feature_profile_person_roles), it) }
                    details.phoneNumber?.let { InfoRow(stringResource(R.string.feature_profile_phone), it) }
                }
            }
        }
    }
}

@WaiveraPreviews
@Composable
private fun PersonDetailScreenPreview() {
    WaiveraTheme {
        PersonDetailContent(
            state = PersonDetailUiState(
                userId = "preview",
                name = "Priya Raman",
                email = "priya.raman@example.com",
                details = Section.Ready(
                    PersonDetails(
                        displayName = "Priya Raman",
                        email = "priya.raman@example.com",
                        phoneNumber = "(512) 555-0100",
                        roles = listOf("Care lead"),
                        organization = null,
                    ),
                ),
                celebration = Celebration(birthday = true, anniversaryYears = null),
                connection = ConnectionBannerState.Connected,
            ),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onMessage = {},
            onRetry = {},
            onRetryConnection = {},
        )
    }
}
