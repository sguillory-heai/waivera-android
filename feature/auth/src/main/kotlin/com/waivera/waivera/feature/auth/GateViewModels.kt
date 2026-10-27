package com.waivera.waivera.feature.auth

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.R as NetworkR
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

@Immutable
data class BootstrapFailedUiState(
    @StringRes val message: Int = NetworkR.string.core_network_error_unexpected,
    /** Network or timeout: the title says "offline" and the icon is the cloud. */
    val isOffline: Boolean = false,
    val retrying: Boolean = false,
)

/**
 * Firebase has a user but the backend bootstrap failed and nothing is cached. Shows the mapped
 * reason, retries once per tap, and leaves a way out through sign-out.
 */
@HiltViewModel
class BootstrapFailedViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BootstrapFailedUiState())
    val state: StateFlow<BootstrapFailedUiState> = _state.asStateFlow()

    private val _effects = Channel<AuthEffect>(Channel.BUFFERED)
    val effects: Flow<AuthEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.state.collect { state ->
                if (state is SessionState.BootstrapFailed) {
                    _state.update { it.copy(message = state.error.messageRes, isOffline = state.error.isOffline()) }
                }
            }
        }
    }

    fun retry() {
        if (_state.value.retrying) return
        _state.update { it.copy(retrying = true) }
        viewModelScope.launch {
            val result = session.refresh()
            _state.update { it.copy(retrying = false) }
            result.onFailure { failure ->
                val error = WaiveraError.from(failure)
                _state.update { it.copy(message = error.messageRes, isOffline = error.isOffline()) }
                _effects.trySend(AuthEffect.Error)
            }
        }
    }

    private fun WaiveraError.isOffline(): Boolean = this is WaiveraError.Network || this is WaiveraError.Timeout
}

@Immutable
data class OnboardingUiState(
    val checking: Boolean = false,
    /** The check worked but the server still says setup is not finished. */
    val stillPending: Boolean = false,
    /** The check itself failed (offline, server error). */
    @StringRes val errorMessage: Int? = null,
)

/**
 * The account exists in Firebase but its Waivera signup is not finished. Nothing can be done
 * in the app (there is no onboarding contract yet), so the person can re-check after finishing
 * setup elsewhere, or sign out.
 */
@HiltViewModel
class OnboardingRequiredViewModel @Inject constructor(
    private val session: SessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<AuthEffect>(Channel.BUFFERED)
    val effects: Flow<AuthEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            session.state.collect { if (it is SessionState.SignedOut) _state.value = OnboardingUiState() }
        }
    }

    fun checkAgain() {
        if (_state.value.checking) return
        _state.value = OnboardingUiState(checking = true)
        viewModelScope.launch {
            val failure = session.refresh().exceptionOrNull()
            // When setup is done the gate moves on by itself; only the other outcomes are shown here.
            _state.value = when {
                failure != null -> OnboardingUiState(errorMessage = WaiveraError.from(failure).messageRes)
                session.state.value.currentUser?.requiresOnboarding == true -> OnboardingUiState(stillPending = true)
                else -> OnboardingUiState()
            }
            if (_state.value.stillPending || _state.value.errorMessage != null) _effects.trySend(AuthEffect.Error)
        }
    }
}
