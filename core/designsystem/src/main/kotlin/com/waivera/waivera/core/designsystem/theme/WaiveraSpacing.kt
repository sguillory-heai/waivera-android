package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale in steps of 4dp. Layouts use these instead of literal dp values. */
object WaiveraSpacing {
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 32.dp

    /** Minimum touch target on Android. */
    val MinTouchTarget: Dp = 48.dp
}
