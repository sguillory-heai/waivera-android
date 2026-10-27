package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waivera.waivera.core.data.model.Bookmark
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.core.network.time.WaiveraTime
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a bookmark row says instead of the text when there is none. */
internal sealed interface BookmarkPreview {
    data class Text(val text: String) : BookmarkPreview

    data object Photo : BookmarkPreview

    data class File(val name: String?) : BookmarkPreview

    data object Saved : BookmarkPreview
}

@Immutable
internal data class BookmarkRow(
    val messageId: Long,
    val conversationId: Long,
    /** Where a thread reply opens: the thread root, never the direct parent (Expo bug 11 §21). */
    val threadRootId: Long?,
    val author: String,
    /** The author's user id, for their photo through the shared avatar loader (UI3-05). */
    val authorId: String? = null,
    val createdAt: Instant?,
    val preview: BookmarkPreview,
    /** [createdAt] in the inbox's words, set by the ViewModel from its clock and the saved zone. */
    val date: BookmarkDate? = null,
) {
    val isThreadReply: Boolean get() = threadRootId != null
}

@Immutable
internal data class BookmarksUiState(
    val query: String = "",
    val rows: List<BookmarkRow> = emptyList(),
    val totalCount: Int = 0,
    val phase: ListPhase = ListPhase.Loading,
    val error: WaiveraError? = null,
    val isRefreshing: Boolean = false,
    val connection: ConnectionBannerState = ConnectionBannerState.Connected,
    /** "Now" for relative times, from the injected clock so screenshots are stable. */
    val now: Instant = Instant.EPOCH,
    /** The employee's saved zone, so "today" and dates agree with the inbox (never the device zone). */
    val zone: ZoneId = WaiveraTime.FallbackZone,
)

/** When a bookmarked message was sent, in the inbox's words (UI2-27): a time today, Yesterday, "Oct 20". */
internal sealed interface BookmarkDate {
    data class Today(val at: ZonedDateTime) : BookmarkDate

    data object Yesterday : BookmarkDate

    data class OnDate(val date: LocalDate, val showYear: Boolean) : BookmarkDate

    companion object {
        fun of(at: Instant, now: Instant, zone: ZoneId): BookmarkDate {
            val sent = at.atZone(zone)
            val today = now.atZone(zone).toLocalDate()
            return when (sent.toLocalDate()) {
                today -> Today(sent)
                today.minusDays(1) -> Yesterday
                else -> OnDate(sent.toLocalDate(), showYear = sent.year != today.year)
            }
        }
    }
}

/** Localised words a search can match besides the message itself ("thread reply", "photo"...). */
internal data class BookmarkSearchWords(
    val threadReply: String = "thread reply",
    val message: String = "message",
    val photo: String = "photo",
    val file: String = "file",
)

internal sealed interface BookmarksEffect {
    /** Removed: offer Undo. */
    data class Removed(val messageId: Long) : BookmarksEffect

    data class RemoveFailed(val messageId: Long, val error: WaiveraError) : BookmarksEffect

    /** Undo could not save the message again. */
    data class RestoreFailed(val error: WaiveraError) : BookmarksEffect
}

/** Mapping, ordering and search rules from spec 06 §B, kept pure for tests. */
internal object BookmarkRules {
    /** Rows need a positive conversation id (the stricter of the two Expo rules). */
    fun rows(bookmarks: List<Bookmark>): List<BookmarkRow> = bookmarks
        .map { it.message }
        .filter { it.conversationId > 0 && !it.isDeleted }
        .distinctBy { it.id }
        // Instants come from the shared UTC parser, so zone-less and zoned values order together.
        .sortedWith(compareByDescending<Message, Instant?>(nullsFirst()) { it.createdAt }.thenByDescending { it.id })
        .map(::row)

    fun row(message: Message) = BookmarkRow(
        messageId = message.id,
        conversationId = message.conversationId,
        threadRootId = threadRoot(message),
        author = message.author?.name.orEmpty(),
        authorId = message.authorId ?: message.author?.id,
        createdAt = message.createdAt,
        preview = preview(message),
    )

    /** Fills each row's [BookmarkRow.date] for [now] in the employee's [zone]. */
    fun dated(rows: List<BookmarkRow>, now: Instant, zone: ZoneId): List<BookmarkRow> =
        rows.map { row -> row.copy(date = row.createdAt?.let { BookmarkDate.of(it, now, zone) }) }

    /** `threadRootMessageId ?? parentMessageId`, ignoring a root that points at itself. */
    fun threadRoot(message: Message): Long? =
        message.threadRootMessageId?.takeIf { it != message.id } ?: message.parentMessageId?.takeIf { it != message.id }

