package com.waivera.waivera.feature.chat.message

import androidx.annotation.StringRes
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.designsystem.feedback.HapticMoment
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.chat.R
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off things a chat screen does for its ViewModel. */
sealed interface ChatEffect {
    data class CopyText(val text: String) : ChatEffect

    /** Already checked by [SafeLinks]. */
    data class OpenUrl(val url: String) : ChatEffect

    data class OpenThread(val rootMessageId: Long) : ChatEffect

    data class Share(val uri: String, val mimeType: String, val fileName: String) : ChatEffect

    data class PickSaveLocation(val fileName: String, val mimeType: String) : ChatEffect

    data class SetDraft(val text: String) : ChatEffect

    /** Reply from a thread's action sheet: the composer is already the reply box. */
    data object FocusComposer : ChatEffect

    data object ScrollToLatest : ChatEffect

    data class ScrollTo(val key: String) : ChatEffect

    /** Only [HapticMoment.SendFailed] is ever sent: nothing else in chat vibrates. */
    data class Haptic(val moment: HapticMoment) : ChatEffect

    /** [detail] is a client-owned error message; server text never reaches it. */
    data class Snackbar(
        @StringRes val message: Int,
        val argument: String? = null,
        @StringRes val detail: Int? = null,
        val undoBookmarkOf: Long? = null,
    ) : ChatEffect
}

/**
 * The message actions both the conversation and the thread offer (04 §1: threads get the full
 * set). Owns the open sheet; results come back as [ChatEffect]s through [emit].
 */
