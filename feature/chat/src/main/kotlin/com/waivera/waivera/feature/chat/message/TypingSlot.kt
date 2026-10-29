package com.waivera.waivera.feature.chat.message

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.CelebrationCopy
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R

/**
 * Who is typing, as the slot shows it: first names only ("Priya is typing…", "Priya and Jordan
 * are typing…", "Priya and 2 others are typing…"), and where the names end in [shown], so the
 * names are what gets cut, never "is typing…". [spoken] has the full names, for TalkBack.
 */
@Immutable
data class TypingLine(val shown: String, val namesEnd: Int, val spoken: String) {
    companion object {
        /** The line for [names] (display names, in the order they started typing); null for nobody. */
        fun of(names: List<String>, resources: Resources): TypingLine? {
            if (names.isEmpty()) return null
            val short = names.map { CelebrationCopy.firstName(it) ?: it }
            if (names.size == 1) {
                return line(resources, R.string.feature_chat_typing_one, short[0], names[0])
            }
            fun who(list: List<String>): String = if (list.size == 2) {
                resources.getString(R.string.feature_chat_typing_two_names, list[0], list[1])
            } else {
                resources.getQuantityString(R.plurals.feature_chat_typing_others, list.size - 1, list[0], list.size - 1)
            }
            return line(resources, R.string.feature_chat_typing_several, who(short), who(names))
        }

        /** A translation that drops the names leaves the whole line to ellipsize. */
        private fun line(
            resources: Resources,
            @StringRes id: Int,
            shownNames: String,
            spokenNames: String,
        ): TypingLine {
            val shown = resources.getString(id, shownNames)
            val at = shown.lastIndexOf(shownNames)
            return TypingLine(
                shown = shown,
                namesEnd = if (at < 0) shown.length else at + shownNames.length,
                spoken = resources.getString(id, spokenNames),
            )
        }
    }
}

/** [TypingLine.of] in composition. */
@Composable
fun typingLine(names: List<String>): TypingLine? {
    val resources = LocalResources.current
    return remember(names, resources) { TypingLine.of(names, resources) }
}

/**
 * Always reserves one line's height, at any font scale, so the composer never jumps when someone
 * starts or stops typing (03 §11, wave 29 N1). The line never wraps: when it is too long for one
 * line the names are ellipsized and "is typing…" stays whole beside them.
 */
@Composable
fun TypingSlot(line: TypingLine?, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelMedium
    // The drawn line, not style.lineHeight: at large font the glyphs need more than the
    // style's line height, and a slot sized from it clips their descenders.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lineHeight = remember(style, density) {
        with(density) { measurer.measure(TYPING_MEASURE_SAMPLE, style, maxLines = 1).size.height.toDp() }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(maxOf(ChatDimens.TypingSlot, lineHeight))
            .padding(horizontal = WaiveraSpacing.Gutter)
            .testTag(TYPING_SLOT_TAG)
            // One live region that says the full names: a description set on the merged node is
            // what TalkBack reads, in place of the shortened names drawn inside it.
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                if (line != null) contentDescription = line.spoken
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (line != null) TypingText(line = line, style = style, color = WaiveraTheme.colors.textSecondary)
    }
}

/**
 * The line in one piece while it fits, so it draws exactly as before; once it would be cut, the
 * names and "is typing…" as two pieces, the names taking what "is typing…" leaves.
 */
@Composable
private fun TypingText(line: TypingLine, style: TextStyle, color: Color) {
    var split by remember(line) { mutableStateOf(false) }
    if (!split) {
        Text(
            text = line.shown,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(TYPING_NAMES_TAG),
            // Not hasVisualOverflow, which reports width overflow for text that fits (wave 29).
            onTextLayout = { layout -> if (layout.didOverflowHeight || layout.isLineEllipsized(0)) split = true },
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = line.shown.substring(0, line.namesEnd),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(TYPING_NAMES_TAG),
        )
        Text(
            text = line.shown.substring(line.namesEnd),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(TYPING_TAIL_TAG),
        )
    }
}

/** Test tag for the typing slot. */
internal const val TYPING_SLOT_TAG = "chat_typing_slot"

/** Test tag for the typing line's names (the whole line while it fits). */
internal const val TYPING_NAMES_TAG = "chat_typing_names"

/** Test tag for "is typing…" once the names are cut. */
internal const val TYPING_TAIL_TAG = "chat_typing_tail"

/** Tall and deep glyphs, so the slot is sized for any line it shows. */
private const val TYPING_MEASURE_SAMPLE = "Ág"

@WaiveraPreviews
@Composable
private fun TypingSlotPreview() {
    WaiveraTheme {
        TypingSlot(
            line = TypingLine(
                shown = "Wolfeschlegelsteinhausenbergerdorffvoral and 2 others are typing…",
                namesEnd = 53,
                spoken = "Wolfeschlegelsteinhausenbergerdorffvoral and 2 others are typing…",
            ),
        )
    }
}
