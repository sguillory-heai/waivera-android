package com.waivera.waivera.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import kotlin.math.roundToInt

/**
 * Where a row's leading tile or avatar sits: centred, exactly as [Alignment.CenterVertically],
 * while the row's content is no taller than a standard two-line row (a title and a subtitle), and
 * pinned [WaiveraSpacing.LeadingTileTop] below the top once long content (a third line, a long
 * title, an organizer's name) makes it taller, so the tile stays level with the title's first line
 * on cards made tall by content (v7, item 1; follow-up: standard rows stay centred).
 *
 * The decision is made on the measured content height against [twoLineRowContentPx], never on a
 * count of lines in the strings, so it holds at every font scale and for any slot content.
 *
 * Use it on the leading slot inside a `Row`: `Modifier.align(rememberLeadingTileAlignment())`.
 */
@Composable
fun rememberLeadingTileAlignment(): Alignment.Vertical {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val topPx = with(density) { WaiveraSpacing.LeadingTileTop.roundToPx() }
    val standardPx = with(density) {
        twoLineRowContentPx(
            titleLinePx = typography.titleSmall.lineHeight.toPx(),
            gapPx = WaiveraSpacing.XxSmall.toPx(),
            subtitleLinePx = typography.bodySmall.lineHeight.toPx(),
        )
    }
    return remember(topPx, standardPx) { LeadingTileAlignment(topPx = topPx, standardPx = standardPx) }
}

/**
 * The tallest content a standard two-line row has: one title line (titleSmall), the rows' 2dp
 * gap and one subtitle line (bodySmall), each converted at the current font scale as the text
 * lays out, plus half a subtitle line so rounding, a 48dp switch or a slightly taller text style
 * (a profile field's label and value) never tips a two-line row over. A third line adds a whole
 * line, so it always crosses it.
 */
internal fun twoLineRowContentPx(titleLinePx: Float, gapPx: Float, subtitleLinePx: Float): Int =
    (titleLinePx + gapPx + subtitleLinePx * (1f + TWO_LINE_SLACK_LINES)).roundToInt()

private const val TWO_LINE_SLACK_LINES = 0.5f

@Immutable
internal data class LeadingTileAlignment(private val topPx: Int, private val standardPx: Int) : Alignment.Vertical {
    // The centre is rounded as Alignment.CenterVertically rounds it, so standard rows match it to the pixel.
    override fun align(size: Int, space: Int): Int {
        val centred = ((space - size) / 2f).roundToInt()
        return if (space > standardPx) minOf(centred, topPx) else centred
    }
}
