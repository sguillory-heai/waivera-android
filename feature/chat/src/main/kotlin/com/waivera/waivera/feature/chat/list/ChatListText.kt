package com.waivera.waivera.feature.chat.list

import android.content.Context
import com.waivera.waivera.core.data.model.ConversationTitleText
import com.waivera.waivera.core.data.text.ResourceConversationTitleText
import com.waivera.waivera.feature.chat.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The copy the list needs as plain strings, because search matches the rendered title and
 * preview (spec 02 §5). Rows are built in the ViewModel, so the text comes through this seam;
 * tests pass an English fake. Titles come from the shared [ConversationTitleText].
 */
interface ChatListText : ConversationTitleText {
    val noMessages: String
    val photo: String
    val fileAttachment: String
    val conversationUpdate: String
    val deletedMessage: String
    val you: String
    val someone: String
    val severalTyping: String
    val now: String
    val yesterday: String

    fun typing(name: String): String

    fun minutesAgo(minutes: Int): String

    /** A group title's count in short ("+4"), for a row too narrow for "& 4 others". */
    fun compactGroupCount(others: Int): String
}

class ResourceChatListText @Inject constructor(
    @ApplicationContext private val context: Context,
    titles: ResourceConversationTitleText,
) : ChatListText,
    ConversationTitleText by titles {
    private fun s(id: Int) = context.getString(id)

    override val noMessages: String get() = s(R.string.feature_chat_list_preview_none)
    override val photo: String get() = s(R.string.feature_chat_list_preview_photo)
    override val fileAttachment: String get() = s(R.string.feature_chat_list_preview_file)
    override val conversationUpdate: String get() = s(R.string.feature_chat_list_preview_update)
    override val deletedMessage: String get() = s(R.string.feature_chat_list_preview_deleted)
    override val you: String get() = s(R.string.feature_chat_list_sender_you)
    override val someone: String get() = s(R.string.feature_chat_list_typing_someone)
    override val severalTyping: String get() = s(R.string.feature_chat_list_typing_several)
    override val now: String get() = s(R.string.feature_chat_list_time_now)
    override val yesterday: String get() = s(R.string.feature_chat_list_time_yesterday)

    override fun typing(name: String): String = context.getString(R.string.feature_chat_list_typing_one, name)

    override fun minutesAgo(minutes: Int): String = context.getString(R.string.feature_chat_list_time_minutes, minutes)

    override fun compactGroupCount(others: Int): String =
        context.resources.getQuantityString(R.plurals.feature_chat_list_title_count_compact, others, others)
}
