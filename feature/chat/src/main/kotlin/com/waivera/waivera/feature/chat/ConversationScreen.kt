package com.waivera.waivera.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the conversation screen. Owned by `:feature:chat`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun ConversationScreen(
    conversationId: Long,
    highlightMessageId: Long?,
    onBack: () -> Unit,
    onOpenThread: (rootMessageId: Long) -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_chat_conversation_title),
        message = stringResource(R.string.feature_chat_conversation_placeholder) + "\n" +
            stringResource(R.string.feature_chat_conversation_reference, conversationId.toString()),
        icon = WaiveraIcons.Chat,
        onBack = onBack,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun ConversationScreenPreview() {
    WaiveraTheme {
        ConversationScreen(
            conversationId = 42L,
            highlightMessageId = null,
            onBack = {},
            onOpenThread = { _ -> },
            onOpenPerson = { _ -> },
            onOpenEvent = { _ -> },
        )
    }
}
