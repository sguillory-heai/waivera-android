package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Icon-only control with a 48dp touch target and a required content description. It shrinks on
 * touch-down like every other control, and a new [icon] (show / hide password, a bookmark that
 * fills) pops in rather than swapping flat.
 */
@Composable
fun WaiveraIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    val resolvedTint = if (tint == Color.Unspecified) WaiveraTheme.colors.text else tint
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource, depth = PressDepth.Compact)
    val iconPop = rememberPopScale(key = icon)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(WaiveraSizes.IconButton)
            .pressFeedback(press, enabled = enabled, shape = CircleShape),
        enabled = enabled,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = resolvedTint,
            disabledContentColor = resolvedTint.copy(alpha = 0.5f),
        ),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier
                .size(WaiveraSizes.Icon)
                .popScale(iconPop),
        )
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraIconButtonPreview() {
    WaiveraTheme {
        Row {
            WaiveraIconButton(icon = WaiveraIcons.Back, contentDescription = "Go back", onClick = {})
            WaiveraIconButton(icon = WaiveraIcons.Add, contentDescription = "New chat", onClick = {})
            WaiveraIconButton(icon = WaiveraIcons.Search, contentDescription = "Search", onClick = {}, enabled = false)
        }
    }
}
