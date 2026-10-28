package com.waivera.waivera.feature.auth

import com.waivera.waivera.core.network.R as NetworkR
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every auth and onboarding screen in every state, across the shared matrix (light/dark ×
 * small/large phone × 100%/200% font). Names are `<screen>_<state>`. Emails here are test data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class AuthScreenshotTest {
    private val email = "alex.rivera@example.org"

    /** As a configured build shows it: the footer links to the web app's legal pages. */
    private val legalLinks = LegalLinks(
        termsOfService = "https://app.example.test/terms-of-service",
        privacyPolicy = "https://app.example.test/privacy-policy",
    )

    private fun signIn(
        name: String,
        state: SignInUiState,
        showConfigurationKeys: Boolean = false,
        links: LegalLinks? = legalLinks,
    ) = captureScreenshots(name = "sign_in_$name") {
        SignInContent(
            state = state,
            actions = SignInActions(),
            legalLinks = links,
            showConfigurationKeys = showConfigurationKeys,
        )
    }

    @Test
    fun signInEmpty() = signIn("empty", SignInUiState())

    @Test
    fun signInFilled() = signIn("filled", SignInUiState(email = email, password = "correct horse"))

    @Test
    fun signInValidation() = signIn(
        "validation",
        SignInUiState(
            email = "alex.rivera",
            emailError = R.string.feature_auth_error_email_invalid,
            passwordError = R.string.feature_auth_error_password_required,
        ),
    )

    @Test
    fun signInConnecting() = signIn(
        "connecting",
        SignInUiState(email = email, password = "correct horse", phase = SignInPhase.SigningIn),
    )

    @Test
    fun signInWrongPassword() = signIn(
        "wrong_password",
        SignInUiState(
            email = email,
            password = "correct horse",
            errorMessage = NetworkR.string.core_network_error_invalid_credentials,
        ),
    )

    @Test
    fun signInOffline() = signIn(
        "offline",
        SignInUiState(email = email, password = "x", errorMessage = NetworkR.string.core_network_error_network),
    )

    @Test
    fun signInSessionEnded() = signIn(
        "session_ended",
        SignInUiState(email = email, errorMessage = NetworkR.string.core_network_error_session_expired),
    )

    @Test
    fun signInSignedOut() = signIn("signed_out", SignInUiState(signedOutNotice = true))

    @Test
    fun signInResetSent() = signIn("reset_sent", SignInUiState(email = email, resetSentTo = email))

    /** Nothing on the device could open a legal page. */
    @Test
    fun signInLinkUnavailable() = signIn("link_unavailable", SignInUiState(email = email, linkUnavailable = true))

    /** A build with no web base hides the legal sentence instead of showing dead links. */
    @Test
    fun signInNoWebBase() = signIn("no_web_base", SignInUiState(), links = null)

    /** What staff see in a release build: the plain sentence, no settings keys (UI4-11). */
    @Test
    fun signInConfigurationMissing() = signIn(
        "configuration_missing",
        SignInUiState(missingConfiguration = listOf("waivera.apiBaseUrl", "waivera.firebase.apiKey")),
    )

    /** A debug build also lists the missing keys, for the developer who built it. */
    @Test
    fun signInConfigurationMissingDebug() = signIn(
        "configuration_missing_debug",
        SignInUiState(missingConfiguration = listOf("waivera.apiBaseUrl", "waivera.firebase.apiKey")),
        showConfigurationKeys = true,
    )

    private fun passwordChange(name: String, state: PasswordChangeUiState, required: Boolean = true) =
        captureScreenshots("password_change_$name") {
            PasswordChangeContent(
                state = state,
                required = required,
                actions = PasswordChangeActions(onBack = if (required) null else ({})),
            )
        }

    @Test
    fun passwordChangeRequired() = passwordChange("required", PasswordChangeUiState())

    @Test
    fun passwordChangeValidation() = passwordChange(
        "validation",
        PasswordChangeUiState(
            current = "old",
            new = "short",
            confirm = "shorter",
            newError = R.string.feature_auth_error_new_password_short,
            confirmError = R.string.feature_auth_error_confirm_mismatch,
        ),
    )

    @Test
    fun passwordChangeWrongCurrent() = passwordChange(
        "wrong_current",
        PasswordChangeUiState(
            new = "a much longer phrase",
            confirm = "a much longer phrase",
            currentError = R.string.feature_auth_error_current_password_wrong,
        ),
    )

    @Test
    fun passwordChangeSaving() = passwordChange(
        "saving",
        PasswordChangeUiState(
            current = "old password",
            new = "a much longer phrase",
            confirm = "a much longer phrase",
            phase = PasswordChangePhase.Saving,
        ),
    )

    @Test
    fun passwordChangeError() = passwordChange(
        "error",
        PasswordChangeUiState(
            current = "old password",
            new = "a much longer phrase",
            confirm = "a much longer phrase",
            errorMessage = NetworkR.string.core_network_error_server,
        ),
    )

    @Test
    fun passwordChangeDone() = passwordChange("done", PasswordChangeUiState(phase = PasswordChangePhase.Done))

    @Test
    fun passwordChangeContinuing() = passwordChange(
        "continuing",
        PasswordChangeUiState(phase = PasswordChangePhase.Continuing),
    )

    @Test
    fun passwordChangeContinueFailed() = passwordChange(
        "continue_failed",
        PasswordChangeUiState(
            phase = PasswordChangePhase.Done,
            errorMessage = NetworkR.string.core_network_error_network,
        ),
    )

    @Test
    fun passwordChangeDoneVoluntary() = passwordChange(
        "done_voluntary",
        PasswordChangeUiState(phase = PasswordChangePhase.Done),
        required = false,
    )

    @Test
    fun passwordChangeVoluntary() = passwordChange("voluntary", PasswordChangeUiState(), required = false)

    @Test
    fun notificationPrimer() = captureScreenshots("notification_primer_default") {
        NotificationPrimerContent(onEnable = {}, onNotNow = {})
    }

    @Test
    fun notificationPrimerRequesting() = captureScreenshots("notification_primer_requesting") {
        NotificationPrimerContent(onEnable = {}, onNotNow = {}, requesting = true)
    }

    private fun onboarding(name: String, state: OnboardingUiState) = captureScreenshots("onboarding_required_$name") {
        OnboardingRequiredContent(state = state, onCheckAgain = {}, onSignOut = {})
    }

    @Test
    fun onboardingDefault() = onboarding("default", OnboardingUiState())

    @Test
    fun onboardingChecking() = onboarding("checking", OnboardingUiState(checking = true))

    @Test
    fun onboardingStillPending() = onboarding("still_pending", OnboardingUiState(stillPending = true))

    @Test
    fun onboardingError() = onboarding(
        "error",
        OnboardingUiState(errorMessage = NetworkR.string.core_network_error_network),
    )

    private fun bootstrap(name: String, state: BootstrapFailedUiState) = captureScreenshots("bootstrap_failed_$name") {
        BootstrapFailedContent(state = state, onRetry = {}, onSignOut = {})
    }

    @Test
    fun bootstrapOffline() = bootstrap(
        "offline",
        BootstrapFailedUiState(message = NetworkR.string.core_network_error_network, isOffline = true),
    )

    @Test
    fun bootstrapServer() = bootstrap(
        "server",
        BootstrapFailedUiState(message = NetworkR.string.core_network_error_server),
    )

    @Test
    fun bootstrapRetrying() = bootstrap(
        "retrying",
        BootstrapFailedUiState(message = NetworkR.string.core_network_error_server, retrying = true),
    )
}
