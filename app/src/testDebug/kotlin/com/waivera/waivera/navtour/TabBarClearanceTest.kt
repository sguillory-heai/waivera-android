package com.waivera.waivera.navtour

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import com.waivera.waivera.feature.today.R as TodayR
import com.waivera.waivera.navigation.ChatListRoute
import com.waivera.waivera.navigation.ScheduleRoute
import com.waivera.waivera.navigation.TodayRoute
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab bar floats over full-height screens, so each tab root pads its own bottom by the bar's
 * measured height. When an entry kept the height it was built with (0), lists ran under the bar and
 * Today's "Accepted" snackbar was drawn behind it.
 */
@HiltAndroidTest
class TabBarClearanceTest : NavTourHarness() {

    @Test
    fun everyTabRootsScrollingContentEndsAboveTheBar() {
        launch()
        expectStack(TodayRoute)
        assertScrollingContentAboveBar("Today")

        clickTab("Chat")
        expectStack(TodayRoute, ChatListRoute)
        assertScrollingContentAboveBar("Chat")

        clickTab("Schedule")
        expectStack(TodayRoute, ScheduleRoute)
        assertScrollingContentAboveBar("Schedule")
    }

    @Test
    fun todaysSnackbarShowsAboveTheBar() {
        launch()
        val showAll = textOrDescription(stringOf(TodayR.string.feature_today_brand_show_all))
        if (exists(showAll and clickable())) click(showAll)
        click(hasContentDescription(ACCEPT_INVITE))
        val accepted = stringOf(TodayR.string.feature_today_invitation_accepted)
        expectScreen(accepted)
        val snackbar = node(textOrDescription(accepted)).fetchSemanticsNode().boundsInRoot
        assertTrue("the snackbar $snackbar is behind the bar ${barBounds()}", snackbar.bottom <= barBounds().top)
    }

    private fun assertScrollingContentAboveBar(tabRoot: String) {
        settle()
        val bar = barBounds()
        val scrolling = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("$tabRoot has no scrolling content to check", scrolling.isNotEmpty())
        scrolling.forEach { bounds ->
            assertTrue("$tabRoot content $bounds runs under the bar $bar", bounds.bottom <= bar.top)
        }
    }

    /** The smallest node holding all three tabs: the bar itself, wherever it draws its items. */
    private fun barBounds(): Rect {
        val holdsEveryTab = TABS.map { hasAnyDescendant(tab(it)) }.reduce { all, next -> all and next }
        return compose.onAllNodes(holdsEveryTab)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .minBy { it.width * it.height }
    }

    private companion object {
        const val ACCEPT_INVITE = "Accept invitation to Care plan review"
        val TABS = listOf("Today", "Chat", "Schedule")
    }
}
