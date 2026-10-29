package com.waivera.waivera.feature.chat.members

import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The group members screen (v7, item 4) in each state across the shared matrix (light and dark,
 * small and large phone, 100% and 200%): long and unbroken names on one line, the search on a
 * long list, the row skeletons, the empty state and a failed load while offline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class GroupMembersScreenshotTest {
    private fun capture(name: String, state: GroupMembersUiState) = captureScreenshots("group_members_$name") {
        GroupMembersContent(state = state, actions = GroupMembersActions(), skeletonDelayMillis = 0)
    }

    @Test
    fun longNames() = capture("long_names", GroupMembersPreviewData.content)

    @Test
    fun withSearch() = capture("search", GroupMembersPreviewData.long)

    @Test
    fun opening() = capture(
        "opening",
        GroupMembersPreviewData.content.copy(openingChatWith = GroupMembersPreviewData.rows[3].userId),
    )

    @Test
    fun loading() = capture("loading", GroupMembersPreviewData.loading)

    @Test
    fun empty() = capture("empty", GroupMembersPreviewData.empty)

    @Test
    fun error() = capture("error", GroupMembersPreviewData.error)
}
