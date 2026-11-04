package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.AvatarTiles
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

enum class MilestoneKind { Birthday, WorkAnniversary, NewHire, Multiple }

/**
 * Avatar with an initials fallback (person icon when there are no initials). Every avatar is the
 * same soft rounded square ([WaiveraShapes.avatar]) with no border or ring around it: the
 * owner's rule. Without a photo it is the person's own tile ([AvatarTiles]): their initials on
 * one of six brand gradients picked from the name, the same one their chat notifications show.
 * There is no online or presence marker: the product does not track presence. A milestone is
 * announced in the content description; its visible marker is the caller's one
 * badge or emoji tile.
 *
 * @param painter an already loaded image; wins over [imageModel].
 * @param imageModel a Coil model (for example `AvatarModel(userId)` from core:data) loaded through
 *   the app's singleton image loader, which carries the auth-aware avatar fetcher. The fallback
 *   shows until it loads and stays when there is no picture.
 * @param milestone announced only; it draws nothing on the avatar.
 * @param contentDescription null when the avatar sits next to the person's name and is decorative.
 * @param colors a flat fallback instead of the person's tile: the hero bar passes its glass and
 *   white, and lists that colour people by kind pass their own.
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
    colors: WaiveraAvatarColors? = null,
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
            .then(if (colors != null) Modifier.background(colors.fill) else Modifier.personTile(name, size)),
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
                val density = LocalDensity.current
                val fontSize = with(density) { (size * INITIALS_RATIO).toSp() }
                // Line height pinned with the size so 200% font cannot push the initials off centre.
                Text(
                    text = initials,
                    color = colors?.content ?: Color.White,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontWeight = FontWeight.Bold,
                    style = if (colors ==
                        null
                    ) {
                        tileInitialsStyle(with(density) { size.toPx() })
                    } else {
                        LocalTextStyle.current
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
            else -> Icon(
                painter = painterResource(WaiveraIcons.Person),
                contentDescription = null,
                tint = colors?.content ?: Color.White,
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

/** The person's [AvatarTiles] tile: gradient, sheen over the top and a faint inner edge. */
private fun Modifier.personTile(name: String, size: Dp): Modifier = drawWithCache {
    val (start, end) = AvatarTiles.pairFor(name)
    val gradient = Brush.linearGradient(
        listOf(Color(start), Color(end)),
        Offset.Zero,
        Offset(this.size.width, this.size.height),
    )
    val sheen = Brush.radialGradient(
        listOf(Color.White.copy(alpha = AvatarTiles.SHEEN_ALPHA), Color.Transparent),
        center = Offset(this.size.width / 2f, -this.size.height * SHEEN_CENTER),
        radius = this.size.width * SHEEN_RADIUS,
    )
    val edge = this.size.width * AvatarTiles.EDGE_FRACTION
    val corner = WaiveraShapes.avatarCornerRadius(size).toPx()
    onDrawBehind {
        drawRect(gradient)
        drawRect(sheen)
        drawRoundRect(
            color = Color.White.copy(alpha = AvatarTiles.EDGE_ALPHA),
            topLeft = Offset(edge / 2f, edge / 2f),
            size = Size(this.size.width - edge, this.size.height - edge),
            cornerRadius = CornerRadius(corner - edge / 2f),
            style = Stroke(edge),
        )
    }
}

/** The theme's text with the tile's soft shadow, scaled with the avatar as on notifications. */
@Composable
private fun tileInitialsStyle(sidePx: Float): TextStyle = LocalTextStyle.current.merge(
    TextStyle(
        shadow = Shadow(
            color = Color(AvatarTiles.SHADOW_COLOR),
            offset = Offset(0f, sidePx * AvatarTiles.SHADOW_DROP),
            blurRadius = sidePx * AvatarTiles.SHADOW_BLUR,
        ),
    ),
)

/** A flat fallback: its fill and its initials or person glyph. */
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
private const val SHEEN_CENTER = 0.15f
private const val SHEEN_RADIUS = 0.95f

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
