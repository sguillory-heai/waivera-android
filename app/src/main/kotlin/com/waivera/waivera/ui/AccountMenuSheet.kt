package com.waivera.waivera.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.R
import com.waivera.waivera.core.designsystem.component.WaiveraAvatar
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.navigation.AppRoute
import com.waivera.waivera.navigation.BookmarksRoute
import com.waivera.waivera.navigation.PeopleRoute
import com.waivera.waivera.navigation.ProfileRoute
import com.waivera.waivera.navigation.SettingsRoute

/** Opened from the avatar in the Today, Chat and Schedule top bars. */
@Composable
fun AccountMenuSheet(
    accountName: String,
    onSelect: (AppRoute) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        WaiveraListRow(
            headline = accountName.ifBlank { stringResource(R.string.shell_account_fallback) },
            supporting = stringResource(R.string.shell_account_view_profile),
            onClick = { onSelect(ProfileRoute) },
            showChevron = true,
            leading = { WaiveraAvatar(name = accountName) },
        )
        MenuRow(R.string.shell_menu_people, WaiveraIcons.People) { onSelect(PeopleRoute) }
        MenuRow(R.string.shell_menu_bookmarks, WaiveraIcons.Bookmark) { onSelect(BookmarksRoute) }
        MenuRow(R.string.shell_menu_settings, WaiveraIcons.Settings) { onSelect(SettingsRoute) }
    }
}

@Composable
private fun MenuRow(@StringRes label: Int, @DrawableRes icon: Int, onClick: () -> Unit) {
    WaiveraListRow(
        headline = stringResource(label),
        onClick = onClick,
        showChevron = true,
        leading = { WaiveraIconTile(icon = icon) },
    )
}
