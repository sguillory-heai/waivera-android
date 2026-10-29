package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics

/**
 * The single press primitive: ripple plus a spring to the press scale from the moment the finger
 * lands ([pressFeedback]), which collapses to no scale when animations are off. A long press
 * fires the long-press haptic; a plain tap fires none (the feedback map keeps taps silent).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaiveraPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    role: Role? = Role.Button,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource)
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .pressFeedback(press, enabled = enabled)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptics.longPress()
                        longClick()
                    }
                },
                hapticFeedbackEnabled = false,
                onClick = onClick,
            ),
        content = content,
    )
}
