package com.waivera.waivera.feature.chat.message

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.designsystem.component.WaiveraBottomSheet
import com.waivera.waivera.core.designsystem.component.WaiveraConfirmationSheet
import com.waivera.waivera.core.designsystem.component.showUndo
import com.waivera.waivera.core.designsystem.feedback.rememberHaptics
import com.waivera.waivera.feature.chat.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/** Hilt access for composables: the image pieces are app singletons, not ViewModel state. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChatImagesEntryPoint {
    fun imageLoader(): CoilChatImageLoader

    fun attachmentModels(): AttachmentModels

    fun avatars(): AvatarImages
}

/** The real image environment for a routed screen (previews and screenshots use the empty default). */
@Composable
fun rememberChatImageEnvironment(): ChatImageEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val entry = EntryPointAccessors.fromApplication(context, ChatImagesEntryPoint::class.java)
        val models = entry.attachmentModels()
        ChatImageEnvironment(loader = entry.imageLoader(), attachmentModel = models::modelFor, avatars = entry.avatars())
    }
}

/** Screen-side handling of [ChatEffect]s the screen itself does not need to know about. */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
@Composable
fun ChatEffectsHandler(
    effects: Flow<ChatEffect>,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    onSetDraft: (String) -> Unit,
    onOpenThread: (Long) -> Unit,
    onPickSaveLocation: (fileName: String) -> Unit,
    onUndoBookmark: (Long) -> Unit,
    onFocusComposer: () -> Unit,
    scrollToLatest: suspend () -> Unit,
    indexOfKey: (String) -> Int?,
) {
    val context = LocalContext.current
    // The app shell provides a Custom Tabs handler, so links stay in the app.
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val latestOpenThread = rememberUpdatedState(onOpenThread)
    val latestSetDraft = rememberUpdatedState(onSetDraft)
    val latestPickSave = rememberUpdatedState(onPickSaveLocation)
    val latestUndo = rememberUpdatedState(onUndoBookmark)
    val latestScroll = rememberUpdatedState(scrollToLatest)
    val latestFocus = rememberUpdatedState(onFocusComposer)
    val latestIndex = rememberUpdatedState(indexOfKey)

    fun showSnackbar(effect: ChatEffect.Snackbar) {
        val title = effect.argument?.let { resources.getString(effect.message, it) } ?: resources.getString(effect.message)
        val text = effect.detail?.let { resources.getString(R.string.feature_chat_snackbar_with_detail, title, resources.getString(it)) }
            ?: title
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val undo = effect.undoBookmarkOf
            if (undo != null) {
                snackbarHostState.showUndo(text, resources.getString(R.string.feature_chat_undo)) { latestUndo.value(undo) }
            } else {
                snackbarHostState.showSnackbar(text)
            }
        }
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ChatEffect.CopyText -> scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(resources.getString(R.string.feature_chat_messages_list), effect.text)))
                }
                is ChatEffect.OpenUrl -> {
                    val opened = try {
                        uriHandler.openUri(effect.url)
                        true
                    } catch (_: IllegalArgumentException) {
                        false
                    }
                    if (!opened) showSnackbar(ChatEffect.Snackbar(R.string.feature_chat_link_failed))
                }
                is ChatEffect.OpenThread -> latestOpenThread.value(effect.rootMessageId)
                is ChatEffect.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = effect.mimeType
                        putExtra(Intent.EXTRA_STREAM, effect.uri.toUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(send, resources.getString(R.string.feature_chat_share_title, effect.fileName))
                    try {
                        context.startActivity(chooser)
                    } catch (_: ActivityNotFoundException) {
                        showSnackbar(ChatEffect.Snackbar(R.string.feature_chat_file_open_failed))
                    }
                }
                is ChatEffect.PickSaveLocation -> latestPickSave.value(effect.fileName)
                is ChatEffect.SetDraft -> latestSetDraft.value(effect.text)
                ChatEffect.FocusComposer -> latestFocus.value()
                ChatEffect.ScrollToLatest -> scope.launch { latestScroll.value() }
                is ChatEffect.ScrollTo -> latestIndex.value(effect.key)?.let { index ->
                    scope.launch { listState.animateScrollToItem(index) }
                }
                is ChatEffect.Haptic -> haptics.perform(effect.moment)
                is ChatEffect.Snackbar -> showSnackbar(effect)
            }
        }
    }
}