@Suppress("TooManyFunctions", "LongParameterList")
class MessageInteractions(
    private val scope: CoroutineScope,
    private val messages: MessageRepository,
    private val bookmarks: BookmarkRepository,
    private val downloads: AttachmentDownloads,
    private val io: AttachmentIo,
    private val isConnected: () -> Boolean,
    private val emit: (ChatEffect) -> Unit,
) {
    private val _sheet = MutableStateFlow<ChatSheet?>(null)
    val sheet: StateFlow<ChatSheet?> = _sheet.asStateFlow()

    private var fileToSave: File? = null

    fun openActions(model: MessageActionsModel) {
        _sheet.value = ChatSheet.Actions(model)
    }

    fun dismissSheet() {
        _sheet.value = null
    }

    fun copy(message: Message) {
        val text = message.plainText()
        if (text.isEmpty()) return
        emit(ChatEffect.CopyText(text))
        // The sheet stays open and the row reads "Copied" (03 §14).
        _sheet.update { current ->
            (current as? ChatSheet.Actions)?.let { it.copy(model = it.model.copy(copied = true)) }
                ?: current
        }
    }

    /** Reversible, so it offers Undo instead of asking first. */
    fun toggleBookmark(messageId: Long) {
        _sheet.value = null
        scope.launch {
            bookmarks.toggle(messageId)
                .onSuccess { saved ->
                    emit(
                        ChatEffect.Snackbar(
                            message = if (saved) {
                                R.string.feature_chat_bookmark_saved
                            } else {
                                R.string.feature_chat_bookmark_removed
                            },
                            undoBookmarkOf = messageId,
                        ),
                    )
                }
                .onFailure { error -> fail(R.string.feature_chat_bookmark_failed, error) }
        }
    }

    fun undoBookmark(messageId: Long) {
        scope.launch { bookmarks.toggle(messageId).onFailure { fail(R.string.feature_chat_bookmark_failed, it) } }
    }

    fun requestDelete(messageId: Long) {
        _sheet.value = ChatSheet.ConfirmDelete(messageId)
    }

    fun confirmDelete() {
        val confirm = _sheet.value as? ChatSheet.ConfirmDelete ?: return
        if (confirm.inProgress) return
        _sheet.value = confirm.copy(inProgress = true)
        scope.launch {
            messages.delete(confirm.messageId)
                .onSuccess { _sheet.value = null }
                .onFailure { error ->
                    _sheet.value = null
                    fail(R.string.feature_chat_delete_failed, error)
                }
        }
    }

    /** Hub only (G-01): without a live connection nothing changes locally. */
    fun react(messageId: Long, emoji: String) {
        if (_sheet.value is ChatSheet.Actions) _sheet.value = null
        if (!isConnected()) {
            emit(
                ChatEffect.Snackbar(
                    R.string.feature_chat_reaction_failed,
                    detail = R.string.feature_chat_reaction_needs_connection,
                ),
            )
            return
        }
        scope.launch {
            messages.react(messageId, emoji).onFailure { error ->
                if (error is WaiveraError.RealtimeUnavailable) {
                    emit(
                        ChatEffect.Snackbar(
                            R.string.feature_chat_reaction_failed,
                            detail = R.string.feature_chat_reaction_needs_connection,
                        ),
                    )
                } else {
                    fail(R.string.feature_chat_reaction_failed, error)
                }
            }
        }
    }

    fun openReactionDetails(messageId: Long, emoji: String) {
        _sheet.value = ChatSheet.ReactionDetails(messageId, emoji)
    }

    fun openAttachment(message: Message) {
        val attachment = message.attachment ?: return
        if (message.isPending || attachment.isBlocked) return
        _sheet.value = if (Attachments.isImage(attachment)) {
            ChatSheet.ImagePreview(message.id)
        } else {
            ChatSheet.FileActions(message.id, Attachments.fileName(attachment, message.id))
        }
    }

    fun saveAttachment(message: Message) = prepareFile(message, R.string.feature_chat_file_save_failed) {
            file,
            name,
            mime,
        ->
        fileToSave = file
        emit(ChatEffect.PickSaveLocation(name, mime))
    }

    /** From the system "create document" picker; null means the person cancelled, which is silent. */
    fun onSaveLocationPicked(uri: String?) {
        val file = fileToSave ?: return
        fileToSave = null
        if (uri == null) return
        val name = (_sheet.value as? ChatSheet.FileActions)?.fileName.orEmpty()
        scope.launch {
            io.saveTo(file, uri)
                .onSuccess {
                    _sheet.value = null
                    emit(ChatEffect.Snackbar(R.string.feature_chat_file_saved, argument = name))
                }
                .onFailure { fail(R.string.feature_chat_file_save_failed, it) }
        }
    }

    fun shareAttachment(message: Message) = prepareFile(message, R.string.feature_chat_file_open_failed) {
            file,
            name,
            mime,
        ->
        io.shareableUri(file, name)
            .onSuccess { uri ->
                _sheet.value = null
                emit(ChatEffect.Share(uri, mime, name))
            }
            .onFailure { fail(R.string.feature_chat_file_open_failed, it) }
    }

    private fun prepareFile(message: Message, @StringRes failure: Int, then: suspend (File, String, String) -> Unit) {
        val attachment = message.attachment ?: return
        val sheet = _sheet.value as? ChatSheet.FileActions ?: return
        if (sheet.busy) return
        _sheet.value = sheet.copy(busy = true)
        scope.launch {
            val name = Attachments.fileName(attachment, message.id)
            downloads.download(message.id, attachment.originalName)
                .onSuccess { file ->
                    _sheet.update { (it as? ChatSheet.FileActions)?.copy(busy = false) ?: it }
                    then(file, name, Attachments.mimeType(attachment, message.id))
                }
                .onFailure { error ->
                    _sheet.update { (it as? ChatSheet.FileActions)?.copy(busy = false) ?: it }
                    fail(failure, error)
                }
        }
    }

    /**
     * A retry that sends is confirmed by the row's "Sent" label. If the resend fails, the row
     * turning failed again is what vibrates (the view model's send tracker); a retry that cannot
     * even start says so in a snackbar only.
     */
    fun retry(clientMessageId: String) {
        scope.launch { messages.retry(clientMessageId).onFailure { fail(R.string.feature_chat_send_error_server, it) } }
    }

    fun discard(clientMessageId: String) {
        scope.launch { messages.discard(clientMessageId) }
    }

    fun openLink(url: String) {
        if (SafeLinks.isAllowed(url)) emit(ChatEffect.OpenUrl(url))
    }

    private fun fail(@StringRes title: Int, error: Throwable) {
        val typed = error as? WaiveraError ?: WaiveraError.from(error)
        emit(ChatEffect.Snackbar(title, detail = typed.messageRes))
    }
}
