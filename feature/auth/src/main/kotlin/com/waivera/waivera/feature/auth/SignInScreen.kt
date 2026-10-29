package com.waivera.waivera.feature.auth

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.rejectShake
import com.waivera.waivera.core.designsystem.component.rememberLatestNonNull
import com.waivera.waivera.core.designsystem.component.rememberRejectShake
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
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
    val uriHandler = LocalUriHandler.current
    var rejections by remember { mutableIntStateOf(0) }
    SignInEffects(viewModel.effects, onShake = { rejections++ })
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
            onOpenLink = { url ->
                // The app's handler opens a Custom Tab, or a browser; it throws when nothing can.
                try {
                    uriHandler.openUri(url)
                } catch (_: IllegalArgumentException) {
                    viewModel.onLinkUnavailable()
                }
            },
        ),
        legalLinks = viewModel.legalLinks,
        rejections = rejections,
        modifier = modifier,
    )
}

/**
 * Plays a rejected sign-in: the one vibration through the shared [rememberHaptics] (which honours
 * the Settings switch) and [onShake] for the form's shake. Nothing else on this screen has any.
 */
@Composable
private fun SignInEffects(effects: Flow<SignInEffect>, onShake: () -> Unit) {
    val haptics = rememberHaptics()
    val shake by rememberUpdatedState(onShake)
    LaunchedEffect(effects, haptics) {
        effects.collect { effect ->
            when (effect) {
                is SignInEffect.Haptic -> haptics.perform(effect.moment)
                SignInEffect.Shake -> shake()
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
    val onOpenLink: (String) -> Unit = {},
)

/**
 * The sign-in screen: the brand header with the real logo, then the curved sheet with a short
 * welcome, any message, the two fields, Sign in, Forgot password? and the legal links. No
 * tagline or reassurance copy: everything on it helps someone sign in. Each change of
 * [rejections] shakes the fields and any message above them once (not when animations are off).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SignInContent(
    state: SignInUiState,
    actions: SignInActions,
    modifier: Modifier = Modifier,
    legalLinks: LegalLinks? = null,
    rejections: Int = 0,
    showConfigurationKeys: Boolean = rememberIsDebuggableBuild(),
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        actions.onSubmit()
    }
    // The button follows the password field into view, so with the keyboard up it stays reachable.
    val actionsInView = remember { BringIntoViewRequester() }
    var passwordFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    KeepInViewAboveKeyboard(requester = actionsInView, scrollState = scrollState, active = passwordFocused)
    KeepInViewWhileBannerShows(requester = actionsInView, scrollState = scrollState, banner = state.bannerKey())
    SignInFrame(
        modifier = modifier,
        scrollState = scrollState,
        footer = {
            if (legalLinks != null) LegalFooter(links = legalLinks, onOpen = actions.onOpenLink)
        },
    ) {
        // Concept A's 30 / 800; one step down at large font so "Welcome back" keeps to two lines.
        val titleStyle = if (isLargeFontScale()) {
            MaterialTheme.typography.headlineMedium
        } else {
            MaterialTheme.typography.headlineLarge
        }
        Text(
            text = stringResource(R.string.feature_auth_sign_in_title),
            style = titleStyle,
            color = WaiveraTheme.colors.text,
            modifier = Modifier
                .fillMaxWidth()
                .then(GutterPadding)
                .semantics { heading() },
        )
        // The error banner shakes with the fields it explains, so a rejection is one "no" rather
        // than a shake on top of the banner pushing the form down (M2-09). Each banner carries
        // the sheet's gap below it and opens with it, so the form moves down in one even push
        // instead of dropping by the gap on the rejection's frame (M3-05). At rest the spacing
        // is the same.
        val shake = rememberRejectShake(key = rejections)
        Column(
            modifier = Modifier
                .rejectShake(shake)
                .fillMaxWidth(),
        ) {
            SignInBanners(
                state = state,
                onDismiss = actions.onDismissMessage,
                showConfigurationKeys = showConfigurationKeys,
            )
            SignInForm(
                state = state,
                actions = actions,
                onSubmit = submit,
                onPasswordFocusChange = { passwordFocused = it },
            )
        }
        SignInButtons(
            state = state,
            onSubmit = submit,
            onForgotPassword = actions.onForgotPassword,
            modifier = Modifier.bringIntoViewRequester(actionsInView),
        )
    }
}

/** Which banners show, or null when none does: a new banner brings Sign in back into view. */
private fun SignInUiState.bannerKey(): List<Any?>? {
    val showing = listOf(
        missingConfiguration?.let { CONFIGURATION_BANNER },
        errorMessage,
        SIGNED_OUT_BANNER.takeIf { signedOutNotice && errorMessage == null },
        resetSentTo,
        LINK_BANNER.takeIf { linkUnavailable },
    )
    return showing.takeIf { keys -> keys.any { it != null } }
}

private const val CONFIGURATION_BANNER = "configuration"
private const val SIGNED_OUT_BANNER = "signedOut"
private const val LINK_BANNER = "link"

/** The sheet's gap between a banner and what follows it. */
private val BANNER_GAP = WaiveraSpacing.Medium

@Composable
private fun SignInBanners(state: SignInUiState, onDismiss: () -> Unit, showConfigurationKeys: Boolean) {
    // A dismissed banner keeps its text while it folds, so it shrinks instead of emptying at once.
    val errorMessage = rememberLatestNonNull(state.errorMessage)
    val resetSentTo = rememberLatestNonNull(state.resetSentTo)
    AuthAnimatedVisibility(visible = state.missingConfiguration != null, gapBelow = BANNER_GAP) {
        ConfigurationBanner(missingKeys = state.missingConfiguration.orEmpty(), showKeys = showConfigurationKeys)
    }
    AuthAnimatedVisibility(visible = state.errorMessage != null, gapBelow = BANNER_GAP) {
        errorMessage?.let { message ->
            WaiveraBanner(
                title = stringResource(R.string.feature_auth_sign_in_failed_title),
                message = stringResource(message),
                tone = BannerTone.Error,
                onDismiss = onDismiss,
            )
        }
    }
    AuthAnimatedVisibility(visible = state.signedOutNotice && state.errorMessage == null, gapBelow = BANNER_GAP) {
        WaiveraBanner(
            title = stringResource(R.string.feature_auth_signed_out_title),
            message = stringResource(R.string.feature_auth_signed_out_message),
            tone = BannerTone.Info,
            onDismiss = onDismiss,
        )
    }
    AuthAnimatedVisibility(visible = state.resetSentTo != null, gapBelow = BANNER_GAP) {
        resetSentTo?.let { email ->
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
    AuthAnimatedVisibility(visible = state.linkUnavailable, gapBelow = BANNER_GAP) {
        WaiveraBanner(
            title = stringResource(R.string.feature_auth_link_failed_title),
            message = stringResource(R.string.feature_auth_link_failed_message),
            tone = BannerTone.Warning,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Email (IME Next moves to the password) and password (IME Go submits). Submitting with an empty
 * field shows the field errors that explain what is missing. Both carry autofill types, so a
 * password manager offers saved sign-ins and Compose offers to save them after sign-in.
 */
@Composable
private fun SignInForm(
    state: SignInUiState,
    actions: SignInActions,
    onSubmit: () -> Unit,
    onPasswordFocusChange: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(GutterPadding)
            .padding(top = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
    ) {
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
            leadingIcon = { FieldIcon(WaiveraIcons.Mail) },
        )
        PasswordField(
            value = state.password,
            onValueChange = actions.onPasswordChange,
            label = stringResource(R.string.feature_auth_password_label),
            placeholder = stringResource(R.string.feature_auth_password_placeholder),
            imeAction = ImeAction.Go,
            onImeAction = onSubmit,
            errorText = state.passwordError?.let { stringResource(it) },
            enabled = state.fieldsEnabled,
            modifier = Modifier.onFocusChanged { onPasswordFocusChange(it.hasFocus) },
        )
    }
}

/** The primary Sign in (concept A's 56dp CTA), with Forgot password? centred under it. */
@Composable
private fun SignInButtons(
    state: SignInUiState,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(GutterPadding)
            .padding(top = WaiveraSpacing.MediumSmall),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
    ) {
        WaiveraButton(
            text = stringResource(R.string.feature_auth_sign_in_button),
            onClick = onSubmit,
            size = WaiveraButtonSize.Large,
            // Enabled with empty fields so a tap explains what is missing (field errors) instead of
            // a dimmed button that says nothing; enabled while busy so the spinner is not dimmed.
            enabled = state.fieldsEnabled || state.isSigningIn,
            loadingText = stringResource(R.string.feature_auth_connecting).takeIf { state.isSigningIn },
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
        SignInContent(state = SignInUiState(), actions = SignInActions(), legalLinks = PreviewLegalLinks)
    }
}

@WaiveraPreviews
@Composable
private fun SignInLinkUnavailablePreview() {
    WaiveraTheme {
        SignInContent(
            state = SignInUiState(linkUnavailable = true),
            actions = SignInActions(),
            legalLinks = PreviewLegalLinks,
        )
    }
}

/** The footer as a configured build shows it; a preview opens nothing, so the links stay empty. */
private val PreviewLegalLinks = LegalLinks(termsOfService = "", privacyPolicy = "")

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
