package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

enum class PresenceStatus { Online, Away, Offline }

enum class MilestoneKind { Birthday, WorkAnniversary, NewHire, Multiple }

/**
 * Avatar with an initials fallback (person icon when there are no initials), an optional
 * presence dot and an optional milestone ring. Presence and milestone are also announced in
 * the content description so they are never colour-only.
 *
 * @param painter an already loaded image; wins over [imageModel].
 * @param imageModel a Coil model (for example `AvatarModel(userId)` from core:data) loaded through
 *   the app's singleton image loader, which carries the auth-aware avatar fetcher. The fallback
 *   shows until it loads and stays when there is no picture.
 * @param contentDescription null when the avatar sits next to the person's name and is decorative.
 */
// Image, initials fallback, presence and milestone badges are layered in one Box.
@Suppress("LongMethod")
@Composable
fun WaiveraAvatar(
    name: String,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageModel: Any? = null,
    size: Dp = WaiveraSizes.AvatarMedium,
    rounded: Boolean = true,
    presence: PresenceStatus? = null,
    milestone: MilestoneKind? = null,
    contentDescription: String? = name,
) {
    val colors = WaiveraTheme.colors
    val shape = WaiveraShapes.avatar(size, rounded)
    val initials = remember(name) { initialsFor(name) }
    val presenceLabel = presence?.let { stringResource(it.labelRes()) }
    val milestoneLabel = milestone?.let { stringResource(it.labelRes()) }
    val description = contentDescription?.let { listOfNotNull(it, presenceLabel, milestoneLabel).joinToString(", ") }
    val semanticsModifier = if (description != null) {
        Modifier.semantics { this.contentDescription = description }
    } else {
        Modifier
    }
    Box(modifier = modifier.then(semanticsModifier)) {
        val ringModifier = if (milestone != null) {
            Modifier
                .border(WaiveraSizes.AvatarRing, milestone.ringColor(colors), shape)
                .padding(WaiveraSizes.AvatarRing * 2)
        } else {
            Modifier
        }
        Box(
            modifier = ringModifier
                .size(size)
                .clip(shape)
                .background(if (initials.isEmpty()) colors.surfaceMuted else colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            // Reset per model so a recycled row never shows the previous person's picture.
            var loaded by remember(imageModel) { mutableStateOf(false) }
            when {
                painter != null -> Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
                loaded -> Unit
                initials.isNotEmpty() -> {
                    // Sized from the avatar, not the font scale: the avatar is a fixed-size image.
                    val fontSize = with(LocalDensity.current) { (size * INITIALS_RATIO).toSp() }
                    Text(
                        text = initials,
                        color = colors.onAccentSoft,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                else -> Icon(
                    painter = painterResource(WaiveraIcons.Person),
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(size * FALLBACK_ICON_RATIO),
                )
            }
            if (painter == null && imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Crop,
                    onSuccess = { loaded = true },
                    onError = { loaded = false },
                )
            }
        }
        if (presence != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(WaiveraSizes.PresenceDot)
                    .border(WaiveraSizes.AvatarRing, colors.surface, WaiveraShapes.Circle)
                    .padding(WaiveraSizes.AvatarRing)
                    .background(presence.color(colors), WaiveraShapes.Circle),
            )
        }
    }
}

/** Up to two initials: first and last word, uppercased. Empty when the name has no letters or digits. */
fun initialsFor(name: String): String {
    val words = name.trim().split(WHITESPACE).filter { word -> word.any(Char::isLetterOrDigit) }
    if (words.isEmpty()) return ""
    val picked = if (words.size == 1) listOf(words.first()) else listOf(words.first(), words.last())
    return picked.joinToString(separator = "") { word ->
        val start = word.indexOfFirst(Char::isLetterOrDigit)
        val codePoint = word.codePointAt(start)
        String(Character.toChars(codePoint)).uppercase()
    }
}

private val WHITESPACE = Regex("\\s+")
private const val INITIALS_RATIO = 0.4f
private const val FALLBACK_ICON_RATIO = 0.5f

private fun PresenceStatus.labelRes(): Int = when (this) {
    PresenceStatus.Online -> R.string.core_designsystem_presence_online
    PresenceStatus.Away -> R.string.core_designsystem_presence_away
    PresenceStatus.Offline -> R.string.core_designsystem_presence_offline
}

private fun PresenceStatus.color(colors: WaiveraColors): Color = when (this) {
    PresenceStatus.Online -> colors.presenceOnline
    PresenceStatus.Away -> colors.presenceAway
    PresenceStatus.Offline -> colors.presenceOffline
}

private fun MilestoneKind.labelRes(): Int = when (this) {
    MilestoneKind.Birthday -> R.string.core_designsystem_milestone_birthday
    MilestoneKind.WorkAnniversary -> R.string.core_designsystem_milestone_anniversary
    MilestoneKind.NewHire -> R.string.core_designsystem_milestone_new_hire
    MilestoneKind.Multiple -> R.string.core_designsystem_milestone_multiple
}

private fun MilestoneKind.ringColor(colors: WaiveraColors): Color = when (this) {
    MilestoneKind.Birthday -> colors.milestoneDot
    MilestoneKind.WorkAnniversary -> colors.onAnniversaryContainer
    MilestoneKind.NewHire -> colors.onNewHireContainer
    MilestoneKind.Multiple -> colors.onMultipleContainer
}

@WaiveraPreviews
@Composable
private fun WaiveraAvatarPreview() {
    WaiveraTheme {
        Row(
            Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            WaiveraAvatar(name = "Alex Rivera", presence = PresenceStatus.Online)
            WaiveraAvatar(name = "Priya Raman", milestone = MilestoneKind.Birthday, rounded = false)
            WaiveraAvatar(name = "", size = WaiveraSizes.AvatarLarge)
            WaiveraAvatar(name = "Sam", size = WaiveraSizes.AvatarSmall, presence = PresenceStatus.Away)
        }
    }
}
