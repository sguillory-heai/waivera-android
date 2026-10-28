package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The full-bleed brand header: the plum-to-violet gradient with a soft pink and violet glow in
 * the top corners. It draws behind the status bar; [content] (usually [WaiveraHeroLogo]) is
 * centred in the area the caller pads for the system bars.
 */
@Composable
fun WaiveraHeroHeader(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val hero = WaiveraHero.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(hero.headerBrush)
            .drawBehind {
                val radius = size.width * WaiveraHero.GLOW_RADIUS_FRACTION
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(hero.glowPink, hero.glowPink.copy(alpha = 0f)),
                        center = Offset(x = size.width, y = 0f),
                        radius = radius,
                    ),
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(hero.glowViolet, hero.glowViolet.copy(alpha = 0f)),
                        center = Offset(x = 0f, y = size.height),
                        radius = radius,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * The real logo drawn light-on-dark for [WaiveraHeroHeader]: the looped "W" keeps its brand
 * gradient, and the base lines and wordmark take the hero's on-header colour instead of the
 * page text colour (which is near-black in light mode and would vanish on the plum).
 */
@Composable
fun WaiveraHeroLogo(
    height: Dp,
    modifier: Modifier = Modifier,
    variant: WaiveraLogoVariant = WaiveraLogoVariant.Lockup,
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
            colorFilter = ColorFilter.tint(WaiveraHero.colors.onHeader),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraHeroHeaderPreview() {
    WaiveraTheme {
        WaiveraHeroHeader(modifier = Modifier.height(WaiveraHero.HeaderHeight)) {
            WaiveraHeroLogo(height = WaiveraHero.Lockup)
        }
    }
}
