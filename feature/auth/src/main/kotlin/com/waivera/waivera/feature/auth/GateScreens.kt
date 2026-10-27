package com.waivera.waivera.feature.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.R as NetworkR

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
    AuthScaffold(
        modifier = modifier,
        bottomBar = {
            GateActions(
                primaryLabel = stringResource(R.string.feature_auth_try_again),
                primaryLoading = state.retrying,
                primaryLoadingLabel = stringResource(R.string.feature_auth_retrying),
                onPrimary = onRetry,
                onSignOut = onSignOut,
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
            message = stringResource(state.message),
            icon = if (state.isOffline) WaiveraIcons.CloudOff else WaiveraIcons.Error,
        )
        GateHelp(stringResource(R.string.feature_auth_bootstrap_help))
    }
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
    AuthScaffold(
        modifier = modifier,
        bottomBar = {
            GateActions(
                primaryLabel = stringResource(R.string.feature_auth_onboarding_check),
                primaryLoading = state.checking,
                primaryLoadingLabel = stringResource(R.string.feature_auth_checking),
                onPrimary = onCheckAgain,
                onSignOut = onSignOut,
            )
        },
    ) {
        AuthHeader(
            title = stringResource(R.string.feature_auth_onboarding_title),
            message = stringResource(R.string.feature_auth_onboarding_message),
            icon = WaiveraIcons.Person,
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
) {
    WaiveraButton(
        text = primaryLabel,
        onClick = onPrimary,
        loading = primaryLoading,
        loadingText = primaryLoadingLabel,
        fullWidth = true,
    )
    WaiveraButton(
        text = stringResource(R.string.feature_auth_sign_out),
        onClick = onSignOut,
        variant = WaiveraButtonVariant.Text,
        enabled = !primaryLoading,
        fullWidth = true,
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
private fun OnboardingRequiredPreview() {
    WaiveraTheme {
        OnboardingRequiredContent(state = OnboardingUiState(stillPending = true), onCheckAgain = {}, onSignOut = {})
    }
}
