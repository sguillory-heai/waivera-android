package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Which form of the logo to draw. */
enum class WaiveraLogoVariant(
    @param:DrawableRes internal val gradientLayer: Int,
    @param:DrawableRes internal val inkLayer: Int,
    internal val aspectRatio: Float,
) {
    /** The looped "W" over its two base lines. */
    Mark(R.drawable.core_designsystem_logo_mark_loop, R.drawable.core_designsystem_logo_mark_base, MARK_ASPECT),

    /** The mark followed by the "waivera" wordmark. */
    Lockup(R.drawable.core_designsystem_logo_lockup_loop, R.drawable.core_designsystem_logo_lockup_ink, LOCKUP_ASPECT),
}

/**
 * The real Waivera logo, from the web product's SVG: the looped "W" keeps its violet, pink and
 * gold gradient in both themes, and the base lines and wordmark take [WaiveraTheme.colors]'s
 * text colour so they read on the dark plum page too. Sized by [height]; the width follows the
 * logo's proportions.
 *
 * Decorative by default. Pass [contentDescription] where the logo is the only thing naming the
 * app (a hero with no "Waivera" text next to it).
 */
@Composable
fun WaiveraLogo(
    modifier: Modifier = Modifier,
    variant: WaiveraLogoVariant = WaiveraLogoVariant.Mark,
    height: Dp = WaiveraSizes.BrandMark,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .height(height)
            .aspectRatio(variant.aspectRatio)
            .then(semantics),
    ) {
        // Both layers share one viewport, so drawing them with the same scale keeps them aligned.
        Image(
            painter = painterResource(variant.gradientLayer),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(variant.inkLayer),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(WaiveraTheme.colors.text),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Viewport of the mark drawables (48.5 × 52). */
private const val MARK_ASPECT = 48.5f / 52f

/** Viewport of the lockup drawables (256 × 52). */
private const val LOCKUP_ASPECT = 256f / 52f

@WaiveraPreviews
@Composable
private fun WaiveraLogoPreview() {
    WaiveraTheme {
        Column(
            modifier = Modifier
                .background(WaiveraTheme.colors.background)
                .padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
        ) {
            WaiveraLogo()
            WaiveraLogo(variant = WaiveraLogoVariant.Lockup, height = WaiveraSizes.BrandLockup)
            WaiveraLogo(height = WaiveraSizes.Icon)
        }
    }
}
