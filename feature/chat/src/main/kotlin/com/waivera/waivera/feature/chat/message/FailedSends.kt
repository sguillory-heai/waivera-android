package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.BannerTone
import com.waivera.waivera.core.designsystem.component.WaiveraBanner
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.feature.chat.R

/** [SendErrorUi.of] against the live list, recomputed only when visibility actually changes. */
@Composable
fun rememberSendErrorUi(
    sendError: SendErrorKind?,
    failedIndices: List<Int>,
    listState: LazyListState,
    connectionBannerShowing: Boolean = false,
): SendErrorUi {
    val ui by remember(sendError, failedIndices, listState, connectionBannerShowing) {
        derivedStateOf {
            SendErrorUi.of(
                sendError = sendError,
                failedIndices = failedIndices,
                visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index },
                connectionBannerShowing = connectionBannerShowing,
            )
        }
    }
    return ui
}

/** The one send-failure banner a screen shows, per [SendErrorUi]. */
@Composable
fun SendErrorSignal(
    ui: SendErrorUi,
    isReply: Boolean,
    onShow: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (ui) {
        SendErrorUi.None -> Unit
        is SendErrorUi.Banner ->
            SendErrorBanner(kind = ui.kind, isReply = isReply, onDismiss = onDismiss, modifier = modifier)
        is SendErrorUi.Offscreen -> WaiveraBanner(
            title = pluralStringResource(
                if (isReply) R.plurals.feature_chat_replies_not_sent else R.plurals.feature_chat_messages_not_sent,
                ui.count,
                ui.count,
            ),
            message = stringResource(R.string.feature_chat_not_sent_offscreen),
            tone = BannerTone.Error,
            actionLabel = stringResource(R.string.feature_chat_not_sent_show),
            onAction = { onShow(ui.index) },
            actionIcon = WaiveraIcons.ArrowUp,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}
