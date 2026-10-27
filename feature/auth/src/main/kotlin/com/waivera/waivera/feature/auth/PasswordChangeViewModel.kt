package com.waivera.waivera.feature.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.repository.SessionRepository
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

/** [Continuing]: the password is saved and the required flow is reloading the session to move on. */
enum class PasswordChangePhase { Editing, Saving, Done, Continuing }

@Immutable
data class PasswordChangeUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    @StringRes val currentError: Int? = null,
    @StringRes val newError: Int? = null,
    @StringRes val confirmError: Int? = null,
    @StringRes val errorMessage: Int? = null,
    val phase: PasswordChangePhase = PasswordChangePhase.Editing,
) {
    val fieldsEnabled: Boolean get() = phase == PasswordChangePhase.Editing
    val canSubmit: Boolean get() = fieldsEnabled && current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty()

    override fun toString(): String =
        "PasswordChangeUiState(phase=$phase, currentError=$currentError, newError=$newError, " +
            "confirmError=$confirmError, errorMessage=$errorMessage)"
}

/**
 * Changes the password: Firebase re-authenticates with the current password and sets the new
 * one, then the backend's `confirm-password-change` clears the gate and the session refreshes
 * (all inside [SessionRepository.changePassword]). Used for the required gate and for the
 * voluntary change from Settings; the screen decides what happens after [AuthEffect.Success].
 */
@HiltViewModel
class PasswordChangeViewModel @Inject constructor(private val session: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(PasswordChangeUiState())
    val state: StateFlow<PasswordChangeUiState> = _state.asStateFlow()

    private val _effects = Channel<AuthEffect>(Channel.BUFFERED)
    val effects: Flow<AuthEffect> = _effects.receiveAsFlow()

    init {
        // The gate's ViewModel can outlive a sign-out; never show one person's typing to the next.
        viewModelScope.launch {
            session.state.collect { if (it is SessionState.SignedOut) _state.value = PasswordChangeUiState() }
        }
    }

    fun onCurrentChange(value: String) =
        _state.update { it.copy(current = value, currentError = null, errorMessage = null) }

    fun onNewChange(value: String) = _state.update { it.copy(new = value, newError = null, errorMessage = null) }

    fun onConfirmChange(value: String) =
        _state.update { it.copy(confirm = value, confirmError = null, errorMessage = null) }

    fun submit() {
        val form = _state.value
        if (!form.fieldsEnabled) return
        val errors = AuthValidation.passwordChangeErrors(form.current, form.new, form.confirm)
        if (!errors.isValid) {
            _state.update {
                it.copy(currentError = errors.current, newError = errors.new, confirmError = errors.confirm)
            }
            _effects.trySend(AuthEffect.Error)
            return
        }
        _state.update { it.copy(phase = PasswordChangePhase.Saving, errorMessage = null) }
        viewModelScope.launch {
            session.changePassword(form.current, form.new)
                .onSuccess {
                    // Clear every secret from memory once it has been used.
                    _state.value = PasswordChangeUiState(phase = PasswordChangePhase.Done)
                    _effects.trySend(AuthEffect.Success)
                }
                .onFailure { failure -> onFailure(WaiveraError.from(failure)) }
        }
    }

    private fun onFailure(error: WaiveraError) {
        _state.update {
            when (error) {
                // Wrong current password: point at that field and let them retype it.
                is WaiveraError.InvalidCredentials, is WaiveraError.RecentLoginRequired -> it.copy(
                    phase = PasswordChangePhase.Editing,
                    current = "",
                    currentError = R.string.feature_auth_error_current_password_wrong,
                )
                is WaiveraError.WeakPassword -> it.copy(
                    phase = PasswordChangePhase.Editing,
                    newError = error.messageRes,
                )
                else -> it.copy(phase = PasswordChangePhase.Editing, errorMessage = error.messageRes)
            }
        }
        _effects.trySend(AuthEffect.Error)
    }

    fun dismissMessage() = _state.update { it.copy(errorMessage = null) }

    /**
     * The required flow's "Continue": the gate normally moves on by itself once the session
     * refreshes, so this re-runs that refresh when it has not happened (offline, slow API).
     * A failure keeps the success screen and says why, so Continue can be tried again.
     */
    fun continueToApp() {
        if (_state.value.phase != PasswordChangePhase.Done) return
        _state.update { it.copy(phase = PasswordChangePhase.Continuing, errorMessage = null) }
        viewModelScope.launch {
            session.refresh().onFailure { failure ->
                _state.update {
                    it.copy(phase = PasswordChangePhase.Done, errorMessage = WaiveraError.from(failure).messageRes)
                }
                _effects.trySend(AuthEffect.Error)
            }
        }
    }
}
