package com.waivera.waivera.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.feedback.Haptics
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.R as NetworkR
import kotlinx.coroutines.flow.Flow

/**
 * Entry point for sign-in. Owned by `:feature:auth`; shown by the app's gate.
 *
 * [configurationMissing] comes from the gate. The ViewModel also reads the missing keys from the
 * session, so either source disables the form.
 */
@Composable
fun SignInScreen(configurationMissing: Boolean, modifier: Modifier = Modifier) {
    val viewModel: SignInViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthEffects(viewModel.effects, rememberHaptics())
    SignInContent(
        state = if (configurationMissing && state.isConfigured) {
            state.copy(missingConfiguration = emptyList())
        } else {
            state
        },
        actions = SignInActions(
            onEmailChange = viewModel::onEmailChange,
            onPasswordChange = viewModel::onPasswordChange,
            onSubmit = viewModel::signIn,
            onForgotPassword = viewModel::sendPasswordReset,
            onDismissMessage = viewModel::dismissMessage,
        ),
        modifier = modifier,
    )
}

/** Plays the haptic for each one-off outcome: errors and a sent reset link. */
@Composable
internal fun AuthEffects(effects: Flow<AuthEffect>, haptics: Haptics) {
    LaunchedEffect(effects, haptics) {
        effects.collect { effect ->
            when (effect) {
                AuthEffect.Error -> haptics.error()
                AuthEffect.Success -> haptics.success()
            }
        }
    }
}

internal class SignInActions(
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onForgotPassword: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

@Composable
internal fun SignInContent(state: SignInUiState, actions: SignInActions, modifier: Modifier = Modifier) {
    AuthScaffold(modifier = modifier) {
        SignInHero()
        SignInBanners(state = state, onDismiss = actions.onDismissMessage)
        SignInForm(state = state, actions = actions)
        Text(
            text = stringResource(R.string.feature_auth_footer),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .then(GutterPadding),
        )
    }
}

@Composable
private fun SignInBanners(state: SignInUiState, onDismiss: () -> Unit) {
    AuthAnimatedVisibility(visible = state.missingConfiguration != null) {
        ConfigurationBanner(state.missingConfiguration.orEmpty())
    }
    AuthAnimatedVisibility(visible = state.errorMessage != null) {
        state.errorMessage?.let { message ->
            WaiveraBanner(
                title = stringResource(R.string.feature_auth_sign_in_failed_title),
                message = stringResource(message),
                tone = BannerTone.Error,
                onDismiss = onDismiss,
            )
        }
    }
    AuthAnimatedVisibility(visible = state.resetSentTo != null) {
        state.resetSentTo?.let { email ->
            WaiveraBanner(
                title = stringResource(R.string.feature_auth_reset_sent_title),
                message = stringResource(R.string.feature_auth_reset_sent_message, email),
                tone = BannerTone.Info,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * Email (IME Next moves to the password) and password (IME Go submits). Go with an empty field
 * still submits so the field errors explain what is missing, while the button stays disabled.
 */
@Composable
private fun SignInForm(state: SignInUiState, actions: SignInActions) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        actions.onSubmit()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(GutterPadding),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
    ) {
        SignInCardHeading()
        WaiveraTextField(
            value = state.email,
            onValueChange = actions.onEmailChange,
            label = stringResource(R.string.feature_auth_email_label),
            placeholder = stringResource(R.string.feature_auth_email_placeholder),
            contentType = ContentType.EmailAddress + ContentType.Username,
            errorText = state.emailError?.let { stringResource(it) },
            enabled = state.fieldsEnabled,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        )
        PasswordField(
            value = state.password,
            onValueChange = actions.onPasswordChange,
            label = stringResource(R.string.feature_auth_password_label),
            imeAction = ImeAction.Go,
            onImeAction = submit,
            errorText = state.passwordError?.let { stringResource(it) },
            enabled = state.fieldsEnabled,
        )
        SignInButtons(state = state, onSubmit = submit, onForgotPassword = actions.onForgotPassword)
    }
}

@Composable
private fun SignInButtons(state: SignInUiState, onSubmit: () -> Unit, onForgotPassword: () -> Unit) {
    WaiveraButton(
        text = stringResource(R.string.feature_auth_sign_in_button),
        onClick = onSubmit,
        // Enabled while busy so the spinner is not dimmed; the button ignores taps while loading.
        enabled = state.canSubmit || state.isSigningIn,
        loading = state.isSigningIn,
        fullWidth = true,
    )
    if (state.isSigningIn) {
        Text(
            text = stringResource(R.string.feature_auth_connecting),
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    WaiveraButton(
        text = stringResource(R.string.feature_auth_forgot_password),
        onClick = onForgotPassword,
        variant = WaiveraButtonVariant.Text,
        enabled = state.fieldsEnabled || state.phase == SignInPhase.SendingReset,
        loading = state.phase == SignInPhase.SendingReset,
        fullWidth = true,
    )
}

@Composable
private fun SignInHero() {
    val colors = WaiveraTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(GutterPadding),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.feature_auth_brand),
            style = MaterialTheme.typography.titleMedium,
            color = colors.accent,
        )
        Text(
            text = stringResource(R.string.feature_auth_sign_in_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.feature_auth_sign_in_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SignInCardHeading() {
    val colors = WaiveraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
        Text(
            text = stringResource(R.string.feature_auth_sign_in_card_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.feature_auth_sign_in_card_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

/**
 * Release builds without server or Firebase settings. The keys are setting names, not values, so
 * they are safe to show and help whoever packaged the build.
 */
@Composable
private fun ConfigurationBanner(missingKeys: List<String>) {
    val details = if (missingKeys.isEmpty()) {
        ""
    } else {
        "\n" + stringResource(R.string.feature_auth_configuration_details, missingKeys.joinToString())
    }
    WaiveraBanner(
        title = stringResource(R.string.feature_auth_configuration_title),
        message = stringResource(R.string.feature_auth_configuration_message) + details,
        tone = BannerTone.Warning,
    )
}

@WaiveraPreviews
@Composable
private fun SignInEmptyPreview() {
    WaiveraTheme {
        SignInContent(state = SignInUiState(), actions = SignInActions())
    }
}

@WaiveraPreviews
@Composable
private fun SignInErrorPreview() {
    WaiveraTheme {
        SignInContent(
            state = SignInUiState(
                email = "alex.rivera@example.org",
                password = "secret",
                errorMessage = NetworkR.string.core_network_error_invalid_credentials,
            ),
            actions = SignInActions(),
        )
    }
}

@WaiveraPreviews
@Composable
private fun SignInConnectingPreview() {
    WaiveraTheme {
        SignInContent(
            state = SignInUiState(
                email = "alex.rivera@example.org",
                password = "secret",
                phase = SignInPhase.SigningIn,
            ),
            actions = SignInActions(),
        )
    }
}

@WaiveraPreviews
@Composable
private fun SignInConfigurationMissingPreview() {
    WaiveraTheme {
        SignInContent(
            state = SignInUiState(missingConfiguration = listOf("waivera.apiBaseUrl", "waivera.firebase.apiKey")),
            actions = SignInActions(),
        )
    }
}
