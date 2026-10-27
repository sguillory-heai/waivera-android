package com.waivera.waivera.feature.today.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.component.SkeletonLine
import com.waivera.waivera.core.designsystem.component.SkeletonListRow
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraEmptyState
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import kotlinx.coroutines.launch

/**
 * "You're all caught up": the one place the celebrate motion token is used. It settles in with
 * a soft spring once; with animations off it simply appears.
 */
@Composable
internal fun AllCaughtUp(modifier: Modifier = Modifier) {
    val motion = WaiveraTheme.motion
    val scale = remember { Animatable(if (motion.reduced) 1f else CELEBRATE_START_SCALE) }
    val alpha = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(motion) {
        launch { scale.animateTo(1f, motion.celebrate()) }
        alpha.animateTo(1f, motion.state())
    }
    WaiveraEmptyState(
        title = stringResource(R.string.feature_today_all_caught_up_title),
        message = stringResource(R.string.feature_today_all_caught_up_message),
        icon = R.drawable.feature_today_ic_celebration,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        },
    )
}

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
        Icon(
            painter = painterResource(WaiveraIcons.Error),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(WaiveraSizes.Icon),
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

/** First-load skeleton shaped like the final list: two section headers with rows, one shared pulse. */
@Composable
internal fun TodaySkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_today_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
    ) {
        repeat(SKELETON_SECTIONS) {
            SkeletonLine(
                widthFraction = SKELETON_HEADER_FRACTION,
                pulse = pulse,
                modifier = Modifier.padding(
                    start = WaiveraSpacing.Gutter,
                    top = WaiveraSpacing.Large,
                    bottom = WaiveraSpacing.Small,
                ),
            )
            repeat(SKELETON_ROWS) { SkeletonListRow(pulse = pulse) }
        }
    }
}

/** Placeholder rows for one section whose source is still loading. */
@Composable
internal fun SectionSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_today_loading)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = loading }) {
        repeat(SKELETON_ROWS) { SkeletonListRow(pulse = pulse) }
    }
}

private const val CELEBRATE_START_SCALE = 0.85f
private const val SKELETON_SECTIONS = 2
private const val SKELETON_ROWS = 3
private const val SKELETON_HEADER_FRACTION = 0.3f

@WaiveraPreviews
@Composable
private fun TodayStatesPreview() {
    WaiveraTheme {
        Column(Modifier.heightIn(min = WaiveraSizes.AvatarHero)) {
            AllCaughtUp()
            SectionError(message = "Your schedule couldn't load.", onRetry = {})
            TodaySkeleton()
        }
    }
}
