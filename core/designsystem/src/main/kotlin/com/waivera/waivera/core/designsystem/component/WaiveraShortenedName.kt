package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.waivera.waivera.core.designsystem.theme.WaiveraNameFit

/**
 * A one-line title composed around a name, such as a group chat's "Maria-Fernanda R. & 4 others"
 * (v7 follow-up, item 4). It follows [WaiveraOneLineName]'s rule: full size when it fits, then
 * the size steps down to [WaiveraNameFit.MIN_FONT_SCALE]; past that floor only [shortenable] (the
 * name) ends in "…", so the count around it ("& 4 others", "+4") always shows whole and the title
 * still never wraps. When not even "…" and the count fit, the name and [compactSuffix] (a short
 * form of the count, "+4") are tried the same way before the whole title ends in "…". TalkBack and
 * text lookups read the whole title; tests get the drawn layout.
 *
 * The fit is measured as [WaiveraAddressText] measures its breaks: once per text, style and width.
 * [style] carries its ink and weight already, and its size is in sp.
 */
@Composable
internal fun WaiveraShortenedName(
    text: String,
    shortenable: IntRange,
    style: TextStyle,
    modifier: Modifier = Modifier,
    compactSuffix: String? = null,
) {
    val measurer = rememberTextMeasurer()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
            getTextLayoutResult { results ->
                val current = layout
                if (current != null) results.add(current)
                current != null
            }
        },
    ) {
        val maxWidth = constraints.maxWidth
        val fit = remember(text, shortenable, compactSuffix, style, maxWidth, measurer) {
            fitShortenedName(measurer, text, shortenable, style, maxWidth, compactSuffix)
        }
        BasicText(
            text = fit.shown,
            style = style.copy(fontSize = fit.fontSize),
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = 1,
            onTextLayout = { layout = it },
        )
    }
}

/** What a shortened title shows and the size it is drawn at. */
@Immutable
internal data class ShortenedName(val shown: String, val fontSize: TextUnit)

/**
 * The largest size, from the full size down the auto-size steps to the floor, at which the whole
 * [text] fits [maxWidth] on one line; else, at the floor, the longest start of the [shortenable]
 * part, ended in "…", that keeps everything around it on the line. When not even "…" fits, the
 * name (whole, or its longest start ended in "…") and [compactSuffix] in place of the rest; and
 * when that does not fit either, the whole title at the floor size, for the text's own ellipsis.
 */
@Suppress("LongParameterList")
internal fun fitShortenedName(
    measurer: TextMeasurer,
    text: String,
    shortenable: IntRange,
    style: TextStyle,
    maxWidth: Int,
    compactSuffix: String? = null,
): ShortenedName {
    val full = style.fontSize
    val floor = full * WaiveraNameFit.MIN_FONT_SCALE
    fun fits(candidate: String, size: TextUnit): Boolean = maxWidth == Constraints.Infinity ||
        measurer.measure(
            text = candidate,
            style = style.copy(fontSize = size),
            softWrap = false,
            maxLines = 1,
        ).size.width <= maxWidth
    val whole = nameSizes(full, floor).firstOrNull { fits(text, it) }
    if (whole != null) return ShortenedName(text, whole)
    return shortenAtFloor(text, shortenable, floor, ::fits)
        ?: compactSuffix?.let { suffix -> compactAtFloor(text, shortenable, suffix, floor, ::fits) }
        ?: ShortenedName(text, floor)
}

/**
 * At the [floor] size, the longest start of the [shortenable] part, ended in "…", that [fits]
 * with the rest of [text]; null when not even "…" does.
 */
private fun shortenAtFloor(
    text: String,
    shortenable: IntRange,
    floor: TextUnit,
    fits: (String, TextUnit) -> Boolean,
): ShortenedName? {
    val before = text.substring(0, shortenable.first)
    val name = text.substring(shortenable.first, shortenable.last + 1)
    val after = text.substring(shortenable.last + 1)
    fun shortened(keep: Int): String {
        // Never split a surrogate pair, so an emoji in a name is kept whole or dropped.
        val end = if (keep > 0 && name[keep - 1].isHighSurrogate()) keep - 1 else keep
        return before + name.substring(0, end).trimEnd() + ELLIPSIS + after
    }
    if (!fits(shortened(0), floor)) return null
    return ShortenedName(shortened(longestFit(name.length - 1) { fits(shortened(it), floor) }), floor)
}

/**
 * At the [floor] size, the text before the [shortenable] part, the name and [suffix] in place of
 * everything after it ("M. Rui +4"), the name ending in "…" when it must ("Mar… +4"); null when
 * not even "… +4" [fits].
 */
private fun compactAtFloor(
    text: String,
    shortenable: IntRange,
    suffix: String,
    floor: TextUnit,
    fits: (String, TextUnit) -> Boolean,
): ShortenedName? {
    val before = text.substring(0, shortenable.first)
    val name = text.substring(shortenable.first, shortenable.last + 1)
    val whole = before + name + COMPACT_GAP + suffix
    fun compact(keep: Int): String {
        val end = if (keep > 0 && name[keep - 1].isHighSurrogate()) keep - 1 else keep
        return before + name.substring(0, end).trimEnd() + ELLIPSIS + COMPACT_GAP + suffix
    }
    return when {
        fits(whole, floor) -> ShortenedName(whole, floor)
        !fits(compact(0), floor) -> null
        else -> ShortenedName(compact(longestFit(name.length - 1) { fits(compact(it), floor) }), floor)
    }
}

/** The largest count in 0..[most] that [fits]; [fits] holds for 0 and widths grow with the count. */
private inline fun longestFit(most: Int, fits: (Int) -> Boolean): Int {
    var low = 0
    var high = most
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(mid)) low = mid else high = mid - 1
    }
    return low
}

/**
 * The sizes a name may be drawn at, largest first: the full size, then the floor and each step up
 * from it that stays under the full size, as [WaiveraOneLineName]'s auto-size chooses them.
 */
private fun nameSizes(full: TextUnit, floor: TextUnit): List<TextUnit> {
    val step = WaiveraNameFit.Step.value
    val steps = generateSequence(floor.value) { it + step }.takeWhile { it < full.value - SIZE_SLACK }.toList()
    return listOf(full) + steps.asReversed().map { it.sp }
}

private const val ELLIPSIS = "…"

/** A no-break space, so "+4" never parts from the name before it. */
private const val COMPACT_GAP = "\u00A0"
private const val SIZE_SLACK = 0.001f
