package com.waivera.waivera.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import kotlin.math.roundToInt

/**
 * Where a row's leading tile or avatar sits: centred while the row is about the tile's own
 * height, and pinned [WaiveraSpacing.LeadingTileTop] below the top once long content (a long
 * title, an organizer's name) makes the row taller, so the tile stays level with the title's
 * first line (v7, item 1). It is the smaller of the two offsets, so a single-line row lays out
 * exactly as with [Alignment.CenterVertically] and only tall rows change.
 *
 * Use it on the leading slot inside a `Row`: `Modifier.align(rememberLeadingTileAlignment())`.
 */
@Composable
fun rememberLeadingTileAlignment(): Alignment.Vertical {
    val topPx = with(LocalDensity.current) { WaiveraSpacing.LeadingTileTop.roundToPx() }
    return remember(topPx) { LeadingTileAlignment(topPx) }
}

@Immutable
internal data class LeadingTileAlignment(private val topPx: Int) : Alignment.Vertical {
    // The centre is rounded as Alignment.CenterVertically rounds it, so short rows match it to the pixel.
    override fun align(size: Int, space: Int): Int = minOf(((space - size) / 2f).roundToInt(), topPx)
}
