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

/**
 * The single press primitive: ripple plus a spring to the press scale from the moment the finger
 * lands ([pressFeedback]), which collapses to no scale when animations are off. Neither a tap
 * nor a long press vibrates (haptics policy: the press and the menu it opens are the feedback),
 * so the platform's own long-press buzz is switched off too.
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
                onLongClick = onLongClick,
                hapticFeedbackEnabled = false,
                onClick = onClick,
            ),
        content = content,
    )
}
