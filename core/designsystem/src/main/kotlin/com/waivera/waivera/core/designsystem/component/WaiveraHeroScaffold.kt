package com.waivera.waivera.core.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The top of a tab screen in concept A: the full-bleed plum hero (drawn behind the status bar)
 * holding the hero bar and [content], ending in the first band of the rounded sheet that the rest
 * of the screen sits on. Put it first in the screen's lazy list and give the list the page
 * colour, so the sheet continues below it as the person scrolls.
 *
 * While it is on screen the status bar icons are light, so they read on the plum in both themes.
 */
@Composable
fun WaiveraHeroSection(
    modifier: Modifier = Modifier,
    trail: WaiveraHeroTrail = WaiveraHeroTrail.Tab,
    topBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    LightStatusBarIcons()
    WaiveraHeroBackdrop(modifier = modifier.fillMaxWidth(), trail = trail) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(HeroInsets),
            ) {
                topBar()
                content()
            }
            Spacer(Modifier.height(WaiveraHero.TabSheetLip))
        }
        // The sheet's first band: its curve rises over the hero's foot.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WaiveraHero.TabSheetLip)
                .background(WaiveraTheme.colors.background, WaiveraHero.TabSheetShape),
        )
    }
}

/**
 * The hero bar: the mark, the screen title and the signed-in person's avatar on the glass. The
 * title is the screen's heading and wraps to two lines at large font instead of clipping.
 */
@Composable
fun WaiveraHeroTopBar(title: String, modifier: Modifier = Modifier, account: TopBarAccount? = null) {
    val hero = WaiveraHero.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WaiveraHero.BarHeight)
            .padding(start = WaiveraSpacing.Medium + WaiveraSpacing.XxSmall, end = WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraHeroLogo(height = WaiveraHero.BarMark, variant = WaiveraLogoVariant.Mark)
        Text(
            text = title,
            style = WaiveraTextStyles.HeroBarTitle,
            color = hero.onHeader,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (account != null) {
            val label = stringResource(R.string.core_designsystem_account_menu)
            WaiveraPressable(
                onClick = account.onClick,
                modifier = Modifier.size(WaiveraSizes.IconButton),
                onClickLabel = label,
                role = Role.Button,
                shape = WaiveraShapes.avatar(WaiveraHero.BarAvatar),
            ) {
                WaiveraAvatar(
                    name = account.name,
                    painter = account.painter,
                    imageModel = account.imageModel,
                    size = WaiveraHero.BarAvatar,
                    contentDescription = label,
                    colors = WaiveraAvatarColors.onHero(),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * The glass summary card on the hero ("5 of 9 handled"): a faint white fill and hairline over the
 * plum. Its content uses the hero inks ([WaiveraHero.colors]).
 */
@Composable
fun WaiveraHeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val hero = WaiveraHero.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(hero.card, WaiveraShapes.HeroCard)
            .border(BorderStroke(WaiveraSpacing.Hairline, hero.cardBorder), WaiveraShapes.HeroCard)
            .padding(WaiveraSpacing.Medium + WaiveraSpacing.XxSmall),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
        content = content,
    )
}

/**
 * Light status bar icons while the caller is composed, restored to the theme's when it leaves.
 * The app sets dark icons in the light theme, which vanish on the plum hero. In previews and
 * screenshot tests there is no activity window, so it does nothing.
 */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    val dark = WaiveraTheme.colors.isDark
    if (LocalInspectionMode.current) return
    DisposableEffect(view, dark) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !dark }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The hero pads for the status bar and a side cutout; the bottom belongs to the sheet. */
private val HeroInsets: WindowInsets
    @Composable
    get() = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@WaiveraPreviews
@Composable
private fun WaiveraHeroSectionPreview() {
    WaiveraTheme {
        Column(modifier = Modifier.background(WaiveraTheme.colors.background)) {
            WaiveraHeroSection(
                topBar = {
                    WaiveraHeroTopBar(title = "Today", account = TopBarAccount(name = "Alex Rivera", onClick = {}))
                },
            ) {
                WaiveraHeroCard(modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium)) {
                    Text(
                        text = "4 things need you",
                        style = WaiveraTextStyles.HeroTitle,
                        color = WaiveraHero.colors.onHeader,
                    )
                }
            }
        }
    }
}
