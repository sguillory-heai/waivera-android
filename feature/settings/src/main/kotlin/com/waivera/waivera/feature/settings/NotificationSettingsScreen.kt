package com.waivera.waivera.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraSectionHeader
import com.waivera.waivera.core.designsystem.component.WaiveraSwitchRow
import com.waivera.waivera.core.designsystem.component.WaiveraTopBar
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Entry point for the notifications screen. Owned by `:feature:settings`; routed by `:app`. */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: NotificationSettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSystemState() }
    NotificationSettingsContent(
        state = state,
        onBack = onBack,
        onPushChange = viewModel::setPushEnabled,
        onCategoryChange = viewModel::setCategoryEnabled,
        onPreviewChange = viewModel::setPreviewContent,
        onOpenSystemSettings = { context.startIfPresent(SystemNotificationIntents.app(context)) },
        onOpenChannelSettings = { category ->
            context.startIfPresent(SystemNotificationIntents.channel(context, category.id))
        },
        modifier = modifier,
    )
}

@Composable
internal fun NotificationSettingsContent(
    state: NotificationSettingsUiState,
    onBack: () -> Unit,
    onPushChange: (Boolean) -> Unit,
    onCategoryChange: (NotificationCategory, Boolean) -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenChannelSettings: (NotificationCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(WaiveraTheme.colors.background)) {
        WaiveraTopBar(title = stringResource(R.string.feature_settings_notification_settings_title), onBack = onBack)
        if (!state.loaded) return@Column
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (!state.systemAllowed) {
                WaiveraBanner(
                    title = stringResource(R.string.feature_settings_system_blocked_title),
                    message = stringResource(R.string.feature_settings_system_blocked_message),
                    tone = BannerTone.Warning,
                    actionLabel = stringResource(R.string.feature_settings_open_system_settings),
                    onAction = onOpenSystemSettings,
                    modifier = Modifier.padding(top = WaiveraSpacing.Small),
                )
            }
            WaiveraSectionHeader(text = stringResource(R.string.feature_settings_push_section))
            SettingsCard {
                WaiveraSwitchRow(
                    title = stringResource(R.string.feature_settings_push_title),
                    subtitle = stringResource(R.string.feature_settings_push_subtitle),
                    checked = state.preferences.enabled,
                    onCheckedChange = onPushChange,
                )
            }
            WaiveraSectionHeader(text = stringResource(R.string.feature_settings_categories_section))
            SettingsCard {
                CategoryCopy.entries.forEachIndexed { index, copy ->
                    if (index > 0) SettingsRowDivider()
                    WaiveraSwitchRow(
                        title = stringResource(copy.title),
                        subtitle = stringResource(copy.subtitle),
                        checked = state.isChecked(copy.category),
                        onCheckedChange = { onCategoryChange(copy.category, it) },
                        enabled = state.categoriesEnabled,
                    )
                }
            }
            WaiveraSectionHeader(text = stringResource(R.string.feature_settings_privacy_section))
            SettingsCard {
                WaiveraSwitchRow(
                    title = stringResource(R.string.feature_settings_previews_title),
                    subtitle = stringResource(R.string.feature_settings_previews_subtitle),
                    checked = state.preferences.previewContent,
                    onCheckedChange = onPreviewChange,
                    enabled = state.categoriesEnabled,
                )
            }
            WaiveraSectionHeader(text = stringResource(R.string.feature_settings_sound_section))
            Text(
                text = stringResource(R.string.feature_settings_sound_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = WaiveraTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.ExtraSmall),
            )
            SettingsCard(modifier = Modifier.padding(top = WaiveraSpacing.Small)) {
                CategoryCopy.entries.forEachIndexed { index, copy ->
                    if (index > 0) SettingsRowDivider()
                    val blocked = copy.category.id in state.blockedChannels
                    WaiveraListRow(
                        headline = stringResource(copy.title),
                        supporting = stringResource(
                            if (blocked) {
                                R.string.feature_settings_channel_blocked
                            } else {
                                R.string.feature_settings_channel_open
                            },
                        ),
                        onClick = { onOpenChannelSettings(copy.category) },
                        onClickLabel = stringResource(R.string.feature_settings_channel_open_label),
                        trailing = {
                            Icon(
                                painter = painterResource(R.drawable.feature_settings_ic_open_external),
                                contentDescription = null,
                                tint = WaiveraTheme.colors.textSecondary,
                                modifier = Modifier.size(WaiveraSizes.Icon),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** Some trimmed-down Android builds have no notification settings screen; do nothing there. */
private fun Context.startIfPresent(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing to open on this device.
    }
}

/** Copy per category; titles match the Android channel names so both places read the same. */
internal enum class CategoryCopy(
    val category: NotificationCategory,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
) {
    Messages(
        NotificationCategory.MESSAGES,
        R.string.feature_settings_category_messages,
        R.string.feature_settings_category_messages_detail,
    ),
    GroupActivity(
        NotificationCategory.GROUP_ACTIVITY,
        R.string.feature_settings_category_group_activity,
        R.string.feature_settings_category_group_activity_detail,
    ),
    ScheduleReminders(
        NotificationCategory.SCHEDULE_REMINDERS,
        R.string.feature_settings_category_schedule_reminders,
        R.string.feature_settings_category_schedule_reminders_detail,
    ),
    ScheduleChanges(
        NotificationCategory.SCHEDULE_CHANGES,
        R.string.feature_settings_category_schedule_changes,
        R.string.feature_settings_category_schedule_changes_detail,
    ),
    Digest(
        NotificationCategory.DIGEST,
        R.string.feature_settings_category_digest,
        R.string.feature_settings_category_digest_detail,
    ),
}

@WaiveraPreviews
@Composable
private fun NotificationSettingsScreenPreview() {
    WaiveraTheme {
        NotificationSettingsContent(
            state = NotificationSettingsUiState(systemAllowed = false, loaded = true),
            onBack = {},
            onPushChange = {},
            onCategoryChange = { _, _ -> },
            onPreviewChange = {},
            onOpenSystemSettings = {},
            onOpenChannelSettings = {},
        )
    }
}
