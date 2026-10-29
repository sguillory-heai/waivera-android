package com.waivera.waivera.feature.chat.list

import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.network.dto.MessageType

/** The second line of an inbox row (spec 02 §4): preview text, sender prefix and typing override. */
object RowSubtitle {
    private val WHITESPACE = Regex("\\s+")

    /**
     * The preview line (spec 02 §4 "Preview"). A blank result means the server sent a thin
     * `lastMessage` and the row should be hydrated from `/conversation/{id}`.
     */
    @Suppress("ReturnCount")
    fun preview(message: MessagePreview?, text: ChatListText): Preview {
        if (message == null) return Preview(text.noMessages, PreviewKind.Placeholder)
        // Deleted rows would otherwise be blank forever and re-hydrated on every refresh.
        if (message.isDeleted) return Preview(text.deletedMessage, PreviewKind.Placeholder)
        val body = firstLine(bodyText(message))
        if (body.isNotEmpty()) return Preview(body, PreviewKind.Text)
        if (message.hasAttachment || message.attachmentContentType != null) {
            val type = message.attachmentContentType.orEmpty().lowercase()
            val label = when {
                type == "image" || type.startsWith("image/") -> text.photo
                else -> message.attachmentName?.trim()?.takeIf(String::isNotEmpty) ?: text.fileAttachment
            }
            return Preview(label, PreviewKind.Attachment)
        }
        if (isSystem(message)) return Preview(text.conversationUpdate, PreviewKind.System)
        return Preview("", PreviewKind.Blank)
    }

    fun isSystem(message: MessagePreview): Boolean =
        message.messageType == MessageType.SYSTEM || message.messageType == MessageType.CHANNEL_EVENT

    /** The server's plain rendering when it sent one; otherwise the body, unwrapped if it is rich. */
    private fun bodyText(message: MessagePreview): String {
        val plain = message.plainText?.takeIf(String::isNotBlank)
            ?: message.text?.takeIf(String::isNotBlank)?.let(RichTextPlain::toPlainText)
            ?: return ""
        return if (isSystem(message)) SystemText.clean(plain) else plain
    }

    private fun firstLine(value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n').trim('\n', ' ', '\t').substringBefore('\n')
            .replace(WHITESPACE, " ").trim()

    /**
     * "You" for my own message, the author's first name in groups and channels, nothing for a
     * direct chat from the other person, bots, placeholders and system rows.
     */
    fun senderPrefix(c: Conversation, preview: Preview, myUserId: String?, text: ChatListText): String? {
        val message = c.lastMessage ?: return null
        val shown = preview.kind == PreviewKind.Text || preview.kind == PreviewKind.Attachment
        if (!shown || isSystem(message)) return null
        val author = message.authorId
        return when {
            author != null && author == myUserId -> text.you
            !ConversationPresentation.isChannel(c) && !ConversationPresentation.isGroup(c, myUserId) -> null
            else -> authorFirstName(c, message)
        }
    }

    private fun authorFirstName(c: Conversation, message: MessagePreview): String? {
        val person = c.participants.firstOrNull { it.id == message.authorId }
        val name = person?.let(ConversationPresentation::displayName)?.takeIf(String::isNotEmpty)
            ?: message.authorName?.trim()?.takeIf(String::isNotEmpty)
        return name?.split(WHITESPACE)?.first()
    }

    /** Spec 02 §4 "Typing override". */
    fun typingLabel(c: Conversation, typingUserIds: Set<String>, myUserId: String?, text: ChatListText): String? {
        val ids = typingUserIds - setOfNotNull(myUserId)
        return when {
            ids.isEmpty() -> null
            ids.size > 1 -> text.severalTyping
            else -> text.typing(typingName(c, ids.first(), text))
        }
    }

    /**
     * Where the one typing person's name ends in [typingLabel]'s line ("Priya Raman" in "Priya
     * Raman is typing…"), so a one-line row cuts the name and keeps "is typing…" whole, as the
     * conversation's typing slot does. Null when no name shows (several people) or the copy has none.
     */
    fun typingNamesEnd(c: Conversation, typingUserIds: Set<String>, myUserId: String?, text: ChatListText): Int? {
        val ids = typingUserIds - setOfNotNull(myUserId)
        if (ids.size != 1) return null
        val name = typingName(c, ids.first(), text)
        val at = text.typing(name).lastIndexOf(name)
        return if (at < 0) null else at + name.length
    }

    private fun typingName(c: Conversation, userId: String, text: ChatListText): String {
        val person = c.participants.firstOrNull { it.id == userId }
        return person?.let(ConversationPresentation::displayName)?.takeIf(String::isNotEmpty) ?: text.someone
    }
}
