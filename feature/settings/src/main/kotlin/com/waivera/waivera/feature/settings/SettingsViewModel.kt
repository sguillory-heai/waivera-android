package com.waivera.waivera.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.CurrentUser
import com.waivera.waivera.core.data.model.currentUser
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.network.error.WaiveraError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The signed-in identity as the Account section shows it. */
@Immutable
internal data class AccountInfo(val userId: String, val name: String, val email: String?, val organization: String?)

@Immutable
internal data class SettingsUiState(
    val account: AccountInfo? = null,
    val confirmingSignOut: Boolean = false,
    val signingOut: Boolean = false,
)

internal sealed interface SettingsEffect {
    data class SignOutFailed(val error: WaiveraError) : SettingsEffect
}

/**
 * Settings index and account. Sign-out goes through [SessionRepository.signOut], which stops
 * realtime, unregisters the push token, clears caches and signs out of Firebase; the app gate
 * then shows sign-in. A second sign-out while one is running is a no-op.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(private val session: SessionRepository) : ViewModel() {
    private val confirming = MutableStateFlow(false)
    private val signingOut = MutableStateFlow(false)
    private val effects = Channel<SettingsEffect>(Channel.BUFFERED)

    val effect: Flow<SettingsEffect> = effects.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(session.state, confirming, signingOut) { state, confirm, busy ->
        SettingsUiState(account = state.currentUser?.toAccount(), confirmingSignOut = confirm, signingOut = busy)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        SettingsUiState(account = session.state.value.currentUser?.toAccount()),
    )

    fun requestSignOut() {
        if (!signingOut.value && isSignedIn()) confirming.value = true
    }

    fun dismissSignOut() {
        if (!signingOut.value) confirming.value = false
    }

    fun confirmSignOut() {
        if (signingOut.value || !isSignedIn()) return
        signingOut.value = true
        viewModelScope.launch {
            session.signOut().onFailure { error ->
                effects.send(SettingsEffect.SignOutFailed(error as? WaiveraError ?: WaiveraError.Unexpected()))
            }
            // On success the gate replaces this screen; either way the sheet closes.
            confirming.value = false
            signingOut.value = false
        }
    }

    private fun isSignedIn(): Boolean = session.state.value.currentUser != null

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun CurrentUser.toAccount() = AccountInfo(
    userId = userId,
    name = displayName?.takeIf(String::isNotBlank)
        ?: listOfNotNull(firstName, lastName).joinToString(" ").takeIf(String::isNotBlank)
        ?: email.orEmpty(),
    email = email,
    organization = organizationName?.takeIf(String::isNotBlank),
)
