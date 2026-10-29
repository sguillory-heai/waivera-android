package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A person's name, alone or inside a sentence ("Happy birthday, Maria-Fernanda!"), held to
 * [maxLines] lines of its own and ellipsized past them (wave 29). The name wraps only at a space
 * or after a hyphen ([withBreaksAfterHyphens]). When even that would cut a word in two (a single
 * word wider than the line), the name gets one line instead and ends in "…", so it never breaks
 * mid-word. The text before the name keeps every line it needs, so a long greeting at large font
 * reads as before.
 *
 * [name] is the part of [text] that is the name; when it is not found in [text] the text shows
 * as plain, unlimited text. A name that fits looks exactly as a plain [Text] would. The first
 * layout of a name that does not fit is unlimited, and the limit applies from the next frame;
 * a new width measures again. TalkBack reads the whole [text], never the ellipsized lines.
 */
@Composable
fun WaiveraNameText(
    text: String,
    modifier: Modifier = Modifier,
    name: String = text,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
) {
    val shown = remember(text, name) { NameInText.of(text, name) }
    var limit by remember(shown, maxLines) { mutableStateOf<NameLineLimit?>(null) }
    Text(
        text = shown.text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = limit?.lines ?: Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            val width = layout.layoutInput.constraints.maxWidth
            val current = limit
            limit = when {
                current == null -> layout.nameLineLimit(shown.nameStart, maxLines)?.let { NameLineLimit(width, it) }
                current.width != width -> null
                else -> current
            }
        },
    )
}

/** The text as shown, with break points after the name's hyphens, and where the name starts. */
@Immutable
internal data class NameInText(val text: String, val nameStart: Int) {
    companion object {
        fun of(text: String, name: String): NameInText {
            val start = if (name.isBlank()) -1 else text.lastIndexOf(name)
            if (start < 0) return NameInText(text, -1)
            val shownName = name.withBreaksAfterHyphens()
            return NameInText(text.substring(0, start) + shownName + text.substring(start + name.length), start)
        }
    }
}

/** The line limit set at a layout width; another width lays the text out unlimited again. */
@Immutable
private data class NameLineLimit(val width: Int, val lines: Int)

/**
 * The line limit an unlimited layout of a name needs, or null when it already fits: the lines
 * before the name, plus [nameLines] for the name, or just one for the name when its lines would
 * break a word in two. Only the lines before the last shown one can break mid-word: the last is
 * ellipsized.
 */
internal fun TextLayoutResult.nameLineLimit(nameStart: Int, nameLines: Int): Int? {
    if (nameStart < 0 || nameStart >= layoutInput.text.length) return null
    val first = getLineForOffset(nameStart)
    // Saturates, for a name with no line limit of its own (Int.MAX_VALUE).
    val cap = first + minOf(nameLines, lineCount)
    val shownLines = minOf(lineCount, cap)
    val midWord = (first until shownLines - 1).any { breaksMidWord(it) }
    return when {
        midWord -> first + 1
        lineCount > cap -> cap
        else -> null
    }
}

/**
 * Whether [line] ends inside a word: a letter or digit on both sides of the break. Scripts
 * written without spaces between words (Han, kana, Thai and the like) break between letters by
 * design, so a break there is not counted.
 */
internal fun TextLayoutResult.breaksMidWord(line: Int): Boolean {
    val text = layoutInput.text.text
    val end = getLineEnd(line)
    if (end <= 0 || end >= text.length) return false
    return text.codePointBefore(end).isInWord() && text.codePointAt(end).isInWord()
}

private fun Int.isInWord(): Boolean = Character.isLetterOrDigit(this) && Character.UnicodeScript.of(this) !in UNSPACED

private val UNSPACED = setOf(
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL,
    Character.UnicodeScript.THAI,
    Character.UnicodeScript.LAO,
    Character.UnicodeScript.KHMER,
    Character.UnicodeScript.MYANMAR,
)

@WaiveraPreviews
@Composable
private fun WaiveraNameTextPreview() {
    WaiveraTheme {
        Column {
            WaiveraNameText(text = "Priya Raman", style = MaterialTheme.typography.titleSmall)
            WaiveraNameText(
                text = "Maria-Fernanda Alejandra Castellanos-Whitaker de la Vega Rui",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            WaiveraNameText(
                text = "Wolfeschlegelsteinhausenbergerdorffvoral",
                style = MaterialTheme.typography.titleSmall,
            )
            WaiveraNameText(
                text = "Happy birthday, Maximilianus Wolfeschlegelsteinhausenberg!",
                name = "Maximilianus Wolfeschlegelsteinhausenberg",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
        }
    }
}
