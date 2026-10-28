package com.waivera.waivera.feature.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.error.WaiveraError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInPhase { Idle, SigningIn, SendingReset }

/**
 * Everything the sign-in screen shows. Messages are string resources so raw Firebase or server
 * text can never reach the screen.
 */
@Immutable
data class SignInUiState(
    val email: String = "",
    val password: String = "",
    @StringRes val emailError: Int? = null,
    @StringRes val passwordError: Int? = null,
    val phase: SignInPhase = SignInPhase.Idle,
    @StringRes val errorMessage: Int? = null,
    /** The address a reset link was requested for, shown back to the person. */
    val resetSentTo: String? = null,
    /** Non-null when the build has no server or Firebase settings; sign-in is then disabled. */
    val missingConfiguration: List<String>? = null,
    /** The person just signed out on this device: confirm it instead of showing a bare form. */
    val signedOutNotice: Boolean = false,
    /** A Terms of Service or Privacy Policy link found nothing that can open it (no browser). */
    val linkUnavailable: Boolean = false,
) {
    val isConfigured: Boolean get() = missingConfiguration == null
    val fieldsEnabled: Boolean get() = isConfigured && phase == SignInPhase.Idle
    val canSubmit: Boolean get() = fieldsEnabled && email.isNotBlank() && password.isNotEmpty()
    val isSigningIn: Boolean get() = phase == SignInPhase.SigningIn

    /** A banner shows above the form (configuration, failure, signed out or reset sent). */
    val hasBanner: Boolean
        get() = missingConfiguration != null ||
            errorMessage != null ||
            signedOutNotice ||
            resetSentTo != null ||
            linkUnavailable

    /** Never print the password, even in a debug log or a crash report of this object. */
    override fun toString(): String =
        "SignInUiState(emailSet=${email.isNotEmpty()}, passwordSet=${password.isNotEmpty()}, phase=$phase, " +
            "emailError=$emailError, passwordError=$passwordError, errorMessage=$errorMessage, " +
            "resetSent=${resetSentTo != null}, missingConfiguration=$missingConfiguration, " +
            "signedOutNotice=$signedOutNotice, linkUnavailable=$linkUnavailable)"
}

/** Haptic moments: [Warning] is a failure the person did not cause and can retry (UI2-15). */
enum class AuthEffect { Error, Warning, Success }

/**
 * Sign-in form. The session repository owns Firebase and the backend bootstrap; this only
 * validates, submits once, and maps failures to copy. On success the shell's gate moves on by
 * itself, so there is no navigation here.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val session: SessionRepository,
    private val savedState: SavedStateHandle,
    networkConfig: NetworkConfig,
) : ViewModel() {
    /** The footer's legal pages, from the configured web base; null hides the footer. */
    val legalLinks: LegalLinks? = LegalLinks.from(networkConfig.webBase)

    // The email survives process death; the password deliberately does not.
    private val _state = MutableStateFlow(SignInUiState(email = savedState[KEY_EMAIL] ?: ""))
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _effects = Channel<AuthEffect>(Channel.BUFFERED)
    val effects: Flow<AuthEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.state.collect(::onSession)
        }
    }

    private fun onSession(session: SessionState) {
        when (session) {
            is SessionState.ConfigurationMissing ->
                _state.update { it.copy(missingConfiguration = session.missingKeys, phase = SignInPhase.Idle) }
            is SessionState.SignedOut -> {
                val reason = session.reason
                if (reason != null && _state.value.phase == SignInPhase.Idle) {
                    _state.update { it.copy(errorMessage = reason.messageRes, resetSentTo = null) }
                } else if (session.byUser) {
                    _state.update { it.copy(signedOutNotice = true) }
                }
            }
            // Signed in: forget the credentials. This ViewModel outlives the screen, and the next
            // person to sign in on a shared device should start from an empty form.
            is SessionState.SignedIn, is SessionState.PasswordChangeRequired -> {
                savedState[KEY_EMAIL] = ""
                _state.update { SignInUiState(missingConfiguration = it.missingConfiguration) }
            }
            SessionState.Initializing, is SessionState.BootstrapFailed -> Unit
        }
    }

    fun onEmailChange(value: String) {
        savedState[KEY_EMAIL] = value
        _state.update {
            it.copy(email = value, emailError = null, errorMessage = null, resetSentTo = null, signedOutNotice = false)
        }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, errorMessage = null) }
    }

    fun signIn() {
        val current = _state.value
        if (!current.fieldsEnabled) return
        val emailError = AuthValidation.emailError(current.email)
        val passwordError = AuthValidation.signInPasswordError(current.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            _effects.trySend(AuthEffect.Error)
            return
        }
        _state.update {
            it.copy(phase = SignInPhase.SigningIn, errorMessage = null, resetSentTo = null, signedOutNotice = false)
        }
        viewModelScope.launch {
            session.signIn(AuthValidation.normalizeEmail(current.email), current.password)
                .onSuccess {
                    _state.update { it.copy(phase = SignInPhase.Idle, password = "") }
                }
                .onFailure { failure ->
                    val error = WaiveraError.from(failure)
                    _state.update { it.copy(phase = SignInPhase.Idle, errorMessage = error.messageRes) }
                    _effects.trySend(AuthEffect.Error)
                }
        }
    }

    fun sendPasswordReset() {
        val current = _state.value
        if (!current.fieldsEnabled) return
        val emailError = AuthValidation.emailError(current.email)
        if (emailError != null) {
            _state.update { it.copy(emailError = emailError) }
            _effects.trySend(AuthEffect.Error)
            return
        }
        val email = AuthValidation.normalizeEmail(current.email)
        _state.update { it.copy(phase = SignInPhase.SendingReset, errorMessage = null, resetSentTo = null) }
        viewModelScope.launch {
            val result = session.sendPasswordReset(email)
            val error = result.exceptionOrNull()?.let(WaiveraError::from)
            // An unknown address reads as sent, so the screen never reveals who has an account.
            // No haptic: the banner is the confirmation, and "sent" never vibrates (UI6-07).
            if (error == null || error.hidesAccountExistence()) {
                _state.update { it.copy(phase = SignInPhase.Idle, resetSentTo = email) }
            } else {
                _state.update { it.copy(phase = SignInPhase.Idle, errorMessage = error.messageRes) }
                _effects.trySend(AuthEffect.Error)
            }
        }
    }

    fun dismissMessage() {
        _state.update {
            it.copy(errorMessage = null, resetSentTo = null, signedOutNotice = false, linkUnavailable = false)
        }
    }

    /**
     * Nothing on the device could open a legal page. Says so calmly, with no haptic: the person
     * did nothing wrong and their typing is untouched.
     */
    fun onLinkUnavailable() {
        _state.update { it.copy(linkUnavailable = true) }
    }

    private fun WaiveraError.hidesAccountExistence(): Boolean =
        this is WaiveraError.InvalidCredentials || this is WaiveraError.NotFound

    private companion object {
        const val KEY_EMAIL = "sign_in_email"
    }
}
