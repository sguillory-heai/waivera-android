package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraNameFit
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A person's or a chat's name as a title or label in chat: always one line (v7, item 3). A name
 * that does not fit first shrinks, at most to [WaiveraNameFit.MIN_FONT_SCALE] of [style]'s size,
 * and past that ends in "…"; it never wraps to a second line. A name that fits keeps the full
 * size. TalkBack reads the whole name, ellipsized or not.
 *
 * Built on foundation's [BasicText] auto-size ([TextAutoSize.StepBased], after a full-size try). A style without an sp
 * size cannot be stepped, so it shows at its size and only ellipsizes.
 *
 * [shortenable] is the only part of [text] that may be cut, for a title composed around a name
 * ("Maria-Fernanda R. & 4 others"): past the floor size, that part ends in "…" and the rest (the
 * "& 4 others" count) always shows whole ([WaiveraShortenedName], v7 follow-up item 4). When even
 * "…" and the count do not fit, [compactSuffix] (a group's "+4") takes the count's place.
 */
@Composable
fun WaiveraOneLineName(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    shortenable: IntRange? = null,
    compactSuffix: String? = null,
) {
    val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val merged = style.copy(
        color = ink,
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign ?: style.textAlign,
    )
    if (shortenable != null && style.fontSize.isSp && shortenable.isWithin(text)) {
        WaiveraShortenedName(
            text = text,
            shortenable = shortenable,
            compactSuffix = compactSuffix,
            style = merged,
            modifier = modifier,
        )
        return
    }
    val autoSize = remember(style.fontSize) { nameAutoSize(style.fontSize) }
    BasicText(
        text = text,
        modifier = modifier,
        style = merged,
        overflow = TextOverflow.Ellipsis,
        softWrap = true,
        maxLines = 1,
        autoSize = autoSize,
    )
}

private fun IntRange.isWithin(text: String): Boolean = !isEmpty() && first >= 0 && last < text.length

/** The auto-size for a name set at [size], or null when [size] is not in sp. */
internal fun nameAutoSize(size: TextUnit): TextAutoSize? = if (size.isSp) NameAutoSize(size) else null

/**
 * Full size whenever the name fits, then [TextAutoSize.StepBased] down to the floor. StepBased
 * alone steps up from the floor in pixels, and under Android's non-linear font scaling (200%)
 * the full size falls between two of its steps, so even a short name was drawn a step smaller.
 */
private class NameAutoSize(private val size: TextUnit) : TextAutoSize {
    private val shrink = TextAutoSize.StepBased(
        minFontSize = size * WaiveraNameFit.MIN_FONT_SCALE,
        maxFontSize = size,
        stepSize = WaiveraNameFit.Step,
    )

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val full = performLayout(constraints, text, size)
        if (full.lineCount == 1 && !full.isLineEllipsized(0)) return size
        val scope = this
        return with(shrink) { scope.getFontSize(constraints, text) }
    }

    override fun equals(other: Any?): Boolean = other is NameAutoSize && other.size == size

    override fun hashCode(): Int = size.hashCode()
}

@WaiveraPreviews
@Composable
private fun WaiveraOneLineNamePreview() {
    WaiveraTheme {
        Column {
            WaiveraOneLineName(text = "Priya Raman", style = MaterialTheme.typography.titleSmall)
            WaiveraOneLineName(
                text = "Maria-Fernanda Alejandra Castellanos-Whitaker de la Vega Rui",
                style = MaterialTheme.typography.titleSmall,
            )
            WaiveraOneLineName(
                text = "Wolfeschlegelsteinhausenbergerdorffvoral",
                style = MaterialTheme.typography.titleSmall,
            )
            WaiveraOneLineName(
                text = "Maria-Fernanda Alejandra Castellanos & 4 others",
                style = MaterialTheme.typography.titleSmall,
                shortenable = 0 until "Maria-Fernanda Alejandra Castellanos".length,
            )
        }
    }
}
