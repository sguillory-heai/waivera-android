package com.waivera.waivera.feature.chat.list

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlin.math.abs
import kotlinx.coroutines.launch

/** One swipe action. Every swipe action is also a button in the row's action sheet and a TalkBack action. */
@Immutable
data class SwipeAction(
    val label: String,
    @DrawableRes val icon: Int,
    val container: Color,
    val content: Color,
    val onTrigger: () -> Unit,
)

/**
 * Horizontal swipe with one action per side. Dragging past the threshold ticks a haptic once;
 * releasing past it runs the action and the row springs back with the layout motion token
 * (a snap when animations are off). [start] is revealed by dragging toward the end edge.
 */
@Composable
fun SwipeActionsBox(
    start: SwipeAction?,
    end: SwipeAction?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = rememberHaptics()
    val motion = WaiveraTheme.motion
    val scope = rememberCoroutineScope()
    val threshold = with(density) { SwipeThreshold.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val currentStart by rememberUpdatedState(start)
    val currentEnd by rememberUpdatedState(end)

    val dragState = rememberDraggableState { delta ->
        val max = if (currentStart != null) threshold * MAX_DRAG_FACTOR else 0f
        val min = if (currentEnd != null) -threshold * MAX_DRAG_FACTOR else 0f
        offset = (offset + delta).coerceIn(min, max)
        val beyond = abs(offset) >= threshold
        if (beyond && !armed) haptics.swipeThreshold()
        armed = beyond
    }

    Box(modifier = modifier.fillMaxWidth()) {
        val revealed = when {
            offset > 0f -> currentStart
            offset < 0f -> currentEnd
            else -> null
        }
        if (revealed != null) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(revealed.container)
                    .padding(horizontal = WaiveraSpacing.Large)
                    // The same action is announced by the row's custom actions instead.
                    .clearAndSetSemantics { },
                horizontalArrangement = if (offset > 0f) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(revealed.icon),
                    contentDescription = null,
                    tint = revealed.content,
                    modifier = Modifier.size(WaiveraSizes.Icon),
                )
                Text(
                    text = revealed.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = revealed.content,
                    modifier = Modifier.padding(horizontal = WaiveraSpacing.Small),
                )
            }
        }
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = if (rtl) -offset else offset }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = start != null || end != null,
                    reverseDirection = rtl,
                    onDragStopped = {
                        val triggered = when {
                            offset >= threshold -> currentStart
                            offset <= -threshold -> currentEnd
                            else -> null
                        }
                        armed = false
                        triggered?.onTrigger?.invoke()
                        scope.launch {
                            animate(
                                initialValue = offset,
                                targetValue = 0f,
                                animationSpec = motion.layout(),
                            ) { value, _ ->
                                offset = value
                            }
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/** Twice the touch-target unit, so a deliberate swipe is needed. */
private val SwipeThreshold = WaiveraSpacing.XxLarge * 2
private const val MAX_DRAG_FACTOR = 1.4f
