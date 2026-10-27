package com.waivera.waivera.navigation

import com.waivera.waivera.core.notifications.deeplink.DeepLinkTarget
import kotlinx.serialization.Serializable

/**
 * The shell's navigation model: one back stack per tab and the selected tab. It is a pure value
 * so every rule (tab back behaviour, deep links, duplicate pushes) is unit tested without
 * Compose. The stack shown to Navigation 3 is [visibleBackStack].
 */
@Serializable
data class NavState(
    val selected: TopLevelTab = TopLevelTab.Today,
    val stacks: Map<TopLevelTab, List<AppRoute>> = TopLevelTab.entries.associateWith { listOf(it.root) },
) {
    fun stack(tab: TopLevelTab): List<AppRoute> = stacks[tab]?.takeIf(List<AppRoute>::isNotEmpty) ?: listOf(tab.root)

    /**
     * The Today stack sits under every other tab, so back from the root of Chat or Schedule
     * lands on Today (with a predictive back preview) and back from Today's root leaves the app.
     */
    val visibleBackStack: List<AppRoute>
        get() = if (selected == TopLevelTab.Today) {
            stack(TopLevelTab.Today)
        } else {
            stack(TopLevelTab.Today) + stack(selected)
        }

    /**
     * [visibleBackStack] with a key per entry for Navigation 3. The same route can sit in two
     * stacks (or twice in one), so keys carry the tab and position as well as the route.
     */
    val visibleEntries: List<ShellEntry>
        get() {
            val today = entriesOf(TopLevelTab.Today)
            return if (selected == TopLevelTab.Today) today else today + entriesOf(selected)
        }

    val top: AppRoute get() = stack(selected).last()

    /** Selecting the current tab again pops it to its root, as platform tab bars do. */
    fun select(tab: TopLevelTab): NavState = if (tab == selected) {
        withStack(tab, listOf(tab.root))
    } else {
        copy(selected = tab)
    }

    /** Pushes [route] on the selected tab, ignoring a push of the route already on top. */
    fun push(route: AppRoute): NavState {
        if (route == top) return this
        if (route.isTabRoot) return select(TopLevelTab.entries.first { it.root == route })
        return withStack(selected, stack(selected) + route)
    }

    /** Pops the selected tab, or returns to Today from another tab's root. At Today's root it is a no-op. */
    fun back(): NavState {
        val current = stack(selected)
        return when {
            current.size > 1 -> withStack(selected, current.dropLast(1))
            selected != TopLevelTab.Today -> copy(selected = TopLevelTab.Today)
            else -> this
        }
    }

    /** True when [back] would leave the app instead of changing screens. */
    val isAtExit: Boolean get() = selected == TopLevelTab.Today && stack(TopLevelTab.Today).size == 1

    /**
     * Opens a deep link (push tap or `waivera://` link). The target's tab is selected and given a
     * fresh stack that leads to the target, so back always walks up through the parent screens.
     * When the target is already on top (for example a second push for the open conversation),
     * nothing changes, so the screen keeps its scroll position and draft.
     */
    fun open(target: DeepLinkTarget): NavState {
        val (tab, routes) = DeepLinkRouting.stackFor(target)
        val destination = routes.last()
        if (selected == tab && top.contentKey == destination.contentKey) {
            val moveHighlight = destination.highlightedMessageId != null && destination != top
            return if (moveHighlight) withStack(tab, stack(tab).dropLast(1) + destination) else this
        }
        return withStack(tab, routes).copy(selected = tab)
    }

    private fun entriesOf(tab: TopLevelTab): List<ShellEntry> =
        stack(tab).mapIndexed { index, route -> ShellEntry(tab, index, route) }

    private fun withStack(tab: TopLevelTab, routes: List<AppRoute>): NavState = copy(stacks = stacks + (tab to routes))
}

/** One entry on the visible stack. [key] identifies its saved state and ViewModels. */
data class ShellEntry(val tab: TopLevelTab, val index: Int, val route: AppRoute) {
    val key: String get() = "${tab.name}/$index/${route.contentKey}"
}
