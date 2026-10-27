package com.waivera.waivera.core.data.text

import android.content.Context
import com.waivera.waivera.core.data.R
import com.waivera.waivera.core.data.model.ConversationTitleText
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [ConversationTitleText] from string resources, read on each use so a locale change applies. */
class ResourceConversationTitleText @Inject constructor(@ApplicationContext private val context: Context) :
    ConversationTitleText {
    override val channelFallback: String get() = context.getString(R.string.core_data_title_channel)
    override val conversationFallback: String get() = context.getString(R.string.core_data_title_conversation)
    override val botTitle: String get() = context.getString(R.string.core_data_title_bot)
    override val unknownPerson: String get() = context.getString(R.string.core_data_title_unknown_person)

    override fun pairTitle(first: String, second: String): String =
        context.getString(R.string.core_data_title_pair, first, second)

    override fun groupTitle(first: String, others: Int): String =
        context.resources.getQuantityString(R.plurals.core_data_title_group, others, first, others)
}
