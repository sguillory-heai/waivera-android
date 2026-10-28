package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.component.SkeletonAvatar
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isShortWindow
import com.waivera.waivera.feature.today.R

/**
 * A section that failed while the rest of Today works: a quiet row (muted error icon, message,
 * text Retry), not a danger banner, so it is never louder than a failed send.
 */
@Composable
internal fun SectionError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.Small)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The standard tile, in its quiet neutral tone: a section that didn't load is calm (UI8-10).
        WaiveraIconTile(
            icon = WaiveraIcons.Error,
            tone = WaiveraTileTone.Neutral,
            size = WaiveraSizes.IconTileSmall,
            iconSize = WaiveraSizes.IconTileSmallGlyph,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        WaiveraButton(
            text = stringResource(R.string.feature_today_retry),
            onClick = onRetry,
            variant = WaiveraButtonVariant.Text,
        )
    }
}

/** A quiet line of secondary text (empty sections, the stale timestamp). */
@Composable
internal fun QuietLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraSpacing.MinTouchTarget)
            .padding(horizontal = WaiveraSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = WaiveraTheme.colors.textSecondary)
    }
}

/**
 * First-load skeleton on the sheet, shaped like the final screen: a section header and cards,
 * twice, on one shared pulse. The hero above shows its own placeholder card.
 */
@Composable
internal fun TodaySkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_today_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = loading },
    ) {
        repeat(SKELETON_SECTIONS) { section ->
            // The section header's row: its title line, held to the Show all button's height.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
                        end = WaiveraSpacing.Gutter,
                        top = if (section == 0) WaiveraSpacing.MediumSmall else WaiveraSpacing.Large,
                        bottom = WaiveraSpacing.XxSmall,
                    )
                    .heightIn(min = WaiveraSpacing.MinTouchTarget),
                contentAlignment = Alignment.CenterStart,
            ) {
                SkeletonText(
                    style = MaterialTheme.typography.titleMedium,
                    widthFraction = SKELETON_HEADER_FRACTION,
                    pulse = pulse,
                )
            }
            repeat(SKELETON_ROWS) { SkeletonCard(pulse = pulse) }
        }
    }
}

/** Placeholder cards for one section whose source is still loading. */
@Composable
internal fun SectionSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_today_loading)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = loading }) {
        repeat(SKELETON_ROWS) { SkeletonCard(pulse = pulse) }
    }
}

/**
 * A skeleton [TodayRow]: the same lifted card, padding, tile and gap, with bones for the eyebrow,
 * a two-line headline and the supporting line, each at its text style's height.
 */
@Composable
private fun SkeletonCard(pulse: Float) {
    val inset = if (isShortWindow()) WaiveraSpacing.ExtraSmall else WaiveraSpacing.ExtraSmall + WaiveraSpacing.Hairline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter, vertical = inset)
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(WaiveraTheme.colors.surface, WaiveraShapes.SheetCard)
            .heightIn(min = WaiveraSizes.ListRowMinHeight)
            .padding(CardPadding),
        horizontalArrangement = Arrangement.spacedBy(CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonAvatar(size = WaiveraSizes.CardTile, pulse = pulse)
        Column(modifier = Modifier.weight(1f)) {
            SkeletonText(
                style = WaiveraTextStyles.Kicker,
                widthFraction = SKELETON_EYEBROW_FRACTION,
                pulse = pulse,
                modifier = Modifier.padding(bottom = WaiveraSpacing.XxSmall),
            )
            SkeletonText(
                style = MaterialTheme.typography.titleSmall,
                widthFraction = SKELETON_TITLE_FRACTION,
                pulse = pulse,
            )
            SkeletonText(
                style = MaterialTheme.typography.titleSmall,
                widthFraction = SKELETON_TITLE_END_FRACTION,
                pulse = pulse,
            )
            SkeletonText(
                style = MaterialTheme.typography.bodySmall,
                widthFraction = SKELETON_SUPPORTING_FRACTION,
                pulse = pulse,
                modifier = Modifier.padding(top = WaiveraSpacing.XxSmall),
            )
        }
    }
}

private const val SKELETON_SECTIONS = 2
private const val SKELETON_ROWS = 3
private const val SKELETON_HEADER_FRACTION = 0.35f
private const val SKELETON_EYEBROW_FRACTION = 0.3f
private const val SKELETON_TITLE_FRACTION = 0.9f
private const val SKELETON_TITLE_END_FRACTION = 0.5f
private const val SKELETON_SUPPORTING_FRACTION = 0.65f

@WaiveraPreviews
@Composable
private fun TodayStatesPreview() {
    WaiveraTheme {
        Column(Modifier.heightIn(min = WaiveraSizes.AvatarHero)) {
            SectionError(message = "Your schedule couldn't load.", onRetry = {})
            TodaySkeleton()
        }
    }
}
