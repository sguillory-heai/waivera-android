package com.waivera.waivera.ui

import androidx.activity.BackEventCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.waivera.waivera.R
import com.waivera.waivera.core.data.image.AvatarModel
import com.waivera.waivera.core.designsystem.component.NavigationBarItemModel
import com.waivera.waivera.core.designsystem.component.WaiveraNavigationBar
import com.waivera.waivera.core.designsystem.component.WaiveraSnackbarHost
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.navigation.ShellEntry
import com.waivera.waivera.navigation.ShellUiState
import com.waivera.waivera.navigation.TopLevelTab
import com.waivera.waivera.navigation.isTabRoot

/**
 * The signed-in shell: the visible back stack in a [NavDisplay] and, on tab roots, the bottom
 * bar. Insets: the bar owns the navigation bar inset and screen top bars own the status bar, so
 * the shell itself pads nothing. Screens without the bar (conversation, thread, detail screens)
 * handle the navigation bar and IME insets themselves.
 */
@Composable
fun MainShell(state: ShellUiState, actions: ShellActions, modifier: Modifier = Modifier) {
    val nav = state.shell.nav
    val motion = WaiveraTheme.motion
    var accountMenuOpen by rememberSaveable { mutableStateOf(false) }
    val openAccountMenu = remember { { accountMenuOpen = true } }
    val routeActions = remember(actions) { RouteActions(actions, openAccountMenu) }
    val snackbar = remember { SnackbarHostState() }
    val unopenableLinkMessage = stringResource(R.string.shell_link_unopenable)
    LaunchedEffect(state.unopenableLink) {
        if (state.unopenableLink) {
            try {
                snackbar.showSnackbar(unopenableLinkMessage, duration = SnackbarDuration.Long)
            } finally {
                actions.onUnopenableLinkShown()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            NavDisplay(
                backStack = nav.visibleEntries,
                onBack = { actions.back() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = { motion.forwardTransition() },
                popTransitionSpec = { motion.backTransition() },
                predictivePopTransitionSpec = { swipeEdge ->
                    motion.predictiveBackTransition(swipeFromRight = swipeEdge == BackEventCompat.EDGE_RIGHT)
                },
                entryProvider = { entry: ShellEntry ->
                    NavEntry(key = entry, contentKey = entry.key) {
                        RouteContent(route = entry.route, actions = routeActions)
                    }
                },
            )
            WaiveraSnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
        if (nav.top.isTabRoot) {
            WaiveraNavigationBar(
                items = tabItems(state.unreadTotal),
                selectedKey = nav.selected.name,
                onSelect = { key -> actions.select(TopLevelTab.valueOf(key)) },
            )
        }
    }

    if (accountMenuOpen) {
        AccountMenuSheet(
            accountName = state.accountName,
            accountImage = AvatarModel.of(state.accountUserId),
            onSelect = { route ->
                accountMenuOpen = false
                actions.openFromAccountMenu(route)
            },
            onDismiss = { accountMenuOpen = false },
        )
    }
}

@Composable
private fun tabItems(unreadTotal: Int): List<NavigationBarItemModel> = listOf(
    NavigationBarItemModel(
        key = TopLevelTab.Today.name,
        label = stringResource(R.string.shell_tab_today),
        icon = WaiveraIcons.Today,
    ),
    NavigationBarItemModel(
        key = TopLevelTab.Chat.name,
        label = stringResource(R.string.shell_tab_chat),
        icon = WaiveraIcons.Chat,
        badgeCount = unreadTotal,
    ),
    NavigationBarItemModel(
        key = TopLevelTab.Schedule.name,
        label = stringResource(R.string.shell_tab_schedule),
        icon = WaiveraIcons.Calendar,
    ),
)
