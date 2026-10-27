package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlin.math.roundToInt

/** The corner badge on a [WaiveraMilestoneAvatar]; [years] shows on anniversaries only. */
@Immutable
data class MilestoneBadge(val kind: MilestoneKind, val years: Int? = null)

/**
 * Avatar with the milestone corner badge from 07 §4: cake for a birthday, award (plus the year
 * count on avatars of 40dp and up) for a work anniversary, party popper for more than one. The
 * whole avatar is announced as [contentDescription], which callers make one full sentence
 * ("Priya Raman, birthday today"); null makes it decorative.
 */
@Composable
fun WaiveraMilestoneAvatar(
    name: String,
    badge: MilestoneBadge?,
    size: Dp,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageModel: Any? = null,
    contentDescription: String? = name,
) {
    Box(
        modifier = modifier.clearAndSetSemantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    ) {
        WaiveraAvatar(
            name = name,
            painter = painter,
            imageModel = imageModel,
            size = size,
            contentDescription = null,
        )
        if (badge != null) {
            MilestoneCornerBadge(badge = badge, avatarSize = size, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Badge edge: `max(17, round(avatar × 0.43))`. */
internal fun milestoneBadgeSize(avatarSize: Dp): Dp =
    maxOf(WaiveraSizes.MilestoneBadgeMin, (avatarSize.value * BADGE_RATIO).roundToInt().dp)

/** Years only on anniversaries, and only where they stay legible. */
internal fun showsMilestoneYears(badge: MilestoneBadge, avatarSize: Dp): Boolean =
    badge.kind == MilestoneKind.WorkAnniversary &&
        badge.years != null &&
        avatarSize >= WaiveraSizes.MilestoneYearsMinAvatar

@Composable
private fun MilestoneCornerBadge(badge: MilestoneBadge, avatarSize: Dp, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val edge = milestoneBadgeSize(avatarSize)
    val (container, content) = badge.kind.badgeColors(colors)
    val showYears = showsMilestoneYears(badge, avatarSize)
    Row(
        modifier = modifier
            .offset(
                x = (edge.value * BADGE_OFFSET_X_RATIO).roundToInt().dp,
                y = -(edge.value * BADGE_OFFSET_Y_RATIO).roundToInt().dp,
            )
            .heightIn(min = edge)
            .widthIn(min = edge)
            .border(WaiveraSizes.MilestoneBadgeBorder, colors.background, WaiveraShapes.Circle)
            .padding(WaiveraSizes.MilestoneBadgeBorder)
            .background(container, WaiveraShapes.Circle)
            .padding(horizontal = if (showYears) WaiveraSpacing.XxSmall else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(badge.kind.badgeIcon()),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(edge * BADGE_GLYPH_RATIO),
        )
        if (showYears) {
            // Sized from the badge, not the font scale: the badge is a fixed-size image.
            val fontSize = with(LocalDensity.current) { (edge * BADGE_YEARS_RATIO).toSp() }
            Text(
                text = badge.years.toString(),
                color = content,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

private fun MilestoneKind.badgeIcon(): Int = when (this) {
    MilestoneKind.Birthday -> WaiveraIcons.Cake
    MilestoneKind.WorkAnniversary -> WaiveraIcons.Award
    MilestoneKind.NewHire, MilestoneKind.Multiple -> WaiveraIcons.Celebration
}

private fun MilestoneKind.badgeColors(colors: WaiveraColors): Pair<Color, Color> = when (this) {
    MilestoneKind.Birthday -> colors.birthdayContainer to colors.onBirthdayContainer
    MilestoneKind.WorkAnniversary -> colors.anniversaryContainer to colors.onAnniversaryContainer
    MilestoneKind.NewHire -> colors.newHireContainer to colors.onNewHireContainer
    MilestoneKind.Multiple -> colors.multipleContainer to colors.onMultipleContainer
}

private const val BADGE_RATIO = 0.43f
private const val BADGE_OFFSET_X_RATIO = 0.28f
private const val BADGE_OFFSET_Y_RATIO = 0.25f
private const val BADGE_GLYPH_RATIO = 0.52f
private const val BADGE_YEARS_RATIO = 0.42f

@WaiveraPreviews
@Composable
private fun WaiveraMilestoneAvatarPreview() {
    WaiveraTheme {
        Row(
            modifier = Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Large),
        ) {
            val large = WaiveraSizes.AvatarMedium
            WaiveraMilestoneAvatar("Priya Raman", MilestoneBadge(MilestoneKind.Birthday), large)
            WaiveraMilestoneAvatar("Jordan Lee", MilestoneBadge(MilestoneKind.WorkAnniversary, PREVIEW_YEARS), large)
            WaiveraMilestoneAvatar(
                "Jordan Lee",
                MilestoneBadge(MilestoneKind.WorkAnniversary, PREVIEW_YEARS),
                WaiveraSizes.AvatarSmall,
            )
            WaiveraMilestoneAvatar("Sam Ortiz", MilestoneBadge(MilestoneKind.Multiple, PREVIEW_YEARS), large)
            WaiveraMilestoneAvatar("Alex Rivera", null, large)
        }
    }
}

private const val PREVIEW_YEARS = 5
