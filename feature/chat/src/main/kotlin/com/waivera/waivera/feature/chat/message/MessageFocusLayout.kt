package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/** How the focus overlay arranges the lifted bubble and its menu. */
internal enum class FocusArrangement {
    /** Reactions above the bubble, the menu under it: a phone held upright. */
    Stacked,

    /**
     * Reactions above the bubble, the menu in a column beside them: a short window, such as a
     * phone in landscape, where the menu does not fit under even a two-line bubble.
     */
    Beside,
}

/**
 * Everything the focus overlay's placement depends on, in the overlay's own pixels. [safe] is the
 * window less the system bars, the cutout and the keyboard, and less a margin; nothing is placed
 * outside it. [anchor] is where the pressed bubble sits, or null when it is not known (a preview).
 * [pill] is null when the person cannot react.
 */
@Immutable
internal data class FocusGeometry(
    val safe: IntRect,
    val anchor: IntRect?,
    val mine: Boolean,
    val pill: IntSize?,
    val menu: IntSize,
    val gap: Int,
    val minBubbleHeight: Int,
    val maxBubbleHeight: Int,
    val minBubbleWidth: Int,
    val fallbackBubbleWidth: Int,
)

/** The arrangement and the most room the lifted bubble may take in it. */
@Immutable
internal data class FocusFrame(val arrangement: FocusArrangement, val bubbleMaxWidth: Int, val bubbleMaxHeight: Int)

/** Where each piece's top-left corner goes. */
@Immutable
internal data class FocusPlacement(
    val arrangement: FocusArrangement,
    val bubble: IntOffset,
    val pill: IntOffset?,
    val menu: IntOffset,
)

/**
 * The width the action menu is measured at: its token width, never wider than the safe area. When
 * the window is wide enough for the menu to sit beside the reaction pill (landscape), it is held to
 * that room, so the [FocusArrangement.Beside] column never runs under the pill.
 */
internal fun focusMenuWidth(safeWidth: Int, pillWidth: Int, gap: Int, target: Int, minBeside: Int): Int {
    val besideRoom = safeWidth - pillWidth - gap
    return if (besideRoom >= minBeside) min(target, besideRoom) else min(target, safeWidth)
}

/**
 * Picks the arrangement. The menu and the reactions always keep their full size: when room is
 * short it is the bubble that gives way, clamped to less of its height (wave 28 B1: the old sheet
 * cut Delete off instead). Under it the bubble keeps at least [FocusGeometry.minBubbleHeight];
 * when even that does not fit, the menu moves beside the bubble.
 */
internal fun FocusGeometry.frame(): FocusFrame {
    val pillBlock = pill?.let { it.height + gap } ?: 0
    val width = anchor?.width ?: fallbackBubbleWidth
    val cap = max(maxBubbleHeight, minBubbleHeight)
    val stackedRoom = safe.height - pillBlock - gap - menu.height
    val column = safe.width - menu.width - gap
    val besideFits = column >= max(minBubbleWidth, pill?.width ?: 0) && menu.height <= safe.height
    return when {
        stackedRoom >= minBubbleHeight ->
            FocusFrame(FocusArrangement.Stacked, min(width, safe.width), min(stackedRoom, cap))
        besideFits -> FocusFrame(FocusArrangement.Beside, min(width, column), min(safe.height - pillBlock, cap))
        // Neither fits (a split-screen sliver): the smallest bubble, the menu under it.
        else -> FocusFrame(FocusArrangement.Stacked, min(width, safe.width), minBubbleHeight)
    }
}

/**
 * Places the pieces once the bubble is measured. The bubble starts where it was pressed and moves
 * only as far as it must for the reactions and the menu to fit inside the safe area: up when it
 * sat near the composer, down when its top had scrolled under the top bar. The reactions and the
 * menu line up with the bubble's own side, the end for mine and the start for everyone else's.
 */
internal fun FocusGeometry.place(frame: FocusFrame, bubble: IntSize): FocusPlacement {
    val pillBlock = pill?.let { it.height + gap } ?: 0
    return when (frame.arrangement) {
        FocusArrangement.Stacked -> {
            val left = bubbleLeft(safe.left, safe.right, bubble.width)
            val right = left + bubble.width
            val desiredTop = anchor?.top ?: (safe.bottom - menu.height - gap - bubble.height)
            val groupTop = clampTop(desiredTop - pillBlock, pillBlock + bubble.height + gap + menu.height)
            val bubbleTop = groupTop + pillBlock
            FocusPlacement(
                arrangement = FocusArrangement.Stacked,
                bubble = IntOffset(left, bubbleTop),
                pill = pill?.let { IntOffset(sideAligned(it.width, left, right, safe.left, safe.right), groupTop) },
                menu = IntOffset(
                    sideAligned(menu.width, left, right, safe.left, safe.right),
                    bubbleTop + bubble.height + gap,
                ),
            )
        }
        FocusArrangement.Beside -> {
            val columnWidth = safe.width - menu.width - gap
            val columnLeft = if (mine) safe.right - columnWidth else safe.left
            val columnRight = columnLeft + columnWidth
            val menuLeft = if (mine) safe.left else columnRight + gap
            val left = bubbleLeft(columnLeft, columnRight, bubble.width)
            val right = left + bubble.width
            val desiredTop = anchor?.top ?: (safe.bottom - bubble.height)
            val groupTop = clampTop(desiredTop - pillBlock, pillBlock + bubble.height)
            val bubbleTop = groupTop + pillBlock
            FocusPlacement(
                arrangement = FocusArrangement.Beside,
                bubble = IntOffset(left, bubbleTop),
                pill = pill?.let { IntOffset(sideAligned(it.width, left, right, columnLeft, columnRight), groupTop) },
                menu = IntOffset(menuLeft, clampTop(bubbleTop, menu.height)),
            )
        }
    }
}

/** [top] moved just enough for [height] to sit inside the safe area; the top wins if neither fits. */
private fun FocusGeometry.clampTop(top: Int, height: Int): Int =
    top.coerceAtMost(safe.bottom - height).coerceAtLeast(safe.top)

/** The bubble where it was pressed, kept inside [columnLeft]..[columnRight]. */
private fun FocusGeometry.bubbleLeft(columnLeft: Int, columnRight: Int, width: Int): Int {
    val desired = anchor?.left ?: if (mine) columnRight - width else columnLeft
    return desired.coerceAtMost(columnRight - width).coerceAtLeast(columnLeft)
}

/** A piece of [width] lined up with the bubble's own side, kept inside [minLeft]..[maxRight]. */
private fun FocusGeometry.sideAligned(width: Int, left: Int, right: Int, minLeft: Int, maxRight: Int): Int {
    val desired = if (mine) right - width else left
    return desired.coerceAtMost(maxRight - width).coerceAtLeast(minLeft)
}
