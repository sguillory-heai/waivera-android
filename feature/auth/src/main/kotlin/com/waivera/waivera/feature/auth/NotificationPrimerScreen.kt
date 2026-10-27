package com.waivera.waivera.feature.auth

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The one-time value primer shown after the first sign-in on Android 13+ (and after any
 * required password change). The system prompt opens only from the primary button, never on its
 * own. [onFinished] reports whether the permission was granted; either way the shell marks the
 * primer done and moves on, and Settings shows the denied state with a link to system settings.
 */
@Composable
fun NotificationPrimerScreen(onFinished: (granted: Boolean) -> Unit, modifier: Modifier = Modifier) {
    // Survives rotation while the system dialog is open, so a second tap cannot stack prompts.
    var requesting by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requesting = false
        onFinished(granted)
    }
    NotificationPrimerContent(
        requesting = requesting,
        onEnable = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requesting = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Below Android 13 notifications are on by default; the shell never shows the
                // primer there, but finishing as granted keeps this safe if it ever does.
                onFinished(true)
            }
        },
        onNotNow = { onFinished(false) },
        modifier = modifier,
    )
}

/** What each notification category is for, in the order Settings lists them. */
internal data class PrimerCategory(
    val category: NotificationCategory,
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

internal val PrimerCategories: List<PrimerCategory> = NotificationCategory.entries.map { category ->
    when (category) {
        NotificationCategory.MESSAGES -> PrimerCategory(
            category,
            WaiveraIcons.Chat,
            R.string.feature_auth_primer_messages_title,
            R.string.feature_auth_primer_messages_body,
        )
        NotificationCategory.GROUP_ACTIVITY -> PrimerCategory(
            category,
            WaiveraIcons.People,
            R.string.feature_auth_primer_group_title,
            R.string.feature_auth_primer_group_body,
        )
        NotificationCategory.SCHEDULE_REMINDERS -> PrimerCategory(
            category,
            WaiveraIcons.Schedule,
            R.string.feature_auth_primer_reminders_title,
            R.string.feature_auth_primer_reminders_body,
        )
        NotificationCategory.SCHEDULE_CHANGES -> PrimerCategory(
            category,
            WaiveraIcons.Calendar,
            R.string.feature_auth_primer_changes_title,
            R.string.feature_auth_primer_changes_body,
        )
        NotificationCategory.DIGEST -> PrimerCategory(
            category,
            WaiveraIcons.Today,
            R.string.feature_auth_primer_digest_title,
            R.string.feature_auth_primer_digest_body,
        )
    }
}

@Composable
internal fun NotificationPrimerContent(
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    requesting: Boolean = false,
) {
    AuthScaffold(modifier = modifier) {
        AuthHeader(
            title = stringResource(R.string.feature_auth_primer_title),
            message = stringResource(R.string.feature_auth_primer_message),
            icon = WaiveraIcons.Notifications,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(GutterPadding),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
        ) {
            PrimerCategories.forEach { item -> PrimerCategoryRow(item) }
            Text(
                text = stringResource(R.string.feature_auth_primer_control),
                style = MaterialTheme.typography.bodyMedium,
                color = WaiveraTheme.colors.textSecondary,
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
            WaiveraButton(
                text = stringResource(R.string.feature_auth_primer_enable),
                onClick = onEnable,
                loading = requesting,
                fullWidth = true,
            )
            WaiveraButton(
                text = stringResource(R.string.feature_auth_primer_not_now),
                onClick = onNotNow,
                variant = WaiveraButtonVariant.Text,
                enabled = !requesting,
                fullWidth = true,
            )
        }
    }
}

@Composable
private fun PrimerCategoryRow(item: PrimerCategory) {
    val colors = WaiveraTheme.colors
    Row(
        // One TalkBack stop per category: title and explanation read together.
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
        ) {
            Text(text = stringResource(item.title), style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(
                text = stringResource(item.body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun NotificationPrimerPreview() {
    WaiveraTheme {
        NotificationPrimerContent(onEnable = {}, onNotNow = {})
    }
}
