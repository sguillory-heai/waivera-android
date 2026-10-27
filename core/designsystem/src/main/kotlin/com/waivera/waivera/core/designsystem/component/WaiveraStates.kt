package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The shared empty-state block: icon tile, title, message and one clear action. Every screen
 * uses this so copy and spacing stay consistent.
 */
@Composable
fun WaiveraEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateBlock(
        title = title,
        message = message,
        modifier = modifier,
        icon = icon,
        actionLabel = actionLabel,
        onAction = onAction,
    )
}

/** Error state with a user-facing message (never raw server text) and a retry action. */
@Composable
fun WaiveraErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.core_designsystem_error_title),
) {
    StateBlock(
        title = title,
        message = message,
        modifier = modifier,
        icon = WaiveraIcons.Error,
        actionLabel = stringResource(R.string.core_designsystem_try_again),
        onAction = onRetry,
    )
}

/** Inline loading block for small areas. Lists use [SkeletonList] instead. */
@Composable
fun WaiveraLoadingState(caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(WaiveraSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        CircularProgressIndicator(color = WaiveraTheme.colors.accent)
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The one full-screen loading screen, shown only by the auth gate before the first session state. */
@Composable
fun WaiveraLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        WaiveraLoadingState(caption = stringResource(R.string.core_designsystem_loading_app))
    }
}

@Composable
private fun StateBlock(
    title: String,
    message: String,
    modifier: Modifier,
    @DrawableRes icon: Int?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Large, vertical = WaiveraSpacing.ExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(WaiveraSizes.StateIconTile)
                    .background(colors.accentSoft, WaiveraShapes.Button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = colors.onAccentSoft,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            WaiveraButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = WaiveraSpacing.Small),
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraStatesPreview() {
    WaiveraTheme {
        Column {
            WaiveraEmptyState(
                title = "All caught up",
                message = "New messages and mentions will show up here.",
                icon = WaiveraIcons.Check,
            )
            WaiveraErrorState(message = "We couldn't load your conversations. Check your connection.", onRetry = {})
        }
    }
}
