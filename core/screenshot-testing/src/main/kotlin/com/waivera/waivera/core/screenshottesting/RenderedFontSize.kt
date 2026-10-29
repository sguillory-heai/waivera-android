package com.waivera.waivera.core.screenshottesting

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The font size an auto-sized text was drawn at. Compose keeps the declared style in
 * [TextLayoutResult.layoutInput] and does not say which size `autoSize` chose, so this lays the
 * same text out at the style's size and at each step up from [floor] (where foundation's
 * StepBased steps from), and returns the one whose line matches this layout exactly; null when
 * none does.
 */
fun TextLayoutResult.renderedFontSize(floor: TextUnit, step: TextUnit): TextUnit? {
    val input = layoutInput
    val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0)
    val max = input.style.fontSize.value
    val steps = generateSequence(floor.value) { it + step.value }.takeWhile { it <= max + FLOAT_SLACK }
    return (sequenceOf(max) + steps.toList().asReversed()).firstOrNull { size ->
        val candidate = measurer.measure(
            text = input.text,
            style = input.style.copy(fontSize = size.sp),
            overflow = input.overflow,
            softWrap = input.softWrap,
            maxLines = input.maxLines,
            constraints = input.constraints,
            layoutDirection = input.layoutDirection,
            density = input.density,
            fontFamilyResolver = input.fontFamilyResolver,
        )
        sameLine(candidate)
    }?.sp
}

private fun TextLayoutResult.sameLine(other: TextLayoutResult): Boolean = size == other.size &&
    lineCount == other.lineCount &&
    getLineEnd(0, visibleEnd = true) == other.getLineEnd(0, visibleEnd = true) &&
    getLineRight(0) == other.getLineRight(0)

private const val FLOAT_SLACK = 0.001f
