package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** A section's trailing link ("Show all", "See schedule") and what it opens. */
data class WaiveraSectionAction(val label: String, val onClick: () -> Unit, val onClickLabel: String? = null)

/**
 * A section heading on a tab screen's sheet: the title (B's 17 / 600), an optional gradient
 * count ("Needs you 4", announced as [countDescription]) and an optional trailing link. The title
 * is a heading for TalkBack's heading navigation.
 *
 * The row is as tall as its title, not the link's 48dp target, so the sheet's first card sits
 * close under the curve (UI10-01). The link keeps its full 48dp target, centred on the title and
 * reaching past the row by up to [WaiveraSizes.SectionActionOverhang] above and below; callers leave at least
 * that much room under the row, so the card below never takes the link's taps.
 */
@Composable
fun WaiveraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    countDescription: String? = null,
    action: WaiveraSectionAction? = null,
) {
    val colors = WaiveraTheme.colors
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // The title wraps before it pushes the count or the link off the row at large font.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() },
                )
                if (count != null && count > 0) {
                    Spacer(Modifier.width(WaiveraSpacing.Small))
                    WaiveraGradientCount(
                        text = formatBadgeCount(count).orEmpty(),
                        contentDescription = countDescription ?: count.toString(),
                    )
                }
            }
            if (action != null) {
                WaiveraPressable(
                    onClick = action.onClick,
                    onClickLabel = action.onClickLabel,
                    shape = WaiveraShapes.Chip,
                    modifier = Modifier.heightIn(min = WaiveraSpacing.MinTouchTarget),
                ) {
                    Text(
                        text = action.label,
                        style = WaiveraTextStyles.SectionAction,
                        color = colors.link,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = WaiveraSpacing.Small),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val link = measurables.getOrNull(1)?.measure(loose)
        val linkWidth = link?.width ?: 0
        val titleRow = measurables[0].measure(
            loose.copy(maxWidth = (constraints.maxWidth - linkWidth).coerceAtLeast(0)),
        )
        val height = titleRow.height.coerceAtLeast(constraints.minHeight)
        val width = constraints.maxWidth
        layout(width, height) {
            titleRow.placeRelative(0, (height - titleRow.height) / 2)
            // Centred on the title: a link taller than the row reaches past it, above and below.
            link?.placeRelative(width - link.width, (height - link.height) / 2)
        }
    }
}

/**
 * The concept's gradient count: white on the CTA gradient (the brand gradient's pink end fails
 * white), 24dp tall with 8dp corners.
 */
@Composable
fun WaiveraGradientCount(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val hero = WaiveraHero.colors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = WaiveraSizes.CountBadgeHeight, minHeight = WaiveraSizes.CountBadgeHeight)
            .background(hero.buttonBrush, WaiveraShapes.CountBadge)
            .padding(horizontal = WaiveraSpacing.Small)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = WaiveraTextStyles.SectionCount, color = hero.onButton, maxLines = 1, softWrap = false)
    }
}

/**
 * A content card on a tab screen's sheet: the surface with 20dp corners and a soft lift, as in the
 * concept. It is a plain container: a whole-card tap goes on the content (with its own label), so
 * the actions inside stay separate targets.
 */
@Composable
fun WaiveraSheetCard(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(colors.surface, WaiveraShapes.SheetCard)
            .padding(WaiveraSpacing.MediumSmall + WaiveraSpacing.XxSmall),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@WaiveraPreviews
@Composable
private fun WaiveraSectionPreview() {
    WaiveraTheme {
        Column(
            modifier = Modifier
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraSectionHeader(
                title = "Needs you",
                count = 4,
                countDescription = "4 items",
                action = WaiveraSectionAction(label = "Show all", onClick = {}),
            )
            WaiveraSheetCard {
                Text(text = "Care plan review", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Invitation from Jordan",
                    style = MaterialTheme.typography.bodySmall,
                    color = WaiveraTheme.colors.textSecondary,
                )
            }
        }
    }
}
