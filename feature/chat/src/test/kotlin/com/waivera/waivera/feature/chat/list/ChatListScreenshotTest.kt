package com.waivera.waivera.feature.chat.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.designsystem.component.WaiveraSheetFrame
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.screenshottesting.ScreenshotSdk
import com.waivera.waivera.core.screenshottesting.captureScreenshots
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.newchat.NewChatPreviewData
import com.waivera.waivera.feature.chat.newchat.NewChatSheetContent
import com.waivera.waivera.feature.chat.newchat.NewChatUiState
import com.waivera.waivera.feature.chat.newchat.createErrorRes
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every inbox and new chat state across the shared matrix (light/dark × small/large phone ×
 * 100%/200% font). Names follow `<screen>_<state>`. The populated inbox is built by the real
 * state holder from the demo graph, so it shows what the demo app shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk.VALUE])
class ChatListScreenshotTest {
    private val sample = ChatListPreviewData.state

    private fun demoState(): ChatListUiState {
        var state = ChatListUiState()
        runTest(UnconfinedTestDispatcher()) {
            val graph = DemoGraph(backgroundScope)
            val holder = ChatListStateHolder(
                scope = backgroundScope,
                conversations = graph.conversations,
                session = graph.session,
                realtime = graph.realtimeStatus,
                text = EnglishText,
                clock = graph.backend.clock,
                ticker = flowOf(graph.backend.now()),
                locale = Locale.US,
            )
            state = holder.state.value
        }
        return state
    }

    private fun list(
        name: String,
        state: ChatListUiState,
        connectingPillDelayMillis: Long = CONNECTING_PILL_DELAY_MILLIS,
    ) = captureScreenshots("chat_list_$name") {
        ChatListContent(
            state = state,
            avatars = emptyMap(),
            actions = ChatListActions(),
            skeletonDelayMillis = 0,
            connectingPillDelayMillis = connectingPillDelayMillis,
        )
    }

    @Test
    fun populated() = list("populated", demoState())

    @Test
    fun longContent() = list("long_content", sample)

    @Test
    fun loading() = list("loading", ChatListUiState(connection = ConnectionUi.Connecting))

    @Test
    fun empty() = list("empty", ChatListUiState(content = ChatListContent.Empty(EmptyReason.NoConversations)))

    @Test
    fun emptyUnread() = list(
        "empty_unread",
        ChatListUiState(content = ChatListContent.Empty(EmptyReason.AllCaughtUp), filter = ChatListFilter.Unread),
    )

    @Test
    fun emptyChannels() = list(
        "empty_channels",
        ChatListUiState(content = ChatListContent.Empty(EmptyReason.NoChannels), filter = ChatListFilter.Channels),
    )

    @Test
    fun noMatches() = list(
        "no_matches",
        ChatListUiState(content = ChatListContent.Empty(EmptyReason.NoMatches), query = "payroll"),
    )

    @Test
    fun error() = list(
        "error",
        ChatListUiState(
            content = ChatListContent.Error(WaiveraError.Network().messageRes),
            connection = ConnectionUi.Offline,
        ),
    )

    /** A server failure while online: the error state alone, with the server copy, no offline signal. */
    @Test
    fun serverError() = list(
        "server_error",
        ChatListUiState(
            content = ChatListContent.Error(WaiveraError.Server(WaiveraError.HTTP_SERVER_ERROR).messageRes),
            connection = ConnectionUi.Online,
        ),
    )

    /** Start-up within a normal connect: the "Connecting" pill waits, so nothing shows yet. */
    @Test
    fun connecting() = list("connecting", sample.copy(connection = ConnectionUi.Connecting))

    /** A slow start-up, past the pill's wait: the quiet "Connecting" pill above search (UI3-16). */
    @Test
    fun connectingSlow() = list(
        "connecting_slow",
        sample.copy(connection = ConnectionUi.Connecting),
        connectingPillDelayMillis = 0,
    )

    @Test
    fun reconnecting() = list("reconnecting", sample.copy(connection = ConnectionUi.Reconnecting))

    @Test
    fun offline() = list("offline", sample.copy(connection = ConnectionUi.Offline))

    @Test
    fun refreshFailed() = list("refresh_failed", sample.copy(refreshErrorRes = WaiveraError.Server(500).messageRes))

    @Test
    fun unreadFilter() = list(
        "unread_filter",
        sample.copy(filter = ChatListFilter.Unread, rows = sample.rows.filter { it.isUnread }),
    )

    @Test
    fun rowActions() = captureScreenshots("chat_list_row_actions") {
        SheetSurface {
            RowActionsContent(
                // The group with a live call, so "Join call" shows where a call really is (UI4-10).
                row = sample.rows.first { it.leading == RowLeading.Group && it.isCallLive },
                onOpen = {},
                onMarkRead = {},
                onJoinCall = {},
                onOpenProfile = {},
            )
        }
    }

    @Test
    fun newChatSelected() = newChat("selected", NewChatPreviewData.state)

    @Test
    fun newChatCreating() = newChat("creating", NewChatPreviewData.state.copy(isCreating = true))

    @Test
    fun newChatLoading() = newChat("loading", NewChatUiState())

    @Test
    fun newChatEmpty() = newChat("empty", NewChatUiState(query = "zz", isLoading = false))

    @Test
    fun newChatError() = newChat(
        "error",
        NewChatPreviewData.state.copy(errorRes = createErrorRes(WaiveraError.Conflict()), isCreating = false),
    )

    @Test
    fun newChatLoadError() = newChat(
        "load_error",
        NewChatUiState(isLoading = false, loadErrorRes = WaiveraError.Network().messageRes),
    )

    /** Inside the real sheet frame, so the title and drag handle are part of what is checked. */
    private fun newChat(name: String, state: NewChatUiState) = captureScreenshots("new_chat_$name") {
        WaiveraSheetFrame(title = stringResource(R.string.feature_chat_newchat_title)) {
            NewChatSheetContent(
                state = state,
                avatars = emptyMap(),
                onQueryChange = {},
                onToggle = {},
                onSubmit = {},
                onRetryLoad = {},
                onVisiblePeople = {},
                skeletonDelayMillis = 0,
            )
        }
    }

    @Composable
    private fun SheetSurface(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .background(WaiveraTheme.colors.surface)
                .padding(WaiveraSpacing.Gutter),
            content = content,
        )
    }
}
