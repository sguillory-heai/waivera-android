package com.waivera.waivera.feature.calendar.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads avatar bytes for a backend user id; null when there is no picture. */
fun interface AvatarLoader {
    suspend fun load(userId: String): ByteArray?
}

/**
 * The design-system milestone avatar (07 §4) for a [Celebration]. The whole avatar is announced
 * as the celebration sentence, or as the person's name when there is none.
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
    WaiveraMilestoneAvatar(
        name = name,
        badge = celebration?.let { MilestoneBadge(it.type.milestoneKind(), it.years) },
        size = size,
        modifier = modifier,
        painter = painter,
        contentDescription = label,
    )
}

private fun CelebrationType.milestoneKind(): MilestoneKind = when (this) {
    CelebrationType.BIRTHDAY -> MilestoneKind.Birthday
    CelebrationType.WORK_ANNIVERSARY -> MilestoneKind.WorkAnniversary
    CelebrationType.MULTIPLE -> MilestoneKind.Multiple
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
