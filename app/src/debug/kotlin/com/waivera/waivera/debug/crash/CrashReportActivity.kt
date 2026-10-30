package com.waivera.waivera.debug.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.R
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Debug only: shows the report saved by [CrashCatcher] with Copy and Dismiss. */
class CrashReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val store = CrashReportStore(filesDir)
        val report = store.read()
        if (report == null) {
            finish()
            return
        }
        setContent {
            WaiveraTheme {
                CrashReportScreen(
                    report = report,
                    onCopy = { copy(report) },
                    onDismiss = {
                        store.clear()
                        finish()
                    },
                )
            }
        }
    }

    private fun copy(report: String) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.debug_crash_clip_label), report))
    }
}

@Composable
internal fun CrashReportScreen(
    report: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(WaiveraSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
    ) {
        Text(
            text = stringResource(R.string.debug_crash_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.debug_crash_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = report,
            style = MaterialTheme.typography.bodySmall,
            color = colors.text,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        )
        WaiveraButton(text = stringResource(R.string.debug_crash_copy), onClick = onCopy, fullWidth = true)
        WaiveraButton(
            text = stringResource(R.string.debug_crash_dismiss),
            onClick = onDismiss,
            variant = WaiveraButtonVariant.Secondary,
            fullWidth = true,
        )
    }
}

@WaiveraPreviews
@Composable
private fun CrashReportScreenPreview() {
    WaiveraTheme {
        CrashReportScreen(
            report = "Exception: java.lang.IllegalStateException: Example\n" +
                "    at com.example.Screen.render(Screen.kt:1)",
            onCopy = {},
            onDismiss = {},
        )
    }
}
