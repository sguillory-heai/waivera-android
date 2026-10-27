package com.waivera.waivera.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
internal data class AppearanceUiState(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val hapticsEnabled: Boolean = true,
    /** False until the stored settings arrive, so the rows never show a wrong default first. */
    val loaded: Boolean = false,
)

/**
 * Theme and haptics. The app's theme reads the same [SettingsRepository] flow, so a change
 * applies at once, and "System" follows the device as it switches.
 */
@HiltViewModel
internal class AppearanceViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<AppearanceUiState> = settings.settings
        .map(AppSettings::toAppearance)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppearanceUiState())

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHapticsEnabled(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun AppSettings.toAppearance() =
    AppearanceUiState(theme = theme, hapticsEnabled = hapticsEnabled, loaded = true)
