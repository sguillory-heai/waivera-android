package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
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

/**
 * A single snackbar on the hero's plum in both themes, so a passing note carries the brand and
 * never glares as a white block in dark. White text; the action in the light lilac (7.4:1 or
 * better on both plums).
 */
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
        shape = WaiveraShapes.Snackbar,
        containerColor = colors.snackbar,
        contentColor = colors.onSnackbar,
        action = if (actionLabel != null) {
            {
                val interactionSource = remember { MutableInteractionSource() }
                val press = rememberPressFeedback(interactionSource)
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.pressFeedback(press),
                    interactionSource = interactionSource,
                ) {
                    Text(text = actionLabel, color = colors.snackbarAction, style = MaterialTheme.typography.labelLarge)
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
