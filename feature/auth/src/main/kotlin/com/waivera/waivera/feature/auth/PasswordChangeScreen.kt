package com.waivera.waivera.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.WaiveraIllustration
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.R as NetworkR

/**
 * Entry point for changing the password. Owned by `:feature:auth`.
 *
 * Two flows share this screen:
 * - **Required** (the gate, [onBack] is null): no way past it except a new password or
 *   [onSignOut]. On success the session refreshes and the gate moves on by itself.
 * - **Voluntary** (from Settings, [onBack] set): a top bar with back, no sign-out, and
 *   [onChanged] once the new password is saved.
 */
@Composable
fun PasswordChangeScreen(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onChanged: () -> Unit = {},
) {
    val viewModel: PasswordChangeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestOnChanged by rememberUpdatedState(onChanged)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PasswordChangeEffect.Changed -> latestOnChanged()
            }
        }
    }
    PasswordChangeContent(
        state = state,
        required = onBack == null,
        actions = PasswordChangeActions(
            onCurrentChange = viewModel::onCurrentChange,
            onNewChange = viewModel::onNewChange,
            onConfirmChange = viewModel::onConfirmChange,
            onSubmit = viewModel::submit,
            onContinue = viewModel::continueToApp,
            onDismissMessage = viewModel::dismissMessage,
            onSignOut = onSignOut,
            onBack = onBack,
        ),
        modifier = modifier,
    )
}

/** Under a top bar that already owns the status bar and cutout: only the sides, bottom and IME. */
private val BelowTopBarInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

@Immutable
internal class PasswordChangeActions(
    val onCurrentChange: (String) -> Unit = {},
    val onNewChange: (String) -> Unit = {},
    val onConfirmChange: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onBack: (() -> Unit)? = null,
)

@Composable
internal fun PasswordChangeContent(
    state: PasswordChangeUiState,
    required: Boolean,
    actions: PasswordChangeActions,
    modifier: Modifier = Modifier,
) {
    val done = state.phase == PasswordChangePhase.Done || state.phase == PasswordChangePhase.Continuing
    val bottomBar: @Composable ColumnScope.() -> Unit = if (done) {
        { PasswordChangeDoneButton(state = state, required = required, actions = actions) }
    } else {
        { PasswordChangeButtons(state = state, actions = actions, showSignOut = required) }
    }
    if (required) {
        AuthScaffold(modifier = modifier, hero = true, bottomBar = bottomBar) {
            if (done) {
                PasswordChangeDone(state = state, onDismiss = actions.onDismissMessage)
            } else {
                AuthHeader(
                    title = stringResource(R.string.feature_auth_password_change_required_title),
                    message = stringResource(R.string.feature_auth_password_change_required_message),
                    icon = WaiveraIcons.Lock,
                )
                PasswordChangeForm(state = state, actions = actions)
            }
        }
    } else {
        // The voluntary screen sits under the app's top bar convention: the bar owns the status
        // bar inset, the scaffold below owns the rest (navigation bar and keyboard).
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(WaiveraTheme.colors.background),
        ) {
            WaiveraTopBar(
                title = stringResource(R.string.feature_auth_password_change_title),
                onBack = actions.onBack,
            )
            AuthScaffold(insets = BelowTopBarInsets, bottomBar = bottomBar) {
                if (done) {
                    PasswordChangeDone(state = state, onDismiss = actions.onDismissMessage)
                } else {
                    Text(
                        text = stringResource(R.string.feature_auth_password_change_voluntary_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = WaiveraTheme.colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(GutterPadding),
                    )
                    PasswordChangeForm(state = state, actions = actions)
                }
            }
        }
    }
}

