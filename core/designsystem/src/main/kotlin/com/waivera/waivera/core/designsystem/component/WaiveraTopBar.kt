package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.PressDepth
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/** An icon action in the top bar. */
@Immutable
data class TopBarAction(
    @DrawableRes val icon: Int,
    val contentDescription: String,
    val onClick: () -> Unit,
    val badgeCount: Int = 0,
)

/**
 * The signed-in person's avatar at the end of the tab top bars; opens the account menu.
 * [imageModel] is a Coil model, as for [WaiveraAvatar]. Tabs usually build it with
 * [rememberTopBarAccount].
 */
@Immutable
data class TopBarAccount(
    val name: String,
    val onClick: () -> Unit,
    val painter: Painter? = null,
    val imageModel: Any? = null,
)

/** The signed-in person as the app shell knows them; [imageModel] is a Coil model for their picture. */
@Immutable
data class SignedInAccount(val name: String, val imageModel: Any? = null)

/** Provided once by the app shell, so every tab's top bar shows the same person without plumbing. */
val LocalSignedInAccount = compositionLocalOf<SignedInAccount?> { null }

/**
 * The top bar account for the signed-in person from [LocalSignedInAccount], with [fallbackName]
 * (the screen's own copy of the person's real name) while the shell has no name yet. Pass a
 * blank name when it is unknown: the avatar then shows the generic person icon, never initials
 * made from placeholder copy, so identity looks the same on every tab.
 */
@Composable
fun rememberTopBarAccount(onClick: () -> Unit, fallbackName: String = "", painter: Painter? = null): TopBarAccount {
    val account = LocalSignedInAccount.current
    return remember(account, onClick, fallbackName, painter) {
        TopBarAccount(
            name = account?.name?.takeIf(String::isNotBlank) ?: fallbackName,
            onClick = onClick,
            painter = painter,
            imageModel = account?.imageModel,
        )
    }
}

/**
 * Screen top bar: concept A's compact plum hero. The plum gradient with its glows and the faint
 * tab trail runs behind the status bar, the bar row sits on it in white, and the bar ends in the
 * first band of the rounded sheet ([sheetColor], the colour of what sits below it), so every
 * screen opens the way Today does. A tab's root screen (no [onBack]) leads with the mark.
 *
 * It owns the status bar and horizontal cutout insets for the screen, so screens using it must
 * not pad for the status bar again. While it shows, the status bar icons are light. Titles wrap
 * to two lines at large font scales instead of clipping, and the sheet band narrows there so the
 * header stays compact.
 *
 * [heroContent] goes on the plum under the bar row, for a screen that has a hero of its own (a
 * person's avatar and name). It uses the hero inks ([WaiveraHero.colors]).
 */
// One layout with optional back, subtitle, actions, account and hero slots; splitting hides the shared row.
@Suppress("LongMethod")
@Composable
fun WaiveraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: List<TopBarAction> = emptyList(),
    account: TopBarAccount? = null,
    sheetColor: Color = WaiveraTheme.colors.background,
    heroContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val hero = WaiveraHero.colors
    val lip = if (isLargeFontScale()) WaiveraHero.BarSheetLipCompact else tabSheetLip()
    LightStatusBarIcons()
    WaiveraHeroBackdrop(modifier = modifier.fillMaxWidth(), trail = WaiveraHeroTrail.Tab) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(TopBarInsets),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WaiveraSizes.TopBarMinHeight)
                        .padding(horizontal = WaiveraSpacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        WaiveraIconButton(
                            icon = WaiveraIcons.Back,
                            contentDescription = stringResource(R.string.core_designsystem_go_back),
                            onClick = onBack,
                            tint = hero.onHeader,
                        )
                    } else {
                        Spacer(Modifier.width(WaiveraSpacing.MediumSmall))
                        WaiveraHeroLogo(height = WaiveraHero.BarMark, variant = WaiveraLogoVariant.Mark)
                        Spacer(Modifier.width(WaiveraSpacing.MediumSmall))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = WaiveraSpacing.Small),
                    ) {
                        // Hyphenated names wrap after a hyphen, never mid-word (UI5-05).
                        Text(
                            text = title.withBreaksAfterHyphens(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = hero.onHeader,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = hero.onHeaderSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TopBarTrailing(actions = actions, account = account)
                }
                heroContent?.invoke(this)
            }
            Spacer(Modifier.height(lip))
        }
        // The sheet's first band: its curve rises over the hero's foot.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(lip)
                .background(sheetColor, WaiveraHero.TabSheetShape),
        )
    }
}

/** The bar's icon actions (white on the plum, each with its count) and the account avatar. */
@Composable
private fun TopBarTrailing(actions: List<TopBarAction>, account: TopBarAccount?) {
    val hero = WaiveraHero.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            Box {
                WaiveraIconButton(
                    icon = action.icon,
                    contentDescription = action.contentDescription,
                    onClick = action.onClick,
                    tint = hero.onHeader,
                )
                // Always composed: the badge pops in and out with the count (hidden at 0).
                WaiveraCountBadge(count = action.badgeCount, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        if (account != null) {
            val label = stringResource(R.string.core_designsystem_account_menu)
            WaiveraPressable(
                depth = PressDepth.Compact,
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

private val TopBarInsets: WindowInsets
    @Composable
    get() = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@WaiveraPreviews
@Composable
private fun WaiveraTopBarPreview() {
    WaiveraTheme {
        Column {
            WaiveraTopBar(
                title = "Today",
                subtitle = "Tuesday, October 27",
                account = TopBarAccount(name = "Alex Rivera", onClick = {}),
            )
            WaiveraTopBar(
                title = "North team check-in with a long conversation name",
                onBack = {},
                actions = listOf(TopBarAction(icon = WaiveraIcons.Search, contentDescription = "Search", onClick = {})),
            )
        }
    }
}
