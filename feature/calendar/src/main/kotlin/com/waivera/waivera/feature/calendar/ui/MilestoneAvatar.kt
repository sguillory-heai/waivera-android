package com.waivera.waivera.feature.calendar.ui

import android.graphics.BitmapFactory
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.Celebration
import com.waivera.waivera.feature.calendar.model.CelebrationType
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads avatar bytes for a backend user id; null when there is no picture. */
fun interface AvatarLoader {
    suspend fun load(userId: String): ByteArray?
}

/**
 * Avatar with the milestone badge from 07 §4: cake for a birthday, award (plus the year count
 * on avatars of 40dp and up) for a work anniversary, party popper for both. The whole avatar is
 * announced as the celebration sentence, or as the person's name when there is none.
 *
 * Pass `celebration = null` for non-employee attendees: nothing is computed or drawn for them.
 */
@Composable
fun MilestoneAvatar(
    name: String,
    celebration: Celebration?,
    size: Dp,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
) {
    val fallbackName = stringResource(R.string.feature_calendar_celebration_name_fallback)
    val label = celebration?.let { CelebrationText.accessible(it, fallbackName).resolve() }
        ?: name.ifBlank { fallbackName }
    Box(modifier = modifier.clearAndSetSemantics { contentDescription = label }) {
        WaiveraAvatar(name = name, painter = painter, size = size, contentDescription = null)
        if (celebration != null) {
            MilestoneBadge(
                celebration = celebration,
                avatarSize = size,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun MilestoneBadge(celebration: Celebration, avatarSize: Dp, modifier: Modifier = Modifier) {
    val colors = WaiveraTheme.colors
    val badge = maxOf(CalendarSizes.BadgeMin, (avatarSize.value * CalendarSizes.BADGE_RATIO).roundToInt().dp)
    val (container, content) = celebration.type.badgeColors(colors)
    val showYears = celebration.type == CelebrationType.WORK_ANNIVERSARY &&
        celebration.years != null &&
        avatarSize >= CalendarSizes.BadgeYearsMinAvatar
    Row(
        modifier = modifier
            .offset(
                x = (badge.value * CalendarSizes.BADGE_OFFSET_X_RATIO).roundToInt().dp,
                y = -(badge.value * CalendarSizes.BADGE_OFFSET_Y_RATIO).roundToInt().dp,
            )
            .heightIn(min = badge)
            .widthIn(min = badge)
            .border(CalendarSizes.BadgeBorder, colors.background, WaiveraShapes.Circle)
            .padding(CalendarSizes.BadgeBorder)
            .background(container, WaiveraShapes.Circle)
            .padding(horizontal = if (showYears) WaiveraSpacing.XxSmall else 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(celebration.type.badgeIcon()),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(badge * CalendarSizes.BADGE_GLYPH_RATIO),
        )
        if (showYears) {
            // Sized from the badge, not the font scale: the badge is a fixed-size image.
            val fontSize = with(LocalDensity.current) { (badge * CalendarSizes.BADGE_YEARS_RATIO).toSp() }
            Text(
                text = celebration.years.toString(),
                color = content,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

private fun CelebrationType.badgeIcon(): Int = when (this) {
    CelebrationType.BIRTHDAY -> CalendarIcons.Cake
    CelebrationType.WORK_ANNIVERSARY -> CalendarIcons.Award
    CelebrationType.MULTIPLE -> CalendarIcons.Celebration
}

private fun CelebrationType.badgeColors(colors: WaiveraColors): Pair<Color, Color> = when (this) {
    CelebrationType.BIRTHDAY -> colors.birthdayContainer to colors.onBirthdayContainer
    CelebrationType.WORK_ANNIVERSARY -> colors.anniversaryContainer to colors.onAnniversaryContainer
    CelebrationType.MULTIPLE -> colors.accentSoft to colors.onAccentSoft
}

/**
 * Decodes the avatar for [userId] off the main thread. Keeps showing the initials fallback
 * until bytes arrive; a missing or broken image simply stays on the fallback.
 */
@Composable
fun rememberAvatarPainter(userId: String?, loader: AvatarLoader?): Painter? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, userId, loader) {
        if (userId.isNullOrBlank() || loader == null) return@produceState
        val bytes = loader.load(userId) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    return remember(bitmap) { bitmap?.let(::BitmapPainter) }
}

@WaiveraPreviews
@Composable
private fun MilestoneAvatarPreview() {
    WaiveraTheme {
        Row(
            modifier = Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Large),
        ) {
            val large = WaiveraSizes.AvatarMedium
            MilestoneAvatar("Priya Raman", Celebration(CelebrationType.BIRTHDAY, null, "Priya Raman"), large)
            MilestoneAvatar(
                "Jordan Lee",
                Celebration(CelebrationType.WORK_ANNIVERSARY, PREVIEW_YEARS, "Jordan Lee"),
                large,
            )
            MilestoneAvatar(
                "Jordan Lee",
                Celebration(CelebrationType.WORK_ANNIVERSARY, PREVIEW_YEARS, "Jordan Lee"),
                WaiveraSizes.AvatarSmall,
            )
            MilestoneAvatar("Sam Ortiz", Celebration(CelebrationType.MULTIPLE, PREVIEW_YEARS, "Sam Ortiz"), large)
            MilestoneAvatar("Alex Rivera", null, large)
        }
    }
}

private const val PREVIEW_YEARS = 5
