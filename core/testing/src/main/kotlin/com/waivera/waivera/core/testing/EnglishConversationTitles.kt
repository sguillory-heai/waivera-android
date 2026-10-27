package com.waivera.waivera.core.testing

import com.waivera.waivera.core.data.model.ConversationTitleText

/** The shipped English title copy, for JVM tests that cannot read resources. */
object EnglishConversationTitles : ConversationTitleText {
    override val channelFallback = "Channel"
    override val conversationFallback = "Conversation"
    override val botTitle = "Waivera"
    override val unknownPerson = "Unknown"

    override fun pairTitle(first: String, second: String) = "$first, $second"

    override fun groupTitle(first: String, others: Int) = "$first & $others ${if (others == 1) "other" else "others"}"
}
