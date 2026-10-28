package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.waivera.waivera.core.designsystem.component.SkeletonAvatar
import com.waivera.waivera.core.designsystem.component.SkeletonBlock
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.component.skeletonLineHeight
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R

/**
 * Message bones shaped like [MessageRow]: someone else's message has the small avatar, the author
 * line, a one-line bubble and its time; mine has the bubble and its "Sent" line on the right. The
 * bones use the real rows' paddings and text line heights, so the skeleton is as tall as what
 * lands at every font scale. All bones share one pulse and the group is announced once (03 §21).
 *
 * With [fillHeight] the bones fill the whole timeline from the bottom up, as the newest messages
 * do, and the oldest bone is clipped at the top edge instead of leaving the screen half empty.
 * Without it, [rows] bones stack at their own height (the older-page row at the top of a list).
 */
@Composable
fun MessageListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = ChatDimens.SKELETON_FRACTIONS_COUNT,
    fillHeight: Boolean = false,
) {
    val pulse = rememberSkeletonPulse()
    val loading = stringResource(R.string.feature_chat_messages_loading)
    val count = if (fillHeight) ChatDimens.SKELETON_FILL_ROWS else rows
    val sizing = if (fillHeight) {
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(
        modifier = modifier
            .then(sizing)
            .padding(vertical = WaiveraSpacing.Small)
            .clearAndSetSemantics { contentDescription = loading },
    ) {
        repeat(count) { index ->
            val fraction = ChatDimens.SkeletonWidths[index % ChatDimens.SkeletonWidths.size]
            // Counted from the bottom, so the newest bone (the last one) is always someone else's.
            val mine = (count - 1 - index) % 2 == 1
            if (mine) MineBone(fraction, pulse) else IncomingBone(fraction, pulse)
        }
    }
}

@Composable
private fun IncomingBone(fraction: Float, pulse: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.Gutter, top = WaiveraSpacing.Small),
    ) {
        SkeletonAvatar(
            size = WaiveraSizes.AvatarSmall,
            pulse = pulse,
            modifier = Modifier.padding(top = ChatDimens.AvatarTopOffset),
        )
        Spacer(Modifier.width(WaiveraSpacing.Small))
        Column(modifier = Modifier.fillMaxWidth(ChatDimens.BUBBLE_MAX_WIDTH_FRACTION)) {
            SkeletonText(
                style = MaterialTheme.typography.labelSmall,
                widthFraction = AUTHOR_FRACTION,
                pulse = pulse,
                modifier = Modifier.padding(bottom = WaiveraSpacing.XxSmall),
            )
            BubbleBone(fraction = fraction, mine = false, pulse = pulse)
            SkeletonText(
                style = MaterialTheme.typography.labelMedium,
                widthFraction = TIME_FRACTION,
                pulse = pulse,
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
        }
    }
}

@Composable
private fun MineBone(fraction: Float, pulse: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.Gutter, top = WaiveraSpacing.Small),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(ChatDimens.BUBBLE_MAX_WIDTH_FRACTION),
            horizontalAlignment = Alignment.End,
        ) {
            BubbleBone(fraction = fraction, mine = true, pulse = pulse)
            SkeletonText(
                style = MaterialTheme.typography.labelMedium,
                widthFraction = SENT_FRACTION,
                pulse = pulse,
                modifier = Modifier.padding(top = WaiveraSpacing.ExtraSmall),
            )
        }
    }
}

/** A one-line bubble: the body's line height plus the bubble's own vertical padding. */
@Composable
private fun BubbleBone(fraction: Float, mine: Boolean, pulse: Float) {
    val height = skeletonLineHeight(MaterialTheme.typography.bodyMedium) + WaiveraSpacing.Small * 2
    SkeletonBlock(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height),
        shape = if (mine) ChatDimens.SkeletonMineShape else ChatDimens.SkeletonOtherShape,
        pulse = pulse,
    )
}

private const val AUTHOR_FRACTION = 0.35f
private const val TIME_FRACTION = 0.22f
private const val SENT_FRACTION = 0.3f

@WaiveraPreviews
@Composable
private fun MessageListSkeletonPreview() {
    WaiveraTheme {
        MessageListSkeleton()
    }
}
