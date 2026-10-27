package com.waivera.waivera.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the chat screen. Owned by `:feature:chat`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun ChatListScreen(
    onAccountClick: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_chat_chat_list_title),
        message = stringResource(R.string.feature_chat_chat_list_placeholder),
        icon = WaiveraIcons.Chat,
        account = TopBarAccount(
            name = stringResource(R.string.feature_chat_account_name),
            onClick = onAccountClick,
        ),
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun ChatListScreenPreview() {
    WaiveraTheme {
        ChatListScreen(
            onAccountClick = {},
            onOpenConversation = { _ -> },
            onOpenPerson = { _ -> },
        )
    }
}
