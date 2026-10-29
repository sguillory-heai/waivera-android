package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * Built on foundation's [BasicText] auto-size ([TextAutoSize.StepBased]). A style without an sp
 * size cannot be stepped, so it shows at its size and only ellipsizes.
 */
@Composable
fun WaiveraOneLineName(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val merged = style.copy(
        color = ink,
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign ?: style.textAlign,
    )
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

/** The auto-size for a name set at [size], or null when [size] is not in sp. */
internal fun nameAutoSize(size: TextUnit): TextAutoSize? = if (size.isSp) {
    TextAutoSize.StepBased(
        minFontSize = size * WaiveraNameFit.MIN_FONT_SCALE,
        maxFontSize = size,
        stepSize = WaiveraNameFit.Step,
    )
} else {
    null
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
        }
    }
}
