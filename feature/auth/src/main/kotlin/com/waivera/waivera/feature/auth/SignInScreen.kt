package com.waivera.waivera.feature.auth

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.waivera.waivera.core.designsystem.component.WaiveraLogo
import com.waivera.waivera.core.designsystem.component.WaiveraLogoVariant
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.feedback.Haptics
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraBrand
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
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

/**
 * Plays the haptic for each one-off outcome: errors and warnings. Sign-in and the gates emit no
 * success; a sent reset link is confirmed by its banner alone (UI6-07).
 */
@Composable
internal fun AuthEffects(effects: Flow<AuthEffect>, haptics: Haptics) {
    LaunchedEffect(effects, haptics) {
        effects.collect { effect ->
            when (effect) {
                AuthEffect.Error -> haptics.error()
                AuthEffect.Warning -> haptics.warning()
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
internal fun SignInContent(
    state: SignInUiState,
    actions: SignInActions,
    modifier: Modifier = Modifier,
    showConfigurationKeys: Boolean = rememberIsDebuggableBuild(),
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        actions.onSubmit()
    }
    AuthScaffold(
        modifier = modifier,
        bottomBar = { SignInButtons(state = state, onSubmit = submit, onForgotPassword = actions.onForgotPassword) },
    ) {
        // On a short window a banner pushes the form down; the tagline goes first so the fields stay close.
        SignInHero(showTagline = !isLargeFontScale() && !(state.hasBanner && LocalCompactAuthWindow.current))
        SignInBanners(
            state = state,
            onDismiss = actions.onDismissMessage,
            showConfigurationKeys = showConfigurationKeys,
        )
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
private fun SignInBanners(state: SignInUiState, onDismiss: () -> Unit, showConfigurationKeys: Boolean) {
    AuthAnimatedVisibility(visible = state.missingConfiguration != null) {
        ConfigurationBanner(missingKeys = state.missingConfiguration.orEmpty(), showKeys = showConfigurationKeys)
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
                message = stringResource(R.string.feature_auth_reset_sent_message),
                tone = BannerTone.Info,
                // The address on a line of its own, so it never breaks at its dots.
                detail = email,
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
            placeholder = stringResource(R.string.feature_auth_brand_password_placeholder),
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
        loadingText = stringResource(R.string.feature_auth_connecting).takeIf { state.isSigningIn },
        fullWidth = true,
    )
    WaiveraButton(
        text = stringResource(R.string.feature_auth_forgot_password),
        onClick = onForgotPassword,
        variant = WaiveraButtonVariant.Text,
        enabled = state.fieldsEnabled || state.phase == SignInPhase.SendingReset,
        loadingText = stringResource(R.string.feature_auth_reset_sending)
            .takeIf { state.phase == SignInPhase.SendingReset },
        fullWidth = true,
    )
}

/**
 * The welcome: the real logo (mark and wordmark) on a soft brand-gradient band, then the
 * greeting. The band shrinks on a short window or at large text so the form still starts on
 * the first screen; the tagline is the first thing to go.
 */
@Composable
private fun SignInHero(showTagline: Boolean) {
    val colors = WaiveraTheme.colors
    val compact = LocalCompactAuthWindow.current || isLargeFontScale()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(GutterPadding),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) WaiveraSizes.HeroBandCompact else WaiveraSizes.HeroBand)
                .clip(WaiveraShapes.Hero)
                .background(colors.surface)
                .background(WaiveraBrand.wash())
                .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Hero)
                .padding(WaiveraSpacing.Large),
            contentAlignment = Alignment.Center,
        ) {
            WaiveraLogo(
                variant = WaiveraLogoVariant.Lockup,
                height = if (compact) WaiveraSizes.BrandLockup else WaiveraSizes.BrandLockupHero,
                contentDescription = stringResource(R.string.feature_auth_brand),
            )
        }
        Text(
            text = stringResource(R.string.feature_auth_sign_in_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        if (showTagline) {
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
private fun ConfigurationBanner(missingKeys: List<String>, showKeys: Boolean) {
    // The raw settings keys help a developer and mean nothing to staff: debug builds only (UI4-11).
    val details = if (missingKeys.isEmpty() || !showKeys) {
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
