package com.waivera.waivera.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings screens in each major state across the shared matrix, named `<screen>_<state>`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class SettingsScreenshotTest {
    private val account = DemoData.currentUser().toAccount()

    @Test
    fun settingsPopulated() = captureScreenshots("settings_populated") {
        SettingsContent(
            state = SettingsUiState(account = account),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onOpenNotificationSettings = {},
            onOpenAppearance = {},
            onSignOut = {},
            onOpenChangePassword = {},
        )
    }

    @Test
    fun settingsSigningOut() = captureScreenshots("settings_signing_out") {
        SettingsContent(
            state = SettingsUiState(account = account, signingOut = true),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onOpenNotificationSettings = {},
            onOpenAppearance = {},
            onSignOut = {},
        )
    }

    @Test
    fun appearanceSystem() = captureScreenshots("appearance_system") {
        AppearanceContent(
            state = AppearanceUiState(theme = ThemePreference.SYSTEM, hapticsEnabled = true, loaded = true),
            onBack = {},
            onThemeChange = {},
            onHapticsChange = {},
        )
    }

    @Test
    fun appearanceDarkHapticsOff() = captureScreenshots("appearance_dark_haptics_off") {
        AppearanceContent(
            state = AppearanceUiState(theme = ThemePreference.DARK, hapticsEnabled = false, loaded = true),
            onBack = {},
            onThemeChange = {},
            onHapticsChange = {},
        )
    }

    private fun notifications(state: NotificationSettingsUiState, name: String) = captureScreenshots(name) {
        NotificationSettingsContent(
            state = state,
            onBack = {},
            onPushChange = {},
            onCategoryChange = { _, _ -> },
            onPreviewChange = {},
            onOpenSystemSettings = {},
            onOpenChannelSettings = {},
        )
    }

    @Test
    fun notificationsPopulated() = notifications(NotificationSettingsUiState(loaded = true), "notifications_populated")

    @Test
    fun notificationsPushOff() = notifications(
        NotificationSettingsUiState(
            preferences = NotificationPreferences(
                enabled = false,
                categories = NotificationCategory.entries.associateWith { it != NotificationCategory.DIGEST },
            ),
            loaded = true,
        ),
        "notifications_push_off",
    )

    @Test
    fun notificationsSystemBlocked() = notifications(
        NotificationSettingsUiState(
            systemAllowed = false,
            blockedChannels = setOf(NotificationCategory.GROUP_ACTIVITY.id),
            loaded = true,
        ),
        "notifications_system_blocked",
    )
}
