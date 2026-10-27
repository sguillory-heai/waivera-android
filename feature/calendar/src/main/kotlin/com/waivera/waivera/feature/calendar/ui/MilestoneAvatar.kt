package com.waivera.waivera.feature.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.MilestoneBadge
import com.waivera.waivera.core.designsystem.component.MilestoneKind
import com.waivera.waivera.core.designsystem.component.WaiveraMilestoneAvatar
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.calendar.R
import com.waivera.waivera.feature.calendar.model.Celebration
import com.waivera.waivera.feature.calendar.model.CelebrationType

/**
 * The design-system milestone avatar (07 §4) for a [Celebration]. The whole avatar is announced
 * as the celebration sentence, or as the person's name when there is none.
 *
 * Pass `celebration = null` for non-employee attendees: nothing is computed or drawn for them.
 * [userId] loads the person's photo through the app's one Coil avatar loader (memory cache,
 * negative cache, no flash back to initials), the same as chat and Today (UI3-05).
 */
@Composable
fun MilestoneAvatar(
    name: String,
    celebration: Celebration?,
    size: Dp,
    modifier: Modifier = Modifier,
    userId: String? = null,
) {
    val fallbackName = stringResource(R.string.feature_calendar_celebration_name_fallback)
    val label = celebration?.let { CelebrationText.accessible(it, fallbackName).resolve() }
        ?: name.ifBlank { fallbackName }
    WaiveraMilestoneAvatar(
        name = name,
        badge = celebration?.let { MilestoneBadge(it.type.milestoneKind(), it.years) },
        size = size,
        modifier = modifier,
        imageModel = AvatarModel.of(userId),
        contentDescription = label,
    )
}

private fun CelebrationType.milestoneKind(): MilestoneKind = when (this) {
    CelebrationType.BIRTHDAY -> MilestoneKind.Birthday
    CelebrationType.WORK_ANNIVERSARY -> MilestoneKind.WorkAnniversary
    CelebrationType.MULTIPLE -> MilestoneKind.Multiple
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
