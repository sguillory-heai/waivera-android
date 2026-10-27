package com.waivera.waivera.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.LocalSignedInAccount
import com.waivera.waivera.core.designsystem.component.SignedInAccount
import com.waivera.waivera.core.designsystem.component.WaiveraLoadingScreen
import com.waivera.waivera.feature.auth.BootstrapFailedScreen
import com.waivera.waivera.feature.auth.NotificationPrimerScreen
import com.waivera.waivera.feature.auth.OnboardingRequiredScreen
import com.waivera.waivera.feature.auth.PasswordChangeScreen
import com.waivera.waivera.feature.auth.SignInScreen
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.AppRoute
import com.waivera.waivera.navigation.ShellUiState
import com.waivera.waivera.navigation.TopLevelTab

/** What the root composable can ask of the shell ViewModel. */
// One callback per shell entry point; grouping them would only rename the list.
@Suppress("LongParameterList")
class ShellActions(
    val select: (TopLevelTab) -> Unit,
    val push: (AppRoute) -> Unit,
    val back: () -> Unit,
    val openFromAccountMenu: (AppRoute) -> Unit,
    val onPrimerFinished: (Boolean) -> Unit,
    val signOut: () -> Unit,
    val onUnopenableLinkShown: () -> Unit = {},
)

/**
 * The whole app below the theme: the auth gate, then the tabs. The signed-in person is provided
 * to every tab's top bar through [LocalSignedInAccount].
 */
@Composable
fun WaiveraApp(state: ShellUiState, actions: ShellActions, modifier: Modifier = Modifier) {
    val account = remember(state.accountName, state.accountUserId) {
        SignedInAccount(name = state.accountName, imageModel = AvatarModel.of(state.accountUserId))
    }
    CompositionLocalProvider(LocalSignedInAccount provides account) {
        when (val gate = state.shell.gate) {
            AppGate.Loading -> WaiveraLoadingScreen(modifier)
            is AppGate.SignIn -> SignInScreen(configurationMissing = gate.configurationMissing, modifier = modifier)
            AppGate.BootstrapFailed -> BootstrapFailedScreen(onSignOut = actions.signOut, modifier = modifier)
            AppGate.PasswordChange -> PasswordChangeScreen(onSignOut = actions.signOut, modifier = modifier)
            AppGate.OnboardingRequired -> OnboardingRequiredScreen(onSignOut = actions.signOut, modifier = modifier)
            AppGate.NotificationPrimer -> NotificationPrimerScreen(
                onFinished = actions.onPrimerFinished,
                modifier = modifier,
            )
            AppGate.Main -> MainShell(state = state, actions = actions, modifier = modifier)
        }
    }
}
