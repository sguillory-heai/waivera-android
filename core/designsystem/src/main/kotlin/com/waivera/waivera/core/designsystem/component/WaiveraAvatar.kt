package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
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
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

enum class MilestoneKind { Birthday, WorkAnniversary, NewHire, Multiple }

/**
 * Avatar with an initials fallback (person icon when there are no initials). Every avatar is the
 * same soft rounded square ([WaiveraShapes.avatar]) with no border, ring or outline: the owner's rule. The fallback's [WaiveraColors.avatarFill] alone sets
 * it apart from every surface and the brand wash. There is no online or presence marker: the
 * product does not track presence. A milestone is announced in the content description; its
 * visible marker is the caller's one badge or emoji tile.
 *
 * @param painter an already loaded image; wins over [imageModel].
 * @param imageModel a Coil model (for example `AvatarModel(userId)` from core:data) loaded through
 *   the app's singleton image loader, which carries the auth-aware avatar fetcher. The fallback
 *   shows until it loads and stays when there is no picture.
 * @param milestone announced only; it draws nothing on the avatar.
 * @param contentDescription null when the avatar sits next to the person's name and is decorative.
 * @param colors the fallback's fill and initials ink; the hero bar passes its glass and white.
 */
@Composable
fun WaiveraAvatar(
    name: String,
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageModel: Any? = null,
    size: Dp = WaiveraSizes.AvatarMedium,
    milestone: MilestoneKind? = null,
    contentDescription: String? = name,
    colors: WaiveraAvatarColors = WaiveraAvatarColors.default(),
) {
    val shape = WaiveraShapes.avatar(size)
    val initials = remember(name) { initialsFor(name) }
    val milestoneLabel = milestone?.let { stringResource(it.labelRes()) }
    val description = contentDescription?.let { listOfNotNull(it, milestoneLabel).joinToString(", ") }
    val semanticsModifier = if (description != null) {
        Modifier.semantics { this.contentDescription = description }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(semanticsModifier)
            .size(size)
            .clip(shape)
            .background(colors.fill),
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
                // Line height pinned with the size so 200% font cannot push the initials off centre.
                Text(
                    text = initials,
                    color = colors.content,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            else -> Icon(
                painter = painterResource(WaiveraIcons.Person),
                contentDescription = null,
                tint = colors.content,
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
}

/** The fallback avatar's fill and its initials or person glyph. */
@Immutable
data class WaiveraAvatarColors(val fill: Color, val content: Color) {
    companion object {
        /** The page avatar: [WaiveraColors.avatarFill] with the soft accent ink. */
        @Composable
        @ReadOnlyComposable
        fun default(): WaiveraAvatarColors =
            WaiveraTheme.colors.let { WaiveraAvatarColors(fill = it.avatarFill, content = it.onAccentSoft) }

        /** On the plum hero: the hero's glass with white initials. */
        @Composable
        @ReadOnlyComposable
        fun onHero(): WaiveraAvatarColors =
            WaiveraHero.colors.let { WaiveraAvatarColors(fill = it.accountFill, content = it.onHeader) }
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

private fun MilestoneKind.labelRes(): Int = when (this) {
    MilestoneKind.Birthday -> R.string.core_designsystem_milestone_birthday
    MilestoneKind.WorkAnniversary -> R.string.core_designsystem_milestone_anniversary
    MilestoneKind.NewHire -> R.string.core_designsystem_milestone_new_hire
    MilestoneKind.Multiple -> R.string.core_designsystem_milestone_multiple
}

@WaiveraPreviews
@Composable
private fun WaiveraAvatarPreview() {
    WaiveraTheme {
        Row(
            Modifier.padding(WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        ) {
            WaiveraAvatar(name = "Alex Rivera")
            WaiveraAvatar(name = "Priya Raman", milestone = MilestoneKind.Birthday)
            WaiveraAvatar(name = "", size = WaiveraSizes.AvatarLarge)
            WaiveraAvatar(name = "Sam", size = WaiveraSizes.AvatarSmall)
        }
    }
}
