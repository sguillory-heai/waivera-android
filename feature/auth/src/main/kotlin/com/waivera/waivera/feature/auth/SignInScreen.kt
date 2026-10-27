package com.waivera.waivera.feature.auth

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
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
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
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
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        actions.onSubmit()
    }
    AuthScaffold(
        modifier = modifier,
        bottomBar = { SignInButtons(state = state, onSubmit = submit, onForgotPassword = actions.onForgotPassword) },
    ) {
        SignInHero()
        SignInBanners(state = state, onDismiss = actions.onDismissMessage)
        SignInForm(state = state, actions = actions, onSubmit = submit)
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
    AuthAnimatedVisibility(visible = state.signedOutNotice && state.errorMessage == null) {
        WaiveraBanner(
            title = stringResource(R.string.feature_auth_signed_out_title),
            message = stringResource(R.string.feature_auth_signed_out_message),
            tone = BannerTone.Info,
            onDismiss = onDismiss,
        )
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
 * Email (IME Next moves to the password) and password (IME Go submits). Submitting with an empty
 * field shows the field errors that explain what is missing.
 */
@Composable
private fun SignInForm(state: SignInUiState, actions: SignInActions, onSubmit: () -> Unit) {
    val focusManager = LocalFocusManager.current
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
            onImeAction = onSubmit,
            errorText = state.passwordError?.let { stringResource(it) },
            enabled = state.fieldsEnabled,
        )
    }
}

@Composable
private fun SignInButtons(state: SignInUiState, onSubmit: () -> Unit, onForgotPassword: () -> Unit) {
    WaiveraButton(
        text = stringResource(R.string.feature_auth_sign_in_button),
        onClick = onSubmit,
        // Enabled with empty fields so a tap explains what is missing (field errors) instead of
        // a dimmed button that says nothing; enabled while busy so the spinner is not dimmed.
        enabled = state.fieldsEnabled || state.isSigningIn,
        loading = state.isSigningIn,
        loadingText = stringResource(R.string.feature_auth_connecting),
        fullWidth = true,
    )
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Icon(
                painter = painterResource(WaiveraIcons.WaiveraMark),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(WaiveraSizes.BrandMark),
            )
            Text(
                text = stringResource(R.string.feature_auth_brand),
                style = MaterialTheme.typography.titleLarge,
                color = colors.accent,
            )
        }
        Text(
            text = stringResource(R.string.feature_auth_sign_in_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        // At large text the form has to start on the first screen; the tagline is the first to go.
        if (!isLargeFontScale()) {
            Text(
                text = stringResource(R.string.feature_auth_sign_in_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
        }
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
        if (!isLargeFontScale()) {
            Text(
                text = stringResource(R.string.feature_auth_sign_in_card_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * Builds without server or Firebase settings. The person only sees what to do; the missing
 * setting names are diagnostics for whoever packaged the build, so they show in debuggable
 * builds only.
 */
@Composable
private fun ConfigurationBanner(missingKeys: List<String>) {
    val details = if (missingKeys.isEmpty() || !rememberIsDebuggableBuild()) {
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

/** True for debuggable (debug) builds, read from the application flags so it needs no BuildConfig. */
@Composable
private fun rememberIsDebuggableBuild(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
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
private fun SignInSignedOutPreview() {
    WaiveraTheme {
        SignInContent(state = SignInUiState(signedOutNotice = true), actions = SignInActions())
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
