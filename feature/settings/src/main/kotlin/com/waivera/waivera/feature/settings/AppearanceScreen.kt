package com.waivera.waivera.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Entry point for the appearance screen. Owned by `:feature:settings`; routed by `:app`. */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_settings_appearance_title),
        message = stringResource(R.string.feature_settings_appearance_placeholder),
        icon = WaiveraIcons.Settings,
        onBack = onBack,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun AppearanceScreenPreview() {
    WaiveraTheme {
        AppearanceScreen(onBack = {})
    }
}
