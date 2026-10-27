package com.waivera.waivera.feature.chat.list

/** The inbox filter chips and search (spec 02 §5), and what an empty result says. */
object ChatListFilters {
    fun matches(row: ChatRowUi, filter: ChatListFilter, query: String): Boolean {
        val passesFilter = when (filter) {
            ChatListFilter.All -> true
            ChatListFilter.Unread -> row.unreadCount > 0
            ChatListFilter.Channels -> row.isChannel
        }
        if (!passesFilter) return false
        val q = query.trim().lowercase()
        return q.isEmpty() || row.title.lowercase().contains(q) || row.preview.lowercase().contains(q)
    }

    fun emptyReason(filter: ChatListFilter, query: String): EmptyReason = when {
        query.isNotBlank() -> EmptyReason.NoMatches
        filter == ChatListFilter.Unread -> EmptyReason.AllCaughtUp
        filter == ChatListFilter.Channels -> EmptyReason.NoChannels
        else -> EmptyReason.NoConversations
    }
}
