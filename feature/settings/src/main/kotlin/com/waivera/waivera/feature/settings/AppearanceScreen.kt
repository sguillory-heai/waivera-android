package com.waivera.waivera.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSwitchRow
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Entry point for the appearance screen. Owned by `:feature:settings`; routed by `:app`. */
@Composable
fun AppearanceScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AppearanceViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppearanceContent(
        state = state,
        onBack = onBack,
        onThemeChange = viewModel::setTheme,
        onHapticsChange = viewModel::setHapticsEnabled,
        modifier = modifier,
    )
}

@Composable
internal fun AppearanceContent(
    state: AppearanceUiState,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(title = stringResource(R.string.feature_settings_appearance_title), onBack = onBack)
        if (!state.loaded) return@Column
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            WaiveraSectionHeader(text = stringResource(R.string.feature_settings_theme_section))
            SettingsCard(modifier = Modifier.selectableGroup()) {
                ThemeOption.entries.forEachIndexed { index, option ->
                    if (index > 0) SettingsRowDivider()
                    SettingsRadioRow(
                        title = stringResource(option.title),
                        subtitle = stringResource(option.subtitle),
                        selected = state.theme == option.preference,
                        onSelect = { onThemeChange(option.preference) },
                    )
                }
            }
            WaiveraSectionHeader(text = stringResource(R.string.feature_settings_feedback_section))
            SettingsCard {
                WaiveraSwitchRow(
                    title = stringResource(R.string.feature_settings_haptics_title),
                    subtitle = stringResource(R.string.feature_settings_haptics_subtitle),
                    checked = state.hapticsEnabled,
                    onCheckedChange = onHapticsChange,
                )
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

internal enum class ThemeOption(
    val preference: ThemePreference,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
) {
    System(
        ThemePreference.SYSTEM,
        R.string.feature_settings_theme_system,
        R.string.feature_settings_theme_system_detail,
    ),
    Light(ThemePreference.LIGHT, R.string.feature_settings_theme_light, R.string.feature_settings_theme_light_detail),
    Dark(ThemePreference.DARK, R.string.feature_settings_theme_dark, R.string.feature_settings_theme_dark_detail),
}

@WaiveraPreviews
@Composable
private fun AppearanceScreenPreview() {
    WaiveraTheme {
        AppearanceContent(
            state = AppearanceUiState(theme = ThemePreference.DARK, hapticsEnabled = true, loaded = true),
            onBack = {},
            onThemeChange = {},
            onHapticsChange = {},
        )
    }
}
