package com.waivera.waivera.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.waivera.waivera.core.designsystem.R as DesignR
import com.waivera.waivera.core.designsystem.component.SkeletonAvatar
import com.waivera.waivera.core.designsystem.component.SkeletonListRow
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.waiveraCardGroupItem
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The people directory while it loads: the count line, then list-row bones on one card with
 * dividers between them, exactly where the loaded rows sit.
 */
@Composable
internal fun PeopleSkeleton(modifier: Modifier = Modifier, rows: Int = LIST_ROWS) {
    val colors = WaiveraTheme.colors
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(DesignR.string.core_designsystem_loading)
    Column(modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = loading }) {
        SkeletonText(
            style = MaterialTheme.typography.labelMedium,
            widthFraction = COUNT_FRACTION,
            pulse = pulse,
            modifier = Modifier.padding(
                start = WaiveraSpacing.Gutter + WaiveraSpacing.ExtraSmall,
                end = WaiveraSpacing.Gutter,
                top = WaiveraSpacing.ExtraSmall,
                bottom = WaiveraSpacing.Small,
            ),
        )
        repeat(rows) { index ->
            Column(modifier = Modifier.waiveraCardGroupItem(index = index, count = rows, surface = colors.surface)) {
                SkeletonListRow(pulse = pulse)
                if (index < rows - 1) WaiveraInsetDivider()
            }
        }
    }
}

/**
 * Saved messages while they load: the count line, then one lifted card per bookmark with the
 * author's avatar, name, date and a preview line, and room for the remove button.
 */
@Composable
internal fun BookmarksSkeleton(modifier: Modifier = Modifier, rows: Int = BOOKMARK_ROWS) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(DesignR.string.core_designsystem_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Gutter)
            .clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        SkeletonText(
            style = MaterialTheme.typography.labelMedium,
            widthFraction = COUNT_FRACTION,
            pulse = pulse,
            modifier = Modifier.padding(vertical = WaiveraSpacing.ExtraSmall),
        )
        repeat(rows) { index ->
            BookmarkCardBone(previewFraction = PREVIEW_FRACTIONS[index % PREVIEW_FRACTIONS.size], pulse = pulse)
        }
    }
}

@Composable
private fun BookmarkCardBone(previewFraction: Float, pulse: State<Float>) {
    val colors = WaiveraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = WaiveraElevation.Card, shape = WaiveraShapes.SheetCard, clip = false)
            .background(colors.surface, WaiveraShapes.SheetCard)
            .padding(WaiveraSpacing.MediumSmall),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.Top,
    ) {
        SkeletonAvatar(size = WaiveraSizes.AvatarMedium, pulse = pulse)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
            SkeletonText(style = MaterialTheme.typography.titleSmall, widthFraction = NAME_FRACTION, pulse = pulse)
            SkeletonText(style = MaterialTheme.typography.labelSmall, widthFraction = DATE_FRACTION, pulse = pulse)
            SkeletonText(style = MaterialTheme.typography.bodyMedium, widthFraction = previewFraction, pulse = pulse)
        }
        // The remove button's touch target, so the text bones stop where the loaded text does.
        Spacer(Modifier.size(WaiveraSpacing.MinTouchTarget))
    }
}

private const val LIST_ROWS = 6
private const val BOOKMARK_ROWS = 4
private const val COUNT_FRACTION = 0.3f
private const val NAME_FRACTION = 0.55f
private const val DATE_FRACTION = 0.25f
private val PREVIEW_FRACTIONS = listOf(0.85f, 0.7f, 0.9f, 0.6f)

@WaiveraPreviews
@Composable
private fun ProfileSkeletonsPreview() {
    WaiveraTheme {
        Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Large)) {
            PeopleSkeleton(rows = 2)
            BookmarksSkeleton(rows = 2)
        }
    }
}
