package com.waivera.waivera.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Entry point for the notifications screen. Owned by `:feature:settings`; routed by `:app`. */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_settings_notification_settings_title),
        message = stringResource(R.string.feature_settings_notification_settings_placeholder),
        icon = WaiveraIcons.Notifications,
        onBack = onBack,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun NotificationSettingsScreenPreview() {
    WaiveraTheme {
        NotificationSettingsScreen(onBack = {})
    }
}
