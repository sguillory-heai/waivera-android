package com.waivera.waivera.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.core.network.R as NetworkR
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Gate shown when Firebase restored a user but the backend bootstrap failed and nothing is
 * cached. Shows the mapped reason, retries the bootstrap and offers sign-out.
 */
@Composable
fun BootstrapFailedScreen(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: BootstrapFailedViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthEffects(viewModel.effects, rememberHaptics())
    BootstrapFailedContent(state = state, onRetry = viewModel::retry, onSignOut = onSignOut, modifier = modifier)
}

@Composable
internal fun BootstrapFailedContent(
    state: BootstrapFailedUiState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // As on the onboarding gate: at large font Try again stays pinned, with the fade over the
    // copy scrolling above it, and Sign out moves to the end of the content, so no action is
    // ever drawn cut in half at the fold (UI11-05).
    val signOutInline = isLargeFontScale()
    AuthScaffold(
        modifier = modifier,
        hero = true,
        pinnedAtLargeFont = true,
        bottomBar = {
            GateActions(
                primaryLabel = stringResource(R.string.feature_auth_try_again),
                primaryLoading = state.retrying,
                primaryLoadingLabel = stringResource(R.string.feature_auth_retrying),
                onPrimary = onRetry,
                onSignOut = onSignOut,
                showSignOut = !signOutInline,
            )
        },
    ) {
        AuthHeader(
            title = stringResource(
                if (state.isOffline) {
                    R.string.feature_auth_bootstrap_offline_title
                } else {
                    R.string.feature_auth_bootstrap_title
                },
            ),
            // The title already says "offline"; the body says what to do instead of repeating it.
            message = stringResource(
                if (state.isOffline) R.string.feature_auth_bootstrap_offline_message else state.message,
            ),
            icon = if (state.isOffline) WaiveraIcons.CloudOff else WaiveraIcons.Error,
            tone = if (state.isOffline) WaiveraTileTone.Neutral else WaiveraTileTone.Danger,
        )
        AuthAnimatedVisibility(visible = state.lastFailedRetryAt != null && !state.retrying) {
            state.lastFailedRetryAt?.let { RetryFailedBanner(it) }
        }
        GateHelp(stringResource(R.string.feature_auth_bootstrap_help))
        if (signOutInline) {
            GateSignOut(onSignOut = onSignOut, enabled = !state.retrying, modifier = GutterPadding)
        }
    }
}

/** "Still can't reach Waivera · tried at 10:31 AM": a failed retry must look different from the first failure. */
@Composable
private fun RetryFailedBanner(at: Instant) {
    val formatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val time = remember(at, formatter) { formatter.format(at.atZone(ZoneId.systemDefault())) }
    WaiveraBanner(
        title = stringResource(R.string.feature_auth_bootstrap_still_failing_title),
        message = stringResource(R.string.feature_auth_bootstrap_still_failing_message, time),
        tone = BannerTone.Warning,
    )
}

/**
 * Gate for an account whose Waivera signup is not finished (`requiresOnboarding`). There is no
 * onboarding contract yet, so this explains what to do outside the app, re-checks on request and
 * offers sign-out.
 */
@Composable
fun OnboardingRequiredScreen(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: OnboardingRequiredViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthEffects(viewModel.effects, rememberHaptics())
    OnboardingRequiredContent(
        state = state,
        onCheckAgain = viewModel::checkAgain,
        onSignOut = onSignOut,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingRequiredContent(
    state: OnboardingUiState,
    onCheckAgain: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // As on the primer: at large font the main action stays pinned, so "Checking…" is always in
    // sight on a small phone, and Sign out moves to the end of the content (UI8-07).
    val signOutInline = isLargeFontScale()
    AuthScaffold(
        modifier = modifier,
        hero = true,
        pinnedAtLargeFont = true,
        bottomBar = {
            GateActions(
                primaryLabel = stringResource(R.string.feature_auth_onboarding_check),
                primaryLoading = state.checking,
                primaryLoadingLabel = stringResource(R.string.feature_auth_checking),
                onPrimary = onCheckAgain,
                onSignOut = onSignOut,
                showSignOut = !signOutInline,
            )
        },
    ) {
        AuthHeader(
            title = stringResource(R.string.feature_auth_onboarding_title),
            message = stringResource(R.string.feature_auth_onboarding_message),
            icon = WaiveraIcons.Person,
            welcome = true,
        )
        AuthAnimatedVisibility(visible = state.stillPending) {
            WaiveraBanner(
                message = stringResource(R.string.feature_auth_onboarding_still_pending),
                tone = BannerTone.Warning,
            )
        }
        AuthAnimatedVisibility(visible = state.errorMessage != null) {
            state.errorMessage?.let { WaiveraBanner(message = stringResource(it), tone = BannerTone.Error) }
        }
        GateHelp(stringResource(R.string.feature_auth_onboarding_help))
        if (signOutInline) {
            GateSignOut(onSignOut = onSignOut, enabled = !state.checking, modifier = GutterPadding)
        }
    }
}

@Composable
private fun GateHelp(help: String) {
    Text(
        text = help,
        style = MaterialTheme.typography.bodyMedium,
        color = WaiveraTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .then(GutterPadding),
    )
}

/** The gate's primary action and sign-out, pinned in the scaffold's bottom bar. */
@Composable
private fun GateActions(
    primaryLabel: String,
    primaryLoading: Boolean,
    primaryLoadingLabel: String,
    onPrimary: () -> Unit,
    onSignOut: () -> Unit,
    showSignOut: Boolean = true,
) {
    WaiveraButton(
        text = primaryLabel,
        onClick = onPrimary,
        loadingText = primaryLoadingLabel.takeIf { primaryLoading },
        fullWidth = true,
    )
    if (showSignOut) GateSignOut(onSignOut = onSignOut, enabled = !primaryLoading)
}

@Composable
private fun GateSignOut(onSignOut: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    WaiveraButton(
        text = stringResource(R.string.feature_auth_sign_out),
        onClick = onSignOut,
        variant = WaiveraButtonVariant.Text,
        enabled = enabled,
        fullWidth = true,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun BootstrapFailedPreview() {
    WaiveraTheme {
        BootstrapFailedContent(
            state = BootstrapFailedUiState(message = NetworkR.string.core_network_error_network, isOffline = true),
            onRetry = {},
            onSignOut = {},
        )
    }
}

@WaiveraPreviews
@Composable
private fun BootstrapRetryFailedPreview() {
    WaiveraTheme {
        BootstrapFailedContent(
            state = BootstrapFailedUiState(
                message = NetworkR.string.core_network_error_server,
                lastFailedRetryAt = Instant.parse("2026-10-27T15:31:00Z"),
            ),
            onRetry = {},
            onSignOut = {},
        )
    }
}

@WaiveraPreviews
@Composable
private fun OnboardingRequiredPreview() {
    WaiveraTheme {
        OnboardingRequiredContent(state = OnboardingUiState(stillPending = true), onCheckAgain = {}, onSignOut = {})
    }
}
