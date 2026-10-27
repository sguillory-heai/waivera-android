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
 * The app's button. Disabled is 50% opacity; loading keeps the size, swaps the label for an
 * inline spinner, ignores taps and announces "Loading" (never a blocking overlay).
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
) {
    val colors = WaiveraTheme.colors
    val (container, content) = variant.colorsFrom(colors)
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animatePressScale(interactionSource)
    val loadingLabel = stringResource(R.string.core_designsystem_loading)
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = WaiveraSizes.ButtonMinHeight)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .semantics { if (loading) stateDescription = loadingLabel },
        enabled = enabled,
        shape = WaiveraShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * DISABLED_ALPHA),
            disabledContentColor = content.copy(alpha = DISABLED_ALPHA),
        ),
        border = if (variant == WaiveraButtonVariant.Secondary) {
            BorderStroke(WaiveraSpacing.Hairline, colors.borderStrong)
        } else {
            null
        },
        contentPadding = PaddingValues(horizontal = WaiveraSpacing.MediumLarge, vertical = WaiveraSpacing.MediumSmall),
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.alpha(if (loading) 0f else 1f),
                horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        painterResource(leadingIcon),
                        contentDescription = null,
                        modifier = Modifier.size(WaiveraSizes.Icon),
                    )
                }
                Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(WaiveraSizes.ProgressSmall),
                    color = content,
                    strokeWidth = WaiveraSizes.ProgressStroke,
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.5f

private fun WaiveraButtonVariant.colorsFrom(colors: WaiveraColors): Pair<Color, Color> = when (this) {
    WaiveraButtonVariant.Primary -> colors.accent to colors.onAccent
    WaiveraButtonVariant.Secondary -> colors.surface to colors.text
    WaiveraButtonVariant.Destructive -> colors.dangerFill to colors.onDangerFill
    WaiveraButtonVariant.Text -> Color.Transparent to colors.accent
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
            WaiveraButton(text = "Cancel", onClick = {}, variant = WaiveraButtonVariant.Secondary)
            WaiveraButton(text = "Delete message", onClick = {}, variant = WaiveraButtonVariant.Destructive)
            WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text)
            WaiveraButton(text = "New chat", onClick = {}, leadingIcon = WaiveraIcons.Add, enabled = false)
        }
    }
}
