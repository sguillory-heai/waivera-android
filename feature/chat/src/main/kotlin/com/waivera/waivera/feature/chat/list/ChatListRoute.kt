package com.waivera.waivera.feature.chat.list

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.newchat.NewChatSheet
import com.waivera.waivera.feature.chat.newchat.NewChatViewModel
import kotlinx.coroutines.launch

/** Screen-level composable: binds [ChatListViewModel] and [NewChatViewModel] to the stateless content. */
@Composable
fun ChatListRoute(
    viewModel: ChatListViewModel,
    newChatViewModel: NewChatViewModel,
    onAccountClick: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onOpenPerson: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val avatars by viewModel.avatars.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current
    val openConversation by rememberUpdatedState(onOpenConversation)
    val openPerson by rememberUpdatedState(onOpenPerson)
    var actionsFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNewChat by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ChatListEffect.OpenConversation -> openConversation(effect.conversationId)
                is ChatListEffect.OpenPerson -> openPerson(effect.userId)
                is ChatListEffect.OpenUrl -> try {
                    uriHandler.openUri(effect.url)
                } catch (_: IllegalArgumentException) {
                    // No browser can open the lobby.
                    val message = resources.getString(R.string.feature_chat_list_call_no_browser)
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
                is ChatListEffect.ShowMessage -> {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(effect.messageRes),
                            actionLabel = effect.retry?.let { resources.getString(R.string.feature_chat_list_retry) },
                        )
                        if (result == SnackbarResult.ActionPerformed) effect.retry?.let(viewModel::onRetry)
                    }
                }
            }
        }
    }

    ChatListContent(
        state = state,
        avatars = avatars,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        actions = ChatListActions(
            onAccountClick = onAccountClick,
            onNewChat = { showNewChat = true },
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onConversationClick = viewModel::onConversationClick,
            onShowActions = { actionsFor = it },
            onJoinCall = viewModel::onJoinCall,
            onMarkRead = viewModel::onMarkRead,
            onOpenProfile = viewModel::onOpenProfile,
            onRefresh = viewModel::onRefresh,
            onRetryLoad = viewModel::onRefresh,
            onRetryConnection = viewModel::onRetryConnection,
        ),
    )

    val actionRow = actionsFor?.let { id -> state.rows.firstOrNull { it.id == id } }
    if (actionRow != null) {
        // No sheet title: the content's header row names the conversation with its avatar (UI3-17).
        WaiveraBottomSheet(onDismissRequest = { actionsFor = null }) {
            RowActionsContent(
                row = actionRow,
                avatar = (actionRow.leading as? RowLeading.Direct)?.userId?.let { avatars[it] },
                onOpen = {
                    actionsFor = null
                    viewModel.onConversationClick(actionRow.id)
                },
                onMarkRead = {
                    actionsFor = null
                    viewModel.onMarkRead(actionRow.id)
                },
                onJoinCall = {
                    actionsFor = null
                    viewModel.onJoinCall(actionRow.id)
                },
                onOpenProfile = {
                    actionsFor = null
                    viewModel.onOpenProfile(it)
                },
            )
        }
    }

    if (showNewChat) {
        NewChatSheet(
            viewModel = newChatViewModel,
            onDismiss = {
                showNewChat = false
                newChatViewModel.onDismissed()
            },
            onOpenConversation = { id ->
                showNewChat = false
                viewModel.onConversationClick(id)
            },
        )
    }
}
