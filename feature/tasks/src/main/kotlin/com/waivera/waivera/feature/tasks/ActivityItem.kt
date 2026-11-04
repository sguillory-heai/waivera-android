package com.waivera.waivera.feature.tasks

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.WaiveraCardGroup
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.landingGrow
import com.waivera.waivera.core.designsystem.component.rememberLandingGrow
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraMotionScheme
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One logged activity, in the original layout: the action leads as the headline beside the
 * activity tile, then when and who, then the note. Your own entries add a "You" tag after the
 * action instead of your name. How long the work took is left out, as on the web: it feeds
 * reporting, not this list. Long notes stop at [ACTIVITY_LINES] lines and open in place.
 */
@Composable
internal fun ActivityItem(row: ActivityRow, modifier: Modifier = Modifier, startExpanded: Boolean = false) {
    val colors = WaiveraTheme.colors
    val grow = rememberLandingGrow(row.isNew)
    val reveal = remember { BringIntoViewRequester() }
    // An entry just logged lands at the bottom of a long log, so it brings itself into view,
    // once: coming back to the task later leaves the page where the person left it.
    var revealed by rememberSaveable(row.id) { mutableStateOf(false) }
    if (row.isNew && !revealed) {
        LaunchedEffect(row.id) {
            reveal.bringIntoView()
            revealed = true
        }
    }
    val who = if (row.byMe) null else row.author
    val meta = listOfNotNull(row.moment?.text(), who).joinToString(META_SEPARATOR)
    Row(
        modifier = modifier
            .then(if (grow != null) Modifier.landingGrow(grow) else Modifier)
            .bringIntoViewRequester(reveal)
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraIconTile(
            icon = R.drawable.feature_tasks_ic_activity,
            tone = if (row.isNew) WaiveraTileTone.Teal else WaiveraTileTone.Brand,
            size = WaiveraSizes.FieldTile,
            iconSize = WaiveraSizes.FieldTileGlyph,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            ActivityHeadline(type = row.type, mine = row.byMe)
            if (meta.isNotEmpty()) {
                // Time first, so a long name ellipsizes at the end and the time always shows.
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            row.description?.let {
                ActivityNote(it, startExpanded, Modifier.padding(top = NoteGap))
            }
        }
    }
}

/**
 * The action as the headline, at most two lines. On your own entries the "You" tag sits at the
 * end of the last line when it fits there, and drops to its own line only when it doesn't.
 */
@Composable
private fun ActivityHeadline(type: String, mine: Boolean) {
    val lines = remember { HeadlineLines() }
    val gap = WaiveraSpacing.Small
    Layout(
        contents = listOf(
            {
                Text(
                    text = type,
                    style = headlineStyle(),
                    color = WaiveraTheme.colors.text,
                    maxLines = HEADLINE_LINES,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { lines.result = it },
                )
            },
            {
                if (mine) {
                    WaiveraStatusBadge(
                        text = stringResource(R.string.feature_tasks_activity_by_you),
                        tone = BadgeTone.Accent,
                    )
                }
            },
        ),
    ) { (texts, badges), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val text = texts.firstOrNull()?.measure(loose)
        val badge = badges.firstOrNull()?.measure(loose)
        val layout = lines.result
        val textHeight = text?.height ?: 0
        if (badge == null) return@Layout layout(text?.width ?: 0, textHeight) { text?.placeRelative(0, 0) }
        val gapPx = gap.roundToPx()
        val last = layout?.lineCount?.minus(1) ?: 0
        val lastRight = if (type.isEmpty() || layout == null) -gapPx else layout.getLineRight(last).roundToInt()
        val fitsInline = lastRight + gapPx + badge.width <= constraints.maxWidth
        if (fitsInline) {
            // Centred on the last line, so the tag reads as part of the headline.
            val lineTop = layout?.getLineTop(last)?.roundToInt() ?: 0
            val lineBottom = layout?.getLineBottom(last)?.roundToInt() ?: badge.height
            val badgeY = lineTop + (lineBottom - lineTop - badge.height) / 2
            val top = minOf(0, badgeY)
            val height = maxOf(textHeight, badgeY + badge.height) - top
            layout(constraints.maxWidth, height) {
                text?.placeRelative(0, -top)
                badge.placeRelative(lastRight + gapPx, badgeY - top)
            }
        } else {
            val rowGap = WaiveraSpacing.XxSmall.roundToPx()
            layout(constraints.maxWidth, textHeight + rowGap + badge.height) {
                text?.placeRelative(0, 0)
                badge.placeRelative(0, textHeight + rowGap)
            }
        }
    }
}

/** The headline's last layout, read while placing the tag; not state, so it never recomposes. */
private class HeadlineLines {
    var result: TextLayoutResult? = null
}

/**
 * The note, cut at [ACTIVITY_LINES] lines with "Show more" when it runs longer. The toggle only
 * appears once the text is known to be cut, and the open state survives rotation. Opening and
 * closing animate the height, timed by how far it moves ([WaiveraMotionScheme.expand]).
 */
@Composable
internal fun ActivityNote(
    text: String,
    startExpanded: Boolean,
    modifier: Modifier = Modifier,
    lines: Int = ACTIVITY_LINES,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val colors = WaiveraTheme.colors
    var expanded by rememberSaveable(text) { mutableStateOf(startExpanded) }
    var cut by rememberSaveable(text) { mutableStateOf(startExpanded) }
    val reveal = rememberNoteReveal(expanded)
    val more = stringResource(R.string.feature_tasks_activity_show_more)
    val less = stringResource(R.string.feature_tasks_activity_show_less)
    val state = stringResource(
        if (expanded) R.string.feature_tasks_activity_expanded else R.string.feature_tasks_activity_collapsed,
    )
    val toggles = cut || expanded
    Column(
        modifier = modifier.then(
            if (toggles) {
                Modifier
                    .clickable(role = Role.Button, onClickLabel = if (expanded) less else more) {
                        if (expanded) reveal.collapse() else reveal.open()
                        expanded = !expanded
                    }
                    .semantics { stateDescription = state }
            } else {
                Modifier
            },
        ),
    ) {
        Text(
            text = text,
            style = style,
            color = colors.text,
            // While it closes the whole note stays laid out and the clip shrinks over it; the
            // three-line ellipsis comes back once the height has settled.
            maxLines = if (reveal.showsAll) Int.MAX_VALUE else lines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout ->
                if (layout.lineCount > 0) {
                    // The closed height is the bottom of the third line, known from either layout.
                    val last = minOf(lines, layout.lineCount) - 1
                    reveal.collapsedHeight = layout.getLineBottom(last).roundToInt()
                    if (!reveal.showsAll) cut = layout.isLineEllipsized(layout.lineCount - 1)
                }
            },
            modifier = Modifier.noteReveal(reveal),
        )
        if (toggles) {
            Text(
                text = if (expanded) less else more,
                style = MaterialTheme.typography.labelLarge,
                color = colors.link,
                // The whole note is the tap target, so this line needs no target of its own.
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
        }
    }
}

/** Height animation state for one note: where it is, where it is going, and the cut height. */
@Stable
private class NoteReveal(
    private val scope: CoroutineScope,
    private val motion: WaiveraMotionScheme,
    private val density: Density,
    expanded: Boolean,
) {
    val height = Animatable(0f)
    var expanded by mutableStateOf(expanded)
    var closing by mutableStateOf(false)
        private set
    var collapsedHeight = 0
    private var target = UNSET
    private var pendingFrom by mutableIntStateOf(UNSET)

    val showsAll: Boolean get() = expanded || closing

    fun collapse() {
        closing = true
    }

    fun open() {
        closing = false
    }

    /** Called from layout with the text's full height; returns the height to show this frame. */
    fun heightFor(full: Int): Int {
        val goal = if (expanded || collapsedHeight == 0) full else collapsedHeight
        if (target == UNSET) {
            target = goal
            return goal
        }
        if (goal != target) {
            val previous = target
            target = goal
            val from = if (height.isRunning) height.value else previous.toFloat()
            val distanceDp = with(density) { abs(goal - from).toDp().value }
            // Hold the old height until the animation picks up, so the first frame doesn't jump.
            pendingFrom = from.roundToInt()
            scope.launch {
                try {
                    height.snapTo(from)
                    height.animateTo(goal.toFloat(), motion.expand(distanceDp))
                    if (!expanded) closing = false
                } finally {
                    if (target == goal) pendingFrom = UNSET
                }
            }
        }
        return when {
            height.isRunning -> height.value.roundToInt()
            pendingFrom != UNSET -> pendingFrom
            else -> goal
        }
    }

    private companion object {
        const val UNSET = -1
    }
}

@Composable
private fun rememberNoteReveal(expanded: Boolean): NoteReveal {
    val scope = rememberCoroutineScope()
    val motion = WaiveraTheme.motion
    val density = LocalDensity.current
    val reveal = remember(scope, motion, density) { NoteReveal(scope, motion, density, expanded) }
    reveal.expanded = expanded
    return reveal
}

/** Lays the text out at its natural height and shows only [NoteReveal.heightFor] of it. */
private fun Modifier.noteReveal(reveal: NoteReveal): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val shown = reveal.heightFor(placeable.height)
    layout(placeable.width, shown) { placeable.placeRelative(0, 0) }
}

private const val META_SEPARATOR = " · "

/** Three lines keeps every entry about the same height; the rest opens in place. */
internal const val ACTIVITY_LINES = 3
private const val HEADLINE_LINES = 2

/** The action reads as the entry's name, like a file name; time and note are the small print. */
@Composable
private fun headlineStyle(): TextStyle = MaterialTheme.typography.titleSmall

/** Headline and time read as one block; the note is the content, so it sits a little apart. */
private val NoteGap = WaiveraSpacing.ExtraSmall

@WaiveraPreviews
@Composable
private fun ActivityItemPreview() {
    WaiveraTheme {
        WaiveraCardGroup {
            TaskPreviewData.activity.forEachIndexed { index, row ->
                if (index > 0) WaiveraInsetDivider()
                ActivityItem(row)
            }
        }
    }
}
