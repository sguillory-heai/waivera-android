package com.waivera.waivera.feature.auth

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The one-time value primer shown after the first sign-in on Android 13+. The system prompt
 * only opens from the primary button, never on its own. [onFinished] reports whether the
 * permission was granted; either way the shell marks the primer done and moves on.
 */
@Composable
fun NotificationPrimerScreen(onFinished: (granted: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onFinished(granted)
    }
    NotificationPrimerContent(
        onEnable = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onFinished(true)
            }
        },
        onNotNow = { onFinished(false) },
        modifier = modifier,
    )
}

@Composable
internal fun NotificationPrimerContent(onEnable: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WaiveraSpacing.Large, vertical = WaiveraSpacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
    ) {
        WaiveraIconTile(icon = WaiveraIcons.Notifications)
        Text(
            text = stringResource(R.string.feature_auth_primer_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.feature_auth_primer_message),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
        PrimerPoint(WaiveraIcons.Chat, stringResource(R.string.feature_auth_primer_messages))
        PrimerPoint(WaiveraIcons.Calendar, stringResource(R.string.feature_auth_primer_schedule))
        PrimerPoint(WaiveraIcons.Settings, stringResource(R.string.feature_auth_primer_control))
        Spacer(Modifier.padding(top = WaiveraSpacing.Small))
        WaiveraButton(
            text = stringResource(R.string.feature_auth_primer_enable),
            onClick = onEnable,
            fullWidth = true,
        )
        WaiveraButton(
            text = stringResource(R.string.feature_auth_primer_not_now),
            onClick = onNotNow,
            variant = WaiveraButtonVariant.Text,
            fullWidth = true,
        )
    }
}

@Composable
private fun PrimerPoint(@DrawableRes icon: Int, text: String) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(WaiveraSizes.Icon),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.text)
    }
}

@WaiveraPreviews
@Composable
private fun NotificationPrimerPreview() {
    WaiveraTheme {
        NotificationPrimerContent(onEnable = {}, onNotNow = {})
    }
}
