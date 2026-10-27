package com.waivera.waivera.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
internal data class NotificationSettingsUiState(
    val preferences: NotificationPreferences = NotificationPreferences(),
    /** False when Android blocks every notification from the app; the switches cannot fix that. */
    val systemAllowed: Boolean = true,
    val blockedChannels: Set<String> = emptySet(),
    val loaded: Boolean = false,
) {
    /** Category and preview switches are dimmed, not changed, while push is off (spec 08 §8). */
    val categoriesEnabled: Boolean get() = preferences.enabled

    fun isChecked(category: NotificationCategory): Boolean = preferences.categories[category] != false
}

/**
 * Notification preferences by meaning. These are server preferences: the data layer sends the
 * full set at registration (G-08) and registers again whenever they change. Turning push off
 * re-registers with `pushEnabled = false` rather than unregistering: the server keeps the device
 * and stops sending to it. Sound and vibration belong to Android's per-channel settings, which the
 * screen links to, because an app cannot change a channel after creating it.
 */
@HiltViewModel
internal class NotificationSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val system: SystemNotificationStatus,
) : ViewModel() {
    private val systemState = MutableStateFlow(readSystem())

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        settings.settings,
        systemState,
    ) { app, (allowed, blocked) ->
        NotificationSettingsUiState(
            preferences = app.notifications,
            systemAllowed = allowed,
            blockedChannels = blocked,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NotificationSettingsUiState())

    /** Call on resume: the person may have changed things in Android settings. */
    fun refreshSystemState() {
        systemState.value = readSystem()
    }

    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setNotificationsEnabled(enabled) }
    }

    fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
        if (!uiState.value.categoriesEnabled) return
        viewModelScope.launch { settings.setCategoryEnabled(category, enabled) }
    }

    fun setPreviewContent(enabled: Boolean) {
        if (!uiState.value.categoriesEnabled) return
        viewModelScope.launch { settings.setPreviewContent(enabled) }
    }

    private fun readSystem(): Pair<Boolean, Set<String>> =
        system.appNotificationsAllowed() to system.blockedChannelIds()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