/** The confirm step: the form is replaced by a success block, with the way forward in the bar. */
@Composable
private fun PasswordChangeDone(state: PasswordChangeUiState, onDismiss: () -> Unit) {
    WaiveraEmptyState(
        title = stringResource(R.string.feature_auth_password_change_done_title),
        message = stringResource(R.string.feature_auth_password_change_done_message),
        illustration = WaiveraIllustration.CaughtUp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    // The password is saved either way; only the move into the app failed.
    AuthAnimatedVisibility(visible = state.errorMessage != null) {
        state.errorMessage?.let { message ->
            WaiveraBanner(
                title = stringResource(R.string.feature_auth_password_change_continue_failed_title),
                message = stringResource(message),
                tone = BannerTone.Warning,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * "Continue" on the required flow (reloads the session so the gate moves on, busy while it
 * does) and "Done" on the voluntary one (back to Settings).
 */
@Composable
private fun PasswordChangeDoneButton(state: PasswordChangeUiState, required: Boolean, actions: PasswordChangeActions) {
    if (required) {
        WaiveraButton(
            text = stringResource(R.string.feature_auth_password_change_continue),
            onClick = actions.onContinue,
            loadingText = stringResource(R.string.feature_auth_password_change_continuing)
                .takeIf { state.phase == PasswordChangePhase.Continuing },
            fullWidth = true,
        )
    } else {
        WaiveraButton(
            text = stringResource(R.string.feature_auth_password_change_done_action),
            onClick = { actions.onBack?.invoke() },
            fullWidth = true,
        )
    }
}

@Composable
private fun PasswordChangeForm(state: PasswordChangeUiState, actions: PasswordChangeActions) {
    PasswordChangeBanners(state = state, onDismiss = actions.onDismissMessage)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(GutterPadding),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
    ) {
        PasswordChangeFields(state = state, actions = actions)
    }
}

/** Update (enabled so a tap explains what is missing) and, on the required flow, sign-out. */
@Composable
private fun PasswordChangeButtons(state: PasswordChangeUiState, actions: PasswordChangeActions, showSignOut: Boolean) {
    val saving = state.phase == PasswordChangePhase.Saving
    WaiveraButton(
        text = stringResource(R.string.feature_auth_password_change_save),
        onClick = actions.onSubmit,
        enabled = state.fieldsEnabled || saving,
        loadingText = stringResource(R.string.feature_auth_password_change_saving).takeIf { saving },
        fullWidth = true,
    )
    if (showSignOut) {
        WaiveraButton(
            text = stringResource(R.string.feature_auth_sign_out),
            onClick = actions.onSignOut,
            variant = WaiveraButtonVariant.Text,
            enabled = !saving,
            fullWidth = true,
        )
    }
}

@Composable
private fun PasswordChangeBanners(state: PasswordChangeUiState, onDismiss: () -> Unit) {
    AuthAnimatedVisibility(visible = state.errorMessage != null) {
        state.errorMessage?.let { message ->
            WaiveraBanner(
                title = stringResource(R.string.feature_auth_password_change_failed_title),
                message = stringResource(message),
                tone = BannerTone.Error,
                onDismiss = onDismiss,
            )
        }
    }
}

/** Current, new and confirm. IME Next walks the fields; Done on the last one submits. */
@Composable
private fun PasswordChangeFields(state: PasswordChangeUiState, actions: PasswordChangeActions) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val moveNext = { focusManager.moveFocus(FocusDirection.Next) }
    val minLength = AuthValidation.MIN_NEW_PASSWORD_LENGTH
    PasswordField(
        value = state.current,
        onValueChange = actions.onCurrentChange,
        label = stringResource(R.string.feature_auth_current_password_label),
        placeholder = stringResource(R.string.feature_auth_current_password_placeholder),
        imeAction = ImeAction.Next,
        onImeAction = { moveNext() },
        errorText = state.currentError?.let { stringResource(it) },
        enabled = state.fieldsEnabled,
    )
    PasswordField(
        value = state.new,
        onValueChange = actions.onNewChange,
        label = stringResource(R.string.feature_auth_new_password_label),
        placeholder = stringResource(R.string.feature_auth_new_password_placeholder),
        contentType = ContentType.NewPassword,
        imeAction = ImeAction.Next,
        onImeAction = { moveNext() },
        errorText = state.newError?.let { stringResource(it, minLength) },
        enabled = state.fieldsEnabled,
    )
    if (state.newError == null) {
        Text(
            text = stringResource(R.string.feature_auth_new_password_hint, minLength),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
        )
    }
    PasswordField(
        value = state.confirm,
        onValueChange = actions.onConfirmChange,
        label = stringResource(R.string.feature_auth_confirm_password_label),
        placeholder = stringResource(R.string.feature_auth_confirm_password_placeholder),
        contentType = ContentType.NewPassword,
        imeAction = ImeAction.Done,
        onImeAction = {
            keyboard?.hide()
            actions.onSubmit()
        },
        errorText = state.confirmError?.let { stringResource(it) },
        enabled = state.fieldsEnabled,
    )
}

@WaiveraPreviews
@Composable
private fun PasswordChangeRequiredPreview() {
    WaiveraTheme {
        PasswordChangeContent(state = PasswordChangeUiState(), required = true, actions = PasswordChangeActions())
    }
}

@WaiveraPreviews
@Composable
private fun PasswordChangeValidationPreview() {
    WaiveraTheme {
        PasswordChangeContent(
            state = PasswordChangeUiState(
                current = "old-password",
                new = "short",
                confirm = "shorter",
                newError = R.string.feature_auth_error_new_password_short,
                confirmError = R.string.feature_auth_error_confirm_mismatch,
            ),
            required = true,
            actions = PasswordChangeActions(),
        )
    }
}

@WaiveraPreviews
@Composable
private fun PasswordChangeVoluntaryErrorPreview() {
    WaiveraTheme {
        PasswordChangeContent(
            state = PasswordChangeUiState(
                current = "old-password",
                new = "a new long phrase",
                confirm = "a new long phrase",
                errorMessage = NetworkR.string.core_network_error_network,
            ),
            required = false,
            actions = PasswordChangeActions(onBack = {}),
        )
    }
}
