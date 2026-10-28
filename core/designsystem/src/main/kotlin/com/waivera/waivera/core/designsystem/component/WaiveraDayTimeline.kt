package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraPalette
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** An event block's colour on the "Your day" track. */
enum class WaiveraTimelineTone(internal val top: Color, internal val bottom: Color) {
    Teal(WaiveraPalette.TimelineTealTop, WaiveraPalette.TimelineTealBottom),
    Violet(WaiveraPalette.TimelineVioletTop, WaiveraPalette.TimelineVioletBottom),
    Gold(WaiveraPalette.TimelineGoldTop, WaiveraPalette.TimelineGoldBottom),
}

/** One event on the track, from [start] to [end] as shares of the window (0 is its left edge). */
@Immutable
data class WaiveraTimelineBlock(val start: Float, val end: Float, val tone: WaiveraTimelineTone)

/** An hour label under the track, centred on [at] (a share of the window). */
@Immutable
data class WaiveraTimelineLabel(val at: Float, val text: String)

/**
 * Concept B's "Your day" card: a title and summary ("3 events · 2 hr scheduled"), the day as a
 * track with hour ticks, a block per event and a pink now-line, the hour labels under it, and
 * [footer] for the next event. Positions are shares of the window the caller chose (8 AM to 6 PM
 * by default in Today), worked out in the employee's zone by the caller.
 *
 * The track is one item for TalkBack, read as [trackDescription]; the blocks are colour only, so
 * the description and the footer carry the times. Hour labels that would collide at large font
 * are dropped, never overlapped.
 */
@Composable
fun WaiveraDayTimeline(
    title: String,
    trackDescription: String,
    blocks: List<WaiveraTimelineBlock>,
    modifier: Modifier = Modifier,
    summary: String? = null,
    ticks: List<Float> = emptyList(),
    labels: List<WaiveraTimelineLabel> = emptyList(),
    now: Float? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.TimelinePanel, clip = false)
            .background(colors.surface, WaiveraShapes.TimelinePanel)
            .padding(
                horizontal = WaiveraSpacing.Medium,
                vertical = WaiveraSpacing.MediumSmall + WaiveraSpacing.XxSmall,
            ),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = WaiveraTextStyles.PanelTitle,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        TimelineTrack(blocks = blocks, ticks = ticks, now = now, description = trackDescription)
        if (labels.isNotEmpty()) AxisLabels(labels = labels)
        footer()
    }
}

@Composable
private fun TimelineTrack(blocks: List<WaiveraTimelineBlock>, ticks: List<Float>, now: Float?, description: String) {
    val colors = WaiveraTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WaiveraSizes.TimelineTrack)
            .clearAndSetSemantics { contentDescription = description }
            .clip(WaiveraShapes.TimelineTrack)
            .background(colors.surfaceMuted)
            .drawBehind {
                val tick = WaiveraSizes.TimelineTick.toPx()
                ticks.forEach { at ->
                    val x = size.width * at.coerceIn(0f, 1f)
                    drawLine(
                        color = colors.border,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = tick,
                    )
                }
                val inset = WaiveraSizes.TimelineBlockInset.toPx()
                val corner = CornerRadius(WaiveraSizes.TimelineBlockInset.toPx() + WaiveraSpacing.Hairline.toPx())
                blocks.forEach { block ->
                    val left = size.width * block.start.coerceIn(0f, 1f)
                    val right = size.width * block.end.coerceIn(0f, 1f)
                    val width = (right - left).coerceAtLeast(inset)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(block.tone.top, block.tone.bottom),
                            startY = inset,
                            endY = size.height - inset,
                        ),
                        topLeft = Offset(left, inset),
                        size = Size(width, size.height - inset * 2),
                        cornerRadius = corner,
                    )
                }
            },
    ) {
        if (now != null && now in 0f..1f) {
            NowLine(at = now)
        }
    }
}

/** The pink now-line with its dot at the top; drawn over the blocks. */
@Composable
private fun NowLine(at: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WaiveraSizes.TimelineTrack)
            .drawBehind {
                val x = size.width * at
                val line = WaiveraSizes.TimelineNowLine.toPx()
                val dot = WaiveraSizes.TimelineNowDot.toPx()
                drawLine(
                    color = WaiveraPalette.BrandPink,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = line,
                )
                drawCircle(color = WaiveraPalette.BrandPink, radius = dot / 2, center = Offset(x, dot / 2))
            },
    )
}

/** Hour labels centred on their positions, kept inside the track, dropping any that would collide. */
@Composable
private fun AxisLabels(labels: List<WaiveraTimelineLabel>) {
    val colors = WaiveraTheme.colors
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        content = {
            labels.forEach { label ->
                Text(text = label.text, style = WaiveraTextStyles.AxisLabel, color = colors.textSecondary, maxLines = 1)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val gap = WaiveraSpacing.Small.roundToPx()
        val xs = placeAxisLabels(
            centers = labels.map { it.at },
            widths = placeables.map { it.width },
            total = width,
            gap = gap,
        )
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                xs[index]?.let { x -> placeable.place(x = x, y = 0) }
            }
        }
    }
}

/**
 * Where each axis label starts, or null when it is dropped: centred on its share of [total],
 * clamped inside, and skipped when it would come within [gap] of the last label kept.
 */
internal fun placeAxisLabels(centers: List<Float>, widths: List<Int>, total: Int, gap: Int): List<Int?> {
    var lastEnd = Int.MIN_VALUE
    return centers.indices.map { index ->
        val width = widths[index]
        val ideal = (total * centers[index].coerceIn(0f, 1f)).toInt() - width / 2
        val x = ideal.coerceIn(0, (total - width).coerceAtLeast(0))
        if (lastEnd != Int.MIN_VALUE && x < lastEnd + gap) {
            null
        } else {
            lastEnd = x + width
            x
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraDayTimelinePreview() {
    WaiveraTheme {
        Box(
            modifier = Modifier
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Medium),
        ) {
            WaiveraDayTimeline(
                title = "Your day",
                summary = "3 events · 2 hr scheduled",
                trackDescription = "3 events between 9 AM and 3 PM",
                blocks = listOf(
                    WaiveraTimelineBlock(start = 0.1f, end = 0.2f, tone = WaiveraTimelineTone.Teal),
                    WaiveraTimelineBlock(start = 0.3f, end = 0.4f, tone = WaiveraTimelineTone.Violet),
                    WaiveraTimelineBlock(start = 0.6f, end = 0.7f, tone = WaiveraTimelineTone.Gold),
                ),
                ticks = (1..9).map { it / 10f },
                labels = listOf(
                    WaiveraTimelineLabel(at = 0f, text = "8 AM"),
                    WaiveraTimelineLabel(at = 0.4f, text = "12 PM"),
                    WaiveraTimelineLabel(at = 1f, text = "6 PM"),
                ),
                now = 0.25f,
            )
        }
    }
}
