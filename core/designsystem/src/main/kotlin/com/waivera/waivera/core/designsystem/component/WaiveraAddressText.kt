package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.addressBreaksFor
import com.waivera.waivera.core.designsystem.text.addressParts
import com.waivera.waivera.core.designsystem.text.asAddressDisplay
import com.waivera.waivera.core.designsystem.theme.TitleLineBreak
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * An email address or handle, shown whole wherever it appears (UI6-02, UI6-03). It stays on one
 * line when it fits. When it doesn't, it wraps after the "@", then, only if the domain alone is
 * wider than the line, after the domain's dots, and only if the local part alone is wider than
 * the line, after the local part's own separators ([asAddressDisplay]). It is never ellipsized:
 * the reader needs every character, to recognise the address or to copy it by eye.
 *
 * The greedy breaker fills each line as far as the allowed break points let it. TalkBack and
 * text lookups see the plain address, without the invisible break characters.
 */
@Composable
fun WaiveraAddressText(
    address: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
) {
    val lineStyle = style.copy(textAlign = textAlign, lineBreak = TitleLineBreak)
    val measurer = rememberTextMeasurer()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // The node reads as the plain address, and still hands tests the laid-out text.
    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            text = AnnotatedString(address)
            getTextLayoutResult { results ->
                val current = layout
                if (current != null) results.add(current)
                current != null
            }
        },
    ) {
        val maxWidth = constraints.maxWidth
        val shown = remember(address, lineStyle, maxWidth, measurer) {
            val (local, domain) = address.addressParts()
            fun width(part: String) = if (part.isEmpty()) {
                0
            } else {
                measurer.measure(text = part, style = lineStyle, softWrap = false, maxLines = 1).size.width
            }
            address.asAddressDisplay(
                breaks = addressBreaksFor(localWidth = width(local), domainWidth = width(domain), maxWidth = maxWidth),
            )
        }
        Text(
            text = shown,
            style = lineStyle,
            color = color,
            softWrap = true,
            overflow = TextOverflow.Clip,
            onTextLayout = { layout = it },
        )
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraAddressTextPreview() {
    WaiveraTheme {
        WaiveraAddressText(address = "alexandria.rivera-montgomery@example.com")
    }
}
