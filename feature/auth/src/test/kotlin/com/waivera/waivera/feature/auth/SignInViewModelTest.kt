package com.waivera.waivera.feature.auth

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.designsystem.feedback.HapticMoment
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.R as NetworkR
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.testing.MainDispatcherRule
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SignInViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val session = FakeSessionRepository()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle(), webBase: String = WEB_BASE) = SignInViewModel(
        session = session,
        savedState = saved,
        networkConfig = NetworkConfig(apiBaseUrl = API_BASE, webBaseUrl = webBase),
    )

    @Test
    fun `the legal links come from the configured web base`() {
        val links = viewModel().legalLinks
        assertEquals("$WEB_BASE/terms-of-service", links?.termsOfService)
        assertEquals("$WEB_BASE/privacy-policy", links?.privacyPolicy)
    }

    @Test
    fun `a build without a web base shows no legal links`() {
        assertNull(viewModel(webBase = "").legalLinks)
    }

    /** No browser: a calm banner, no haptic, and what the person typed stays. */
    @Test
    fun `a link nothing can open says so and keeps the form`() = runTest {
        val vm = viewModel()
        vm.fill()
        vm.effects.test {
            vm.onLinkUnavailable()
            expectNoEvents()
        }
        assertTrue(vm.state.value.linkUnavailable)
        assertTrue(vm.state.value.hasBanner)
        assertEquals("alex.rivera@example.org", vm.state.value.email)
        assertEquals("correct horse", vm.state.value.password)
        vm.dismissMessage()
        assertFalse(vm.state.value.linkUnavailable)
        assertFalse(vm.state.value.hasBanner)
    }

    private fun SignInViewModel.fill(email: String = "alex.rivera@example.org", password: String = "correct horse") {
        onEmailChange(email)
        onPasswordChange(password)
    }

    @Test
    fun `signing out confirms it on the form, a cold start does not`() {
        val vm = viewModel()
        assertFalse(vm.state.value.signedOutNotice, "nothing to confirm on a cold start")
        session.mutableState.value = SessionState.SignedIn(FakeSessionRepository.USER)
        session.mutableState.value = SessionState.SignedOut(byUser = true)
        assertTrue(vm.state.value.signedOutNotice)
        vm.onEmailChange("a")
        assertFalse(vm.state.value.signedOutNotice, "typing dismisses the notice")
    }

    @Test
    fun `a session that ended on its own shows the reason, not the sign-out notice`() {
        val vm = viewModel()
        session.mutableState.value = SessionState.SignedIn(FakeSessionRepository.USER)
        session.mutableState.value = SessionState.SignedOut(WaiveraError.SessionExpired())
        assertFalse(vm.state.value.signedOutNotice)
        assertEquals(NetworkR.string.core_network_error_session_expired, vm.state.value.errorMessage)
    }

    @Test
    fun `empty form cannot submit and makes no call`() {
        val vm = viewModel()
        assertFalse(vm.state.value.canSubmit)
        vm.onEmailChange("   ")
        vm.onPasswordChange("x")
        assertFalse(vm.state.value.canSubmit, "a blank email keeps the button disabled")
        vm.onEmailChange("a@b.co")
        vm.onPasswordChange("")
        assertFalse(vm.state.value.canSubmit, "an empty password keeps the button disabled")
        vm.onPasswordChange("x")
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `submit trims the email but never the password`() {
        val vm = viewModel()
        vm.fill(email = "  alex.rivera@example.org ", password = " spaced pass ")
        vm.signIn()
        assertEquals(listOf("alex.rivera@example.org" to " spaced pass "), session.signIns)
    }

    @Test
    fun `invalid email shows a field error, no vibration or shake, and no call`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.fill(email = "alex.rivera")
            vm.signIn()
            // Haptics policy: only rejected credentials vibrate, not a field the person can see.
            expectNoEvents()
        }
        assertEquals(R.string.feature_auth_error_email_invalid, vm.state.value.emailError)
        assertTrue(session.signIns.isEmpty())
    }

    @Test
    fun `go on the keyboard with no password shows the password error`() {
        val vm = viewModel()
        vm.onEmailChange("alex.rivera@example.org")
        vm.signIn()
        assertEquals(R.string.feature_auth_error_password_required, vm.state.value.passwordError)
        assertTrue(session.signIns.isEmpty())
    }

    @Test
    fun `loading - while signing in the form is locked and a second tap is ignored`() {
        val gate = CompletableDeferred<Unit>()
        session.gate = gate
        val vm = viewModel()
        vm.fill()
        vm.signIn()
        assertEquals(SignInPhase.SigningIn, vm.state.value.phase)
        assertTrue(vm.state.value.isSigningIn, "the button shows Connecting to Waivera")
        assertFalse(vm.state.value.fieldsEnabled)
        vm.signIn()
        assertEquals(1, session.signIns.size)
        gate.complete(Unit)
        assertEquals(SignInPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `success clears the credentials so the next person starts empty`() {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.fill()
        vm.signIn()
        assertEquals("", vm.state.value.password)
        assertEquals("", vm.state.value.email)
        assertNull(vm.state.value.errorMessage)
        session.mutableState.value = SessionState.SignedOut()
        assertEquals("", viewModel(saved).state.value.email)
    }

    @Test
    fun `password change gate also counts as signed in`() {
        session.stateAfterSignIn = SessionState.PasswordChangeRequired(FakeSessionRepository.USER)
        val vm = viewModel()
        vm.fill()
        vm.signIn()
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun `error - wrong password maps to client copy and keeps the fields`() = runTest {
        session.nextSignIn = Result.failure(WaiveraError.InvalidCredentials())
        val vm = viewModel()
        vm.effects.test {
            vm.fill()
            vm.signIn()
            assertEquals(SignInEffect.Haptic(HapticMoment.SignInRejected), awaitItem())
            assertEquals(SignInEffect.Shake, awaitItem())
        }
        val state = vm.state.value
        assertEquals(NetworkR.string.core_network_error_invalid_credentials, state.errorMessage)
        assertEquals("correct horse", state.password)
        assertEquals(SignInPhase.Idle, state.phase)
    }

    /** Haptics policy 2026-10-29: a wrong sign-in is one Reject vibration and one shake. */
    @Test
    fun `wrong credentials vibrate once and shake once per attempt`() = runTest {
        session.nextSignIn = Result.failure(WaiveraError.InvalidCredentials())
        val vm = viewModel()
        vm.effects.test {
            vm.fill()
            vm.signIn()
            assertEquals(SignInEffect.Haptic(HapticMoment.SignInRejected), awaitItem())
            assertEquals(SignInEffect.Shake, awaitItem())
            expectNoEvents()
            vm.signIn()
            assertEquals(SignInEffect.Haptic(HapticMoment.SignInRejected), awaitItem(), "each attempt runs again")
            assertEquals(SignInEffect.Shake, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `a network, offline or server failure neither vibrates nor shakes`() = runTest {
        listOf(
            WaiveraError.Network(),
            WaiveraError.Timeout(),
            WaiveraError.Server(503),
            WaiveraError.TooManyAttempts(),
            WaiveraError.AccountDisabled(),
        ).forEach { error ->
            session.nextSignIn = Result.failure(error)
            val vm = viewModel()
            vm.effects.test {
                vm.fill()
                vm.signIn()
                expectNoEvents()
            }
            assertEquals(error.messageRes, vm.state.value.errorMessage, "${error::class.simpleName} keeps its message")
        }
    }

    @Test
    fun `a failed password reset shows its message without a vibration`() = runTest {
        session.nextReset = Result.failure(WaiveraError.Network())
        val vm = viewModel()
        vm.effects.test {
            vm.onEmailChange("alex.rivera@example.org")
            vm.sendPasswordReset()
            expectNoEvents()
        }
        assertEquals(NetworkR.string.core_network_error_network, vm.state.value.errorMessage)
    }

    @Test
    fun `raw provider text never reaches the screen`() {
        session.nextSignIn = Result.failure(IllegalStateException("Firebase: Error (auth/wrong-password)."))
        val vm = viewModel()
        vm.fill()
        vm.signIn()
        assertEquals(NetworkR.string.core_network_error_unexpected, vm.state.value.errorMessage)
    }

    @Test
    fun `each mapped failure shows its own message`() {
        val cases = listOf(
            WaiveraError.AccountDisabled() to NetworkR.string.core_network_error_account_disabled,
            WaiveraError.TooManyAttempts() to NetworkR.string.core_network_error_too_many_attempts,
            WaiveraError.Network() to NetworkR.string.core_network_error_network,
            WaiveraError.AccountNotProvisioned(404) to NetworkR.string.core_network_error_account_not_provisioned,
        )
        cases.forEach { (error, expected) ->
            session.nextSignIn = Result.failure(error)
            val vm = viewModel()
            vm.fill()
            vm.signIn()
            assertEquals(expected, vm.state.value.errorMessage, error::class.simpleName)
        }
    }

    @Test
    fun `reconnect - a sign-in that failed offline succeeds when tried again`() {
        session.nextSignIn = Result.failure(WaiveraError.Network())
        val vm = viewModel()
        vm.fill()
        vm.signIn()
        assertEquals(NetworkR.string.core_network_error_network, vm.state.value.errorMessage)
        session.nextSignIn = Result.success(Unit)
        vm.signIn()
        assertNull(vm.state.value.errorMessage)
        assertEquals(2, session.signIns.size)
    }

    @Test
    fun `a session ended by the backend shows why on the sign-in screen`() {
        val vm = viewModel()
        session.mutableState.value = SessionState.SignedOut(WaiveraError.SessionExpired())
        assertEquals(NetworkR.string.core_network_error_session_expired, vm.state.value.errorMessage)
    }

    @Test
    fun `editing a field clears the error`() {
        session.nextSignIn = Result.failure(WaiveraError.InvalidCredentials())
        val vm = viewModel()
        vm.fill()
        vm.signIn()
        vm.onPasswordChange("another")
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `missing configuration disables the form and never signs in`() {
        session.mutableState.value = SessionState.ConfigurationMissing(listOf("waivera.apiBaseUrl"))
        val vm = viewModel()
        assertEquals(listOf("waivera.apiBaseUrl"), vm.state.value.missingConfiguration)
        assertFalse(vm.state.value.fieldsEnabled)
        vm.fill()
        vm.signIn()
        vm.sendPasswordReset()
        assertTrue(session.signIns.isEmpty())
        assertTrue(session.resets.isEmpty())
    }

    @Test
    fun `password reset needs a valid email first`() {
        val vm = viewModel()
        vm.sendPasswordReset()
        assertEquals(R.string.feature_auth_error_email_required, vm.state.value.emailError)
        assertTrue(session.resets.isEmpty())
    }

    /** The banner confirms; a sent link plays no haptic, as no "sent" does (UI6-07). */
    @Test
    fun `password reset confirms with the trimmed address and no haptic`() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onEmailChange(" alex.rivera@example.org ")
            vm.sendPasswordReset()
            expectNoEvents()
        }
        assertEquals(listOf("alex.rivera@example.org"), session.resets)
        assertEquals("alex.rivera@example.org", vm.state.value.resetSentTo)
    }

    @Test
    fun `password reset for an unknown address reads as sent`() {
        session.nextReset = Result.failure(WaiveraError.InvalidCredentials())
        val vm = viewModel()
        vm.onEmailChange("nobody@example.org")
        vm.sendPasswordReset()
        assertEquals("nobody@example.org", vm.state.value.resetSentTo)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `password reset offline shows the network message`() {
        session.nextReset = Result.failure(WaiveraError.Network())
        val vm = viewModel()
        vm.onEmailChange("alex.rivera@example.org")
        vm.sendPasswordReset()
        assertNull(vm.state.value.resetSentTo)
        assertEquals(NetworkR.string.core_network_error_network, vm.state.value.errorMessage)
    }

    @Test
    fun `the email survives process death but the password does not`() {
        val saved = SavedStateHandle()
        viewModel(saved).fill()
        val restored = viewModel(saved).state.value
        assertEquals("alex.rivera@example.org", restored.email)
        assertEquals("", restored.password)
    }

    @Test
    fun `state toString never prints the password`() {
        val vm = viewModel()
        vm.fill(password = "hunter2-secret")
        assertFalse(vm.state.value.toString().contains("hunter2-secret"))
        assertFalse(vm.state.value.toString().contains("alex.rivera"))
    }

    private companion object {
        const val API_BASE = "https://api.example.test"
        const val WEB_BASE = "https://app.example.test"
    }
}