/** Hides the keyboard when the person starts dragging the list (03 §15). */
@Composable
fun HideKeyboardOnDrag(listState: LazyListState) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState, keyboard) {
        listState.interactionSource.interactions.filterIsInstance<DragInteraction.Start>().collect { keyboard?.hide() }
    }
}

/** Everything the sheets can do; both chat screens pass their ViewModel's functions. */
@Immutable
class ChatSheetCallbacks(
    val onDismiss: () -> Unit,
    val onReact: (messageId: Long, emoji: String) -> Unit,
    val onCopy: (Message) -> Unit,
    val onReply: (Message) -> Unit,
    val onEdit: (Message) -> Unit,
    val onBookmark: (messageId: Long) -> Unit,
    val onRequestDelete: (messageId: Long) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onSave: (Message) -> Unit,
    val onShare: (Message) -> Unit,
    val onLink: (String) -> Unit,
) {
    companion object {
        val None = ChatSheetCallbacks({}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** The one sheet or dialog open over a message list. */
@Composable
fun ChatSheetHost(
    sheet: ChatSheet?,
    messageFor: (Long) -> Message?,
    myUserId: String?,
    nameFor: (String) -> String,
    callbacks: ChatSheetCallbacks,
) {
    when (sheet) {
        null -> Unit
        is ChatSheet.Actions -> WaiveraBottomSheet(onDismissRequest = callbacks.onDismiss) {
            val message = sheet.model.message
            MessageActionsContent(
                model = sheet.model,
                onReact = { emoji -> callbacks.onReact(message.id, emoji) },
                onCopy = { callbacks.onCopy(message) },
                onReply = { callbacks.onReply(message) },
                onEdit = { callbacks.onEdit(message) },
                onBookmark = { callbacks.onBookmark(message.id) },
                onDelete = { callbacks.onRequestDelete(message.id) },
                onClose = callbacks.onDismiss,
                onLink = callbacks.onLink,
            )
        }
        is ChatSheet.ReactionDetails -> {
            val reactions = messageFor(sheet.messageId)?.let { Reactions.summarize(it.reactions, myUserId) }.orEmpty()
            if (reactions.isEmpty()) {
                // The last reaction was removed while the sheet was open.
                LaunchedEffect(sheet) { callbacks.onDismiss() }
            } else {
                WaiveraBottomSheet(onDismissRequest = callbacks.onDismiss) {
                    ReactionDetailsContent(
                        reactions = reactions,
                        initialEmoji = sheet.initialEmoji,
                        nameFor = nameFor,
                        onClose = callbacks.onDismiss,
                    )
                }
            }
        }
        is ChatSheet.FileActions -> {
            val message = messageFor(sheet.messageId)
            if (message == null) {
                LaunchedEffect(sheet) { callbacks.onDismiss() }
            } else {
                WaiveraBottomSheet(onDismissRequest = callbacks.onDismiss) {
                    FileActionsContent(
                        fileName = sheet.fileName,
                        busy = sheet.busy,
                        onSave = { callbacks.onSave(message) },
                        onShare = { callbacks.onShare(message) },
                        onClose = callbacks.onDismiss,
                    )
                }
            }
        }
        is ChatSheet.ImagePreview -> {
            val message = messageFor(sheet.messageId)
            if (message == null) {
                LaunchedEffect(sheet) { callbacks.onDismiss() }
            } else {
                ImagePreviewDialog(message = message, onClose = callbacks.onDismiss)
            }
        }
        is ChatSheet.ConfirmDelete -> WaiveraConfirmationSheet(
            title = stringResource(R.string.feature_chat_delete_title),
            message = stringResource(R.string.feature_chat_delete_message),
            confirmLabel = stringResource(R.string.feature_chat_delete_confirm),
            onConfirm = callbacks.onConfirmDelete,
            onDismiss = callbacks.onDismiss,
            destructive = true,
            confirmInProgress = sheet.inProgress,
        )
    }
}

/** Names for rows, reactions and typing: the message's own author first, then the directory. */
@Composable
fun authorNameFor(message: Message, names: Map<String, String>, botName: String?): String {
    val unknown = stringResource(R.string.feature_chat_unknown_author)
    return message.author?.name?.takeIf(String::isNotBlank)
        ?: message.authorId?.let(names::get)
        ?: botName?.takeIf { message.botId != null || message.authorId == null }
        ?: unknown
}
