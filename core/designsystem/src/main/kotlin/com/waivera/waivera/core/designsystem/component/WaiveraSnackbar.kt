package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Snackbar host styled for the app. Place one per screen above any bottom bar or composer. */
@Composable
fun WaiveraSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        WaiveraSnackbar(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            onAction = data::performAction,
        )
    }
}

/** A single snackbar. The action uses the inverse accent so it meets contrast on the dark surface. */
@Composable
fun WaiveraSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = WaiveraTheme.colors
    Snackbar(
        modifier = modifier.padding(WaiveraSpacing.MediumSmall),
        shape = WaiveraShapes.Card,
        containerColor = colors.text,
        contentColor = colors.background,
        action = if (actionLabel != null) {
            {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, color = colors.inverseAccent, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            null
        },
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Shows "[message] · Undo" for a reversible action and runs [onUndo] if the person taps Undo.
 * Use this instead of a confirmation when the action can be reversed.
 */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String, onUndo: () -> Unit) {
    val result = showSnackbar(
        message = message,
        actionLabel = undoLabel,
        withDismissAction = false,
        duration = SnackbarDuration.Long,
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

@WaiveraPreviews
@Composable
private fun WaiveraSnackbarPreview() {
    WaiveraTheme {
        WaiveraSnackbar(message = "Bookmark removed", actionLabel = "Undo")
    }
}
