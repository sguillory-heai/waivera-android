package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

enum class WaiveraButtonVariant { Primary, Secondary, Destructive, Text }

/**
 * The app's button.
 *
 * Disabled buttons keep a readable label (`textSecondary` on `surfaceMuted` with a hairline
 * border, the Material on-surface pattern) instead of fading the accent, so the person can
 * still read what the button does. Loading keeps the size, shows a small spinner next to
 * [loadingText] ("Signing out…") when given, ignores taps and announces the progress label
 * (never a blocking overlay).
 */
@Composable
fun WaiveraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WaiveraButtonVariant = WaiveraButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    fullWidth: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    loadingText: String? = null,
) {
    val colors = WaiveraTheme.colors
    val style = variant.styleFrom(colors)
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animatePressScale(interactionSource)
    val progressLabel = loadingText ?: stringResource(R.string.core_designsystem_loading)
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = WaiveraSizes.ButtonMinHeight)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .semantics { if (loading) stateDescription = progressLabel },
        enabled = enabled,
        shape = WaiveraShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = style.container,
            contentColor = style.content,
            disabledContainerColor = style.disabledContainer,
            disabledContentColor = colors.textSecondary,
        ),
        border = buttonBorder(variant, enabled, colors),
        contentPadding = PaddingValues(horizontal = WaiveraSpacing.MediumLarge, vertical = WaiveraSpacing.MediumSmall),
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ButtonLabel(text = text, leadingIcon = leadingIcon, modifier = Modifier.alpha(if (loading) 0f else 1f))
            if (loading) {
                ButtonProgress(label = loadingText, color = if (enabled) style.content else colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ButtonLabel(text: String, @DrawableRes leadingIcon: Int?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(WaiveraSizes.Icon))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Spinner, plus the progress label when there is one. The button's semantics announce it. */
@Composable
private fun ButtonProgress(label: String?, color: Color) {
    Row(
        modifier = Modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(WaiveraSizes.IconSmall),
            color = color,
            strokeWidth = WaiveraSizes.ProgressStroke,
        )
        if (label != null) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
        }
    }
}

private fun buttonBorder(variant: WaiveraButtonVariant, enabled: Boolean, colors: WaiveraColors): BorderStroke? = when {
    variant == WaiveraButtonVariant.Text -> null
    !enabled -> BorderStroke(WaiveraSpacing.Hairline, colors.border)
    variant == WaiveraButtonVariant.Secondary -> BorderStroke(WaiveraSpacing.Hairline, colors.borderStrong)
    else -> null
}

private class ButtonStyle(val container: Color, val content: Color, val disabledContainer: Color)

private fun WaiveraButtonVariant.styleFrom(colors: WaiveraColors): ButtonStyle = when (this) {
    WaiveraButtonVariant.Primary -> ButtonStyle(colors.accent, colors.onAccent, colors.surfaceMuted)
    WaiveraButtonVariant.Secondary -> ButtonStyle(colors.surface, colors.text, colors.surface)
    WaiveraButtonVariant.Destructive -> ButtonStyle(colors.dangerFill, colors.onDangerFill, colors.surfaceMuted)
    WaiveraButtonVariant.Text -> ButtonStyle(Color.Transparent, colors.accent, Color.Transparent)
}

@WaiveraPreviews
@Composable
private fun WaiveraButtonPreview() {
    WaiveraTheme {
        Column(
            Modifier.padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraButton(text = "Send", onClick = {}, fullWidth = true)
            WaiveraButton(text = "Send", onClick = {}, loading = true, fullWidth = true)
            WaiveraButton(text = "Sign out", onClick = {}, loading = true, loadingText = "Signing out…")
            WaiveraButton(text = "Cancel", onClick = {}, variant = WaiveraButtonVariant.Secondary)
            WaiveraButton(text = "Delete message", onClick = {}, variant = WaiveraButtonVariant.Destructive)
            WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text)
            WaiveraButton(text = "New chat", onClick = {}, leadingIcon = WaiveraIcons.Add, enabled = false)
        }
    }
}
