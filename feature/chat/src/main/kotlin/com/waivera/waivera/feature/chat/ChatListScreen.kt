package com.waivera.waivera.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.list.ChatListActions
import com.waivera.waivera.feature.chat.list.ChatListContent
import com.waivera.waivera.feature.chat.list.ChatListPreviewData
import com.waivera.waivera.feature.chat.list.ChatListRoute
import com.waivera.waivera.feature.chat.list.ChatListViewModel
import com.waivera.waivera.feature.chat.newchat.NewChatViewModel

/**
 * Entry point for the chat list (inbox). Owned by `:feature:chat`; routed by `:app`.
 * The signature is the routed contract in `docs/architecture/app-shell.md`.
 */
@Composable
fun ChatListScreen(
    onAccountClick: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatListViewModel = hiltViewModel()
    val newChatViewModel: NewChatViewModel = hiltViewModel()
    ChatListRoute(
        viewModel = viewModel,
        newChatViewModel = newChatViewModel,
        onAccountClick = onAccountClick,
        onOpenConversation = onOpenConversation,
        onOpenPerson = onOpenPerson,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun ChatListScreenPreview() {
    WaiveraTheme {
        ChatListContent(
            state = ChatListPreviewData.state,
            avatars = emptyMap(),
            actions = ChatListActions(),
        )
    }
}
