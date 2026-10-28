package com.waivera.waivera.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Elevation levels. The app is mostly flat; borders separate surfaces before shadows do. */
object WaiveraElevation {
    val Level0: Dp = 0.dp
    val Level1: Dp = 1.dp
    val Level2: Dp = 3.dp
    val Level3: Dp = 6.dp

    /** A content card on a tab screen's sheet: a soft lift, as in the concept. */
    val Card: Dp = 2.dp

    /** The primary button's violet glow and the raised secondary's lift (v5 decision 2, option C). */
    val ButtonGlow: Dp = 6.dp
    val ButtonRaise: Dp = 2.dp

    /** Sheets and floating banners. */
    val Overlay: Dp = 8.dp
}
