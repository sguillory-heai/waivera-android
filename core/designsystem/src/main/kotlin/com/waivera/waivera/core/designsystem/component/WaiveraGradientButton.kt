package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/** How big a [WaiveraGradientButton] is: the screen's one CTA, or an inline action on a card. */
enum class WaiveraGradientButtonSize { Large, Compact }

/**
 * The primary action of concept A: white text on the saturated violet-to-magenta gradient
 * (`#6A3DC4→#B4307D` light, `#7A3BE0→#B8338A` dark) with a soft coloured glow under it.
 * [WaiveraGradientButtonSize.Large] is 56dp and full width, the moment the screen exists for
 * (Sign in); [WaiveraGradientButtonSize.Compact] is 48dp and wraps its label, for an inline
 * action such as Accept on an invitation card.
 *
 * It behaves like a primary [WaiveraButton]: a disabled button keeps a readable label on the
 * muted fill with a hairline border, and a non-null [loadingText] keeps the size, shows a small
 * spinner beside the text, ignores taps and is announced as the button's state.
 */
@Composable
fun WaiveraGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loadingText: String? = null,
    size: WaiveraGradientButtonSize = WaiveraGradientButtonSize.Large,
) {
    val colors = WaiveraTheme.colors
    val hero = WaiveraHero.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animatePressScale(interactionSource)
    val loading = loadingText != null
    val large = size == WaiveraGradientButtonSize.Large
    val shape = if (large) WaiveraShapes.Cta else WaiveraShapes.Button
    val fill = if (enabled) {
        Modifier
            .shadow(
                elevation = if (large) WaiveraElevation.CtaGlow else WaiveraElevation.Level2,
                shape = shape,
                ambientColor = hero.buttonGlow,
                spotColor = hero.buttonGlow,
            )
            .background(brush = hero.buttonBrush, shape = shape)
    } else {
        Modifier
            .background(color = colors.surfaceMuted, shape = shape)
            .border(border = BorderStroke(WaiveraSpacing.Hairline, colors.border), shape = shape)
    }
    val content = if (enabled) hero.onButton else colors.textSecondary
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (large) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (large) WaiveraSizes.CtaHeight else WaiveraSizes.ButtonMinHeight)
            .then(fill)
            .semantics { if (loadingText != null) stateDescription = loadingText },
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = content,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = content,
        ),
        contentPadding = PaddingValues(horizontal = WaiveraSpacing.MediumLarge, vertical = WaiveraSpacing.MediumSmall),
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(if (loading) 0f else 1f),
            )
            if (loadingText != null) {
                Row(
                    modifier = Modifier.clearAndSetSemantics { },
                    horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // At large font the label can wrap; a spinner beside a wrapped label sat stranded (UI5-10).
                    if (!isLargeFontScale()) WaiveraBusyIndicator(color = content, size = WaiveraSizes.IconSmall)
                    Text(
                        text = loadingText,
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraGradientButtonPreview() {
    WaiveraTheme {
        Column(
            modifier = Modifier
                .background(WaiveraHero.colors.sheet)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraGradientButton(text = "Sign in", onClick = {})
            WaiveraGradientButton(text = "Sign in", onClick = {}, loadingText = "Signing you in…")
            WaiveraGradientButton(text = "Sign in", onClick = {}, enabled = false)
            WaiveraGradientButton(text = "Accept", onClick = {}, size = WaiveraGradientButtonSize.Compact)
        }
    }
}
