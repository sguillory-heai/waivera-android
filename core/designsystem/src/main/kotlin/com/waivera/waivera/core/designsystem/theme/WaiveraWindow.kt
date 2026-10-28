package com.waivera.waivera.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window height under which a hero compacts so the sheet's first rows still show above the fold:
 * a 640dp phone is short, a 780dp one is not.
 */
val ShortWindowHeight: Dp = 700.dp

/** True when the window is shorter than [ShortWindowHeight]; false until the window is measured. */
@Composable
fun isShortWindow(): Boolean {
    val height = LocalWindowInfo.current.containerSize.height
    if (height <= 0) return false
    return with(LocalDensity.current) { height.toDp() } < ShortWindowHeight
}
