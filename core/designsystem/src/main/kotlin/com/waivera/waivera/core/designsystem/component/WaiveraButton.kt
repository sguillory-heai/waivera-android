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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * [Tonal] is the quiet filled style (accent-soft fill) for an action that matters but should not
 * shout, such as joining a call from a banner. [DestructiveOutline] is a destructive action that
 * a confirmation sheet already guards (Sign out), so the button itself stays light.
 *
 * [Selected] and [Unselected] are the two sides of a single-choice pair (Going / Not going): the
 * chosen one has the accent-soft fill and an accent outline, the other a subtle outline and
 * regular-weight secondary text, so the chosen one is always the heavier of the two, in dark
 * mode too (UI4-04).
 */
enum class WaiveraButtonVariant {
    Primary,
    Secondary,
    Tonal,
    Destructive,
    DestructiveOutline,
    Text,
    Selected,
    Unselected,
}

/**
 * The app's button.
 *
 * Disabled buttons keep a readable label (`textSecondary` on `surfaceMuted` with a hairline
 * border, the Material on-surface pattern) instead of fading the accent, so the person can
 * still read what the button does. A non-null [loadingText] ("Signing out…") is the loading
 * state: the button keeps its size, shows a small spinner next to the text, ignores taps and
 * announces it (never a blocking overlay). Loading has no other switch, so a busy button
 * always says what it is doing (UI2-21).
 */
@Composable
fun WaiveraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WaiveraButtonVariant = WaiveraButtonVariant.Primary,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    loadingText: String? = null,
) {
    val colors = WaiveraTheme.colors
    val style = variant.styleFrom(colors)
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animatePressScale(interactionSource)
    val loading = loadingText != null
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = WaiveraSizes.ButtonMinHeight)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .semantics { if (loadingText != null) stateDescription = loadingText },
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
            ButtonLabel(
                text = text,
                leadingIcon = leadingIcon,
                regularWeight = variant == WaiveraButtonVariant.Unselected,
                modifier = Modifier.alpha(if (loading) 0f else 1f),
            )
            if (loadingText != null) {
                ButtonProgress(label = loadingText, color = if (enabled) style.content else colors.textSecondary)
            }
        }
    }
}

@Composable
private fun ButtonLabel(
    text: String,
    @DrawableRes leadingIcon: Int?,
    regularWeight: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(WaiveraSizes.Icon))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (regularWeight) FontWeight.Normal else null,
            textAlign = TextAlign.Center,
        )
    }
}

/** Spinner and the progress label. The button's semantics announce it. */
@Composable
private fun ButtonProgress(label: String, color: Color) {
    Row(
        modifier = Modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WaiveraBusyIndicator(color = color, size = WaiveraSizes.IconSmall)
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
    }
}

private fun buttonBorder(variant: WaiveraButtonVariant, enabled: Boolean, colors: WaiveraColors): BorderStroke? = when {
    variant == WaiveraButtonVariant.Text -> null
    !enabled -> BorderStroke(WaiveraSpacing.Hairline, colors.border)
    variant == WaiveraButtonVariant.Secondary -> BorderStroke(WaiveraSpacing.Hairline, colors.borderStrong)
    variant == WaiveraButtonVariant.DestructiveOutline -> BorderStroke(WaiveraSpacing.Hairline, colors.danger)
    variant == WaiveraButtonVariant.Selected -> BorderStroke(WaiveraSizes.SelectedOutline, colors.accent)
    variant == WaiveraButtonVariant.Unselected -> BorderStroke(WaiveraSpacing.Hairline, colors.border)
    else -> null
}

private class ButtonStyle(val container: Color, val content: Color, val disabledContainer: Color)

private fun WaiveraButtonVariant.styleFrom(colors: WaiveraColors): ButtonStyle = when (this) {
    WaiveraButtonVariant.Primary -> ButtonStyle(colors.accent, colors.onAccent, colors.surfaceMuted)
    WaiveraButtonVariant.Secondary -> ButtonStyle(colors.surface, colors.text, colors.surface)
    WaiveraButtonVariant.Tonal -> ButtonStyle(colors.accentSoft, colors.onAccentSoft, colors.surfaceMuted)
    WaiveraButtonVariant.Destructive -> ButtonStyle(colors.dangerFill, colors.onDangerFill, colors.surfaceMuted)
    WaiveraButtonVariant.DestructiveOutline -> ButtonStyle(colors.surface, colors.dangerText, colors.surfaceMuted)
    WaiveraButtonVariant.Text -> ButtonStyle(Color.Transparent, colors.accent, Color.Transparent)
    WaiveraButtonVariant.Selected -> ButtonStyle(colors.accentSoft, colors.onAccentSoft, colors.surfaceMuted)
    WaiveraButtonVariant.Unselected -> ButtonStyle(colors.surface, colors.textSecondary, colors.surface)
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
            WaiveraButton(text = "Sign out", onClick = {}, loadingText = "Signing out…")
            WaiveraButton(text = "Cancel", onClick = {}, variant = WaiveraButtonVariant.Secondary)
            WaiveraButton(text = "Join on web", onClick = {}, variant = WaiveraButtonVariant.Tonal)
            WaiveraButton(text = "Delete message", onClick = {}, variant = WaiveraButtonVariant.Destructive)
            WaiveraButton(text = "Sign out", onClick = {}, variant = WaiveraButtonVariant.DestructiveOutline)
            WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                WaiveraButton(
                    text = "Not going",
                    onClick = {},
                    variant = WaiveraButtonVariant.Unselected,
                    modifier = Modifier.weight(1f),
                )
                WaiveraButton(
                    text = "Going",
                    onClick = {},
                    variant = WaiveraButtonVariant.Selected,
                    leadingIcon = WaiveraIcons.Check,
                    modifier = Modifier.weight(1f),
                )
            }
            WaiveraButton(text = "New chat", onClick = {}, leadingIcon = WaiveraIcons.Add, enabled = false)
        }
    }
}
