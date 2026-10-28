package com.waivera.waivera.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.WaiveraAddressText
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraConfirmationSheet
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the settings screen. Owned by `:feature:settings`; routed by `:app`.
 * Account and sign-out live here (there is no separate account route). The Security section
 * (voluntary password change) shows only when [onOpenChangePassword] is set.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenChangePassword: (() -> Unit)? = null,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.SignOutFailed -> {
                    haptics.error()
                    snackbar.showSnackbar(
                        resources.getString(
                            R.string.feature_settings_sign_out_failed,
                            resources.getString(effect.error.messageRes),
                        ),
                    )
                }
            }
        }
    }
    SettingsContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onOpenNotificationSettings = onOpenNotificationSettings,
        onOpenAppearance = onOpenAppearance,
        onSignOut = viewModel::requestSignOut,
        modifier = modifier,
        onOpenChangePassword = onOpenChangePassword,
    )
    if (state.confirmingSignOut) {
        WaiveraConfirmationSheet(
            title = stringResource(R.string.feature_settings_sign_out_confirm_title),
            message = stringResource(R.string.feature_settings_sign_out_confirm_message),
            confirmLabel = stringResource(R.string.feature_settings_sign_out),
            onConfirm = viewModel::confirmSignOut,
            onDismiss = viewModel::dismissSignOut,
            destructive = true,
            confirmInProgress = state.signingOut,
            confirmInProgressLabel = stringResource(R.string.feature_settings_signing_out),
        )
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenChangePassword: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            WaiveraTopBar(title = stringResource(R.string.feature_settings_settings_title), onBack = onBack)
            // Every section stays on screen while signing out, so nothing jumps; the rows just stop
            // taking taps until the sign-out finishes or fails.
            val rowsEnabled = !state.signingOut
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                WaiveraSectionHeader(text = stringResource(R.string.feature_settings_section_preferences))
                SettingsCard {
                    // Rows navigate from onClick; no pressable is nested inside a link (pitfall 11 §46).
                    WaiveraListRow(
                        headline = stringResource(R.string.feature_settings_notification_settings_title),
                        supporting = stringResource(R.string.feature_settings_notifications_row_detail),
                        onClick = onOpenNotificationSettings,
                        enabled = rowsEnabled,
                        showChevron = true,
                        leading = { WaiveraIconTile(icon = WaiveraIcons.Notifications, tone = WaiveraTileTone.Amber) },
                    )
                    WaiveraListRow(
                        headline = stringResource(R.string.feature_settings_appearance_title),
                        supporting = stringResource(R.string.feature_settings_appearance_row_detail),
                        onClick = onOpenAppearance,
                        enabled = rowsEnabled,
                        showChevron = true,
                        leading = {
                            WaiveraIconTile(icon = R.drawable.feature_settings_ic_palette, tone = WaiveraTileTone.Rose)
                        },
                    )
                }
                if (onOpenChangePassword != null) {
                    WaiveraSectionHeader(text = stringResource(R.string.feature_settings_section_security))
                    SettingsCard {
                        WaiveraListRow(
                            headline = stringResource(R.string.feature_settings_change_password),
                            supporting = stringResource(R.string.feature_settings_change_password_detail),
                            onClick = onOpenChangePassword,
                            enabled = rowsEnabled,
                            showChevron = true,
                            leading = { WaiveraIconTile(icon = WaiveraIcons.Lock) },
                        )
                    }
                }
                WaiveraSectionHeader(text = stringResource(R.string.feature_settings_section_account))
                AccountCard(account = state.account, signingOut = state.signingOut, onSignOut = onSignOut)
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        WaiveraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues()),
        )
    }
}

@Composable
private fun AccountCard(account: AccountInfo?, signingOut: Boolean, onSignOut: () -> Unit) {
    val colors = WaiveraTheme.colors
    SettingsCard {
        if (account != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.Small)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WaiveraAvatar(name = account.name, size = WaiveraSizes.AvatarMedium, contentDescription = null)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
                ) {
                    Text(text = account.name, style = MaterialTheme.typography.titleMedium, color = colors.text)
                    account.email?.let {
                        WaiveraAddressText(
                            address = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    account.organization?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }
            }
        }
        WaiveraButton(
            text = stringResource(R.string.feature_settings_sign_out),
            onClick = onSignOut,
            variant = WaiveraButtonVariant.DestructiveOutline,
            loadingText = stringResource(R.string.feature_settings_signing_out).takeIf { signingOut },
            fullWidth = true,
            leadingIcon = R.drawable.feature_settings_ic_logout,
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.Small),
        )
        Text(
            text = stringResource(R.string.feature_settings_sign_out_detail),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium),
        )
    }
}

@WaiveraPreviews
@Composable
private fun SettingsScreenPreview() {
    WaiveraTheme {
        SettingsContent(
            state = SettingsUiState(
                account = AccountInfo("preview", "Alex Rivera", "alex.rivera@example.com", "Waivera Care"),
            ),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onOpenNotificationSettings = {},
            onOpenAppearance = {},
            onSignOut = {},
        )
    }
}
