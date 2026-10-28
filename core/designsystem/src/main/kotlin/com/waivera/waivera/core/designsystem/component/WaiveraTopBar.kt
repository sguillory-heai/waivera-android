package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

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
 * Screen top bar. It owns the status bar and horizontal cutout insets for the screen, so
 * screens using it must not pad for the status bar again. Titles wrap to two lines at large
 * font scales instead of clipping.
 */
// One layout with optional back, subtitle, actions and account slots; splitting hides the shared row.
@Suppress("LongMethod")
@Composable
fun WaiveraTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: List<TopBarAction> = emptyList(),
    account: TopBarAccount? = null,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
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
                )
            } else {
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
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
                        )
                        if (action.badgeCount > 0) {
                            WaiveraCountBadge(count = action.badgeCount, modifier = Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
                if (account != null) {
                    WaiveraPressable(
                        onClick = account.onClick,
                        modifier = Modifier.size(WaiveraSizes.IconButton),
                        onClickLabel = stringResource(R.string.core_designsystem_account_menu),
                        role = Role.Button,
                        shape = WaiveraShapes.Circle,
                    ) {
                        WaiveraAvatar(
                            name = account.name,
                            painter = account.painter,
                            imageModel = account.imageModel,
                            size = WaiveraSizes.AvatarSmall,
                            contentDescription = stringResource(R.string.core_designsystem_account_menu),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }
        WaiveraDivider()
    }
}

private val TopBarInsets: WindowInsets
    @Composable
    get() = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))

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
