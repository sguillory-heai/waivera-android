package com.waivera.waivera.navigation

import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget

/** What the shell shows before (or instead of) the tabs. Derived only from the session and the primer. */
sealed interface AppGate {
    /** Firebase is restoring the user or the backend bootstrap is running. */
    data object Loading : AppGate

    /**
     * [configurationMissing] is true when the build has no API URL or Firebase values. Only release
     * builds reach it (debug builds fall back to demo data through `DemoMode`), so sign-in explains
     * that the build is not configured instead of offering a demo.
     */
    data class SignIn(val configurationMissing: Boolean = false) : AppGate

    /** Signed in with Firebase, but the backend bootstrap failed and nothing is cached. */
    data object BootstrapFailed : AppGate

    data object PasswordChange : AppGate

    /** Signed in, but the account's signup is not finished (01 §5 rule 5). */
    data object OnboardingRequired : AppGate

    data object NotificationPrimer : AppGate

    /** The tabs. */
    data object Main : AppGate

    /** True for gates that only a signed-in user can reach. */
    val isSignedIn: Boolean
        get() = this == Main ||
            this == PasswordChange ||
            this == OnboardingRequired ||
            this == NotificationPrimer ||
            this == BootstrapFailed

    companion object {
        /**
         * The gate for [session]. The primer shows once, after sign-in, any required password
         * change and onboarding, and only when [primerPending] (Android 13+, not granted, not
         * shown before).
         */
        fun from(session: SessionState, primerPending: Boolean): AppGate = when (session) {
            SessionState.Initializing -> Loading
            is SessionState.SignedOut -> SignIn()
            is SessionState.ConfigurationMissing -> SignIn(configurationMissing = true)
            is SessionState.BootstrapFailed -> BootstrapFailed
            is SessionState.PasswordChangeRequired -> PasswordChange
            is SessionState.SignedIn -> when {
                session.user.requiresOnboarding -> OnboardingRequired
                primerPending -> NotificationPrimer
                else -> Main
            }
        }
    }
}

/**
 * The shell's whole state. Deep links that arrive before [AppGate.Main] wait in
 * [pendingDeepLink] and open once the user reaches the tabs; signing out discards them along
 * with the back stacks, so the next person never lands on the previous person's conversation.
 */
data class ShellState(
    val gate: AppGate = AppGate.Loading,
    val nav: NavState = NavState(),
    val pendingDeepLink: DeepLinkTarget? = null,
) {
    fun onGate(next: AppGate): ShellState {
        if (next == gate) return this
        return when {
            // Signing in runs the bootstrap through Initializing; the sign-in screen keeps its
            // "Signing you in…" button instead of swapping to the full-screen loader (01 §8).
            gate is AppGate.SignIn && next == AppGate.Loading -> this
            next == AppGate.Main && pendingDeepLink != null ->
                copy(gate = next, nav = nav.open(pendingDeepLink), pendingDeepLink = null)
            gate.isSignedIn && next is AppGate.SignIn -> ShellState(gate = next)
            else -> copy(gate = next)
        }
    }

    fun onDeepLink(target: DeepLinkTarget): ShellState = if (gate == AppGate.Main) {
        copy(nav = nav.open(target))
    } else {
        copy(pendingDeepLink = target)
    }

    fun onNav(transform: (NavState) -> NavState): ShellState =
        if (gate == AppGate.Main) copy(nav = transform(nav)) else this
}
