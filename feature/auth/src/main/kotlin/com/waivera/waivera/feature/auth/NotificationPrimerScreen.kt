package com.waivera.waivera.feature.auth

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
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

/**
 * One kind of notification the primer explains. The primer groups the five channels into the
 * three kinds people recognise (messages, schedule, digest) so it fits a small phone without
 * scrolling; Settings lists each channel separately.
 */
internal data class PrimerKind(
    val categories: List<NotificationCategory>,
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

internal val PrimerKinds: List<PrimerKind> = listOf(
    PrimerKind(
        categories = listOf(NotificationCategory.MESSAGES, NotificationCategory.GROUP_ACTIVITY),
        icon = WaiveraIcons.Chat,
        title = R.string.feature_auth_primer_messages_title,
        body = R.string.feature_auth_primer_messages_body,
    ),
    PrimerKind(
        categories = listOf(NotificationCategory.SCHEDULE_REMINDERS, NotificationCategory.SCHEDULE_CHANGES),
        icon = WaiveraIcons.Calendar,
        title = R.string.feature_auth_primer_schedule_title,
        body = R.string.feature_auth_primer_schedule_body,
    ),
    PrimerKind(
        categories = listOf(NotificationCategory.DIGEST),
        icon = WaiveraIcons.Announcement,
        title = R.string.feature_auth_primer_digest_title,
        body = R.string.feature_auth_primer_digest_body,
    ),
)

@Composable
internal fun NotificationPrimerContent(
    onEnable: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    requesting: Boolean = false,
) {
    AuthScaffold(
        modifier = modifier,
        bottomBar = {
            WaiveraButton(
                text = stringResource(R.string.feature_auth_primer_enable),
                onClick = onEnable,
                loading = requesting,
                loadingText = stringResource(R.string.feature_auth_primer_requesting),
                fullWidth = true,
            )
            WaiveraButton(
                text = stringResource(R.string.feature_auth_primer_not_now),
                onClick = onNotNow,
                variant = WaiveraButtonVariant.Text,
                enabled = !requesting,
                fullWidth = true,
            )
        },
    ) {
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
            SampleNotification()
            PrimerKinds.forEach { item -> PrimerKindRow(item) }
            Text(
                text = stringResource(R.string.feature_auth_primer_control),
                style = MaterialTheme.typography.bodyMedium,
                color = WaiveraTheme.colors.textSecondary,
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
        }
    }
}

/**
 * A mocked notification, so the person sees what they are agreeing to. TalkBack reads it as one
 * example ("Example notification: …") rather than as a real alert.
 */
@Composable
private fun SampleNotification() {
    val colors = WaiveraTheme.colors
    val description = stringResource(R.string.feature_auth_primer_sample_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, WaiveraShapes.Card)
            .border(WaiveraSpacing.Hairline, colors.border, WaiveraShapes.Card)
            .padding(WaiveraSpacing.MediumSmall)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(WaiveraIcons.WaiveraMark),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
        ) {
            Text(
                text = stringResource(R.string.feature_auth_primer_sample_app),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = stringResource(R.string.feature_auth_primer_sample_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
            Text(
                text = stringResource(R.string.feature_auth_primer_sample_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
        }
    }
}

@Composable
private fun PrimerKindRow(item: PrimerKind) {
    val colors = WaiveraTheme.colors
    Row(
        // One TalkBack stop per kind: title and explanation read together.
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

@WaiveraPreviews
@Composable
private fun NotificationPrimerRequestingPreview() {
    WaiveraTheme {
        NotificationPrimerContent(onEnable = {}, onNotNow = {}, requesting = true)
    }
}