    fun preview(message: Message): BookmarkPreview {
        val text = message.text?.replace(WHITESPACE, " ")?.trim().orEmpty()
        val attachment = message.attachment
        return when {
            text.isNotEmpty() -> BookmarkPreview.Text(text)
            attachment?.isImage == true -> BookmarkPreview.Photo
            attachment != null -> BookmarkPreview.File(attachment.originalName?.takeIf(String::isNotBlank))
            else -> BookmarkPreview.Saved
        }
    }

    fun matches(row: BookmarkRow, query: String, words: BookmarkSearchWords): Boolean {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) return true
        val haystack = buildList {
            add(row.author)
            add(if (row.isThreadReply) words.threadReply else words.message)
            when (val preview = row.preview) {
                is BookmarkPreview.Text -> add(preview.text)
                BookmarkPreview.Photo -> add(words.photo)
                is BookmarkPreview.File -> {
                    add(words.file)
                    preview.name?.let(::add)
                }
                BookmarkPreview.Saved -> Unit
            }
        }.joinToString(" ").lowercase(Locale.getDefault())
        return haystack.contains(needle)
    }

    private val WHITESPACE = Regex("\\s+")
}

/**
 * Saved messages. Leaving the screen clears the ViewModel, which cancels a running refresh,
 * including the slow per-conversation fallback (G-04). Removing a bookmark is optimistic with
 * Undo; a failure puts the row back and says so.
 */
@HiltViewModel
internal class BookmarksViewModel @Inject constructor(
    private val bookmarks: BookmarkRepository,
    private val realtime: RealtimeStatusRepository,
    private val calendar: CalendarRepository,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val words = MutableStateFlow(BookmarkSearchWords())
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<WaiveraError?>(null)
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())
    private val effects = Channel<BookmarksEffect>(Channel.BUFFERED)
    private var refreshJob: Job? = null

    val effect: Flow<BookmarksEffect> = effects.receiveAsFlow()

    val uiState: StateFlow<BookmarksUiState> = combine(
        bookmarks.observeBookmarks(),
        combine(query, words) { q, w -> q to w },
        combine(refreshing, refreshError) { busy, error -> busy to error },
        hidden,
        combine(realtime.status, calendar.zone) { status, zone -> status to zone },
    ) { synced, (text, searchWords), (busy, failed), removed, (status, zone) ->
        val all = BookmarkRules.rows(synced.data).filter { it.messageId !in removed }
        val now = clock.instant()
        val shown = all.filter { BookmarkRules.matches(it, text, searchWords) }
            .let { BookmarkRules.dated(it, now, zone) }
        val error = synced.error ?: failed
        BookmarksUiState(
            query = text,
            rows = shown,
            totalCount = all.size,
            phase = when {
                all.isNotEmpty() -> if (shown.isEmpty()) ListPhase.Empty else ListPhase.Content
                error != null -> ListPhase.Error
                synced.lastSyncedAt == null || busy -> ListPhase.Loading
                else -> ListPhase.Empty
            },
            error = error,
            isRefreshing = busy,
            connection = status.toBannerState(),
            now = now,
            zone = zone,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        BookmarksUiState(now = clock.instant()),
    )

    init {
        refresh()
        viewModelScope.launch { realtime.status.reconnections().collect { refresh() } }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** The screen passes the localised search words once. */
    fun setSearchWords(value: BookmarkSearchWords) {
        words.value = value
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            try {
                refreshError.value = bookmarks.refresh().exceptionOrNull()?.asWaivera()
            } finally {
                refreshing.value = false
            }
        }
    }

    /** Hides the row at once, then removes the bookmark; Undo saves it again. */
    fun remove(messageId: Long) {
        if (messageId in hidden.value) return
        hidden.update { it + messageId }
        viewModelScope.launch {
            bookmarks.toggle(messageId)
                .onSuccess { saved ->
                    // A toggle that saved instead means the server had already removed it; put it back.
                    if (saved) bookmarks.toggle(messageId)
                    effects.send(BookmarksEffect.Removed(messageId))
                }
                .onFailure { effects.send(BookmarksEffect.RemoveFailed(messageId, it.asWaivera())) }
            hidden.update { it - messageId }
        }
    }

    fun undoRemove(messageId: Long) {
        viewModelScope.launch {
            bookmarks.toggle(messageId).onFailure { effects.send(BookmarksEffect.RestoreFailed(it.asWaivera())) }
        }
    }

    fun retryConnection() = realtime.retryNow()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
