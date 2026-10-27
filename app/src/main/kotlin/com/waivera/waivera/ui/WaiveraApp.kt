package com.waivera.waivera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.R
import com.waivera.waivera.core.designsystem.component.WaiveraErrorState
import com.waivera.waivera.core.designsystem.component.WaiveraLoadingScreen
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.auth.NotificationPrimerScreen
import com.waivera.waivera.feature.auth.PasswordChangeScreen
import com.waivera.waivera.feature.auth.SignInScreen
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.AppRoute
import com.waivera.waivera.navigation.ShellUiState
import com.waivera.waivera.navigation.TopLevelTab

/** What the root composable can ask of the shell ViewModel. */
class ShellActions(
    val select: (TopLevelTab) -> Unit,
    val push: (AppRoute) -> Unit,
    val back: () -> Unit,
    val onPrimerFinished: (Boolean) -> Unit,
    val retryBootstrap: () -> Unit,
    val signOut: () -> Unit,
)

/** The whole app below the theme: the auth gate, then the tabs. */
@Composable
fun WaiveraApp(state: ShellUiState, actions: ShellActions, modifier: Modifier = Modifier) {
    when (val gate = state.shell.gate) {
        AppGate.Loading -> WaiveraLoadingScreen(modifier)
        is AppGate.SignIn -> SignInScreen(configurationMissing = gate.configurationMissing, modifier = modifier)
        AppGate.BootstrapFailed -> BootstrapFailedScreen(onRetry = actions.retryBootstrap, modifier = modifier)
        AppGate.PasswordChange -> PasswordChangeScreen(onSignOut = actions.signOut, modifier = modifier)
        AppGate.NotificationPrimer -> NotificationPrimerScreen(
            onFinished = actions.onPrimerFinished,
            modifier = modifier,
        )
        AppGate.Main -> MainShell(state = state, actions = actions, modifier = modifier)
    }
}

@Composable
private fun BootstrapFailedScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        WaiveraErrorState(message = stringResource(R.string.shell_bootstrap_failed), onRetry = onRetry)
    }
}
