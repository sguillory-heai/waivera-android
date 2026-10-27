package com.waivera.waivera.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the settings screen. Owned by `:feature:settings`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_settings_settings_title),
        message = stringResource(R.string.feature_settings_settings_placeholder),
        icon = WaiveraIcons.Settings,
        onBack = onBack,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun SettingsScreenPreview() {
    WaiveraTheme {
        SettingsScreen(onBack = {}, onOpenNotificationSettings = {}, onOpenAppearance = {})
    }
}
