package com.waivera.waivera.feature.today.notices

import androidx.compose.runtime.Immutable
import com.waivera.waivera.core.data.model.Notice
import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.data.repository.NoticeRepository
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.feature.today.model.NeedsYouItem
import com.waivera.waivera.feature.today.model.PastLabel
import com.waivera.waivera.feature.today.model.TimeLabels
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** The chips above the inbox. Approvals include signature requests: both wait on the person's yes. */
enum class NoticeFilter { All, Tasks, Approvals, Meetings }

/** Where a notification opens: an event in the app, a page of the web app, or nowhere it can reach. */
sealed interface NoticeDestination {
    data class Event(val eventId: String) : NoticeDestination

    data class Web(val url: String) : NoticeDestination

    /** A request waiting on the person's review. */
    data object Requests : NoticeDestination

    data class Task(val taskId: Int) : NoticeDestination

    data object None : NoticeDestination
}

/** One inbox row, with its time already in the employee's zone. */
@Immutable
data class NoticeRowModel(
    val id: String,
    val kind: NoticeKind,
    val title: String,
    val message: String,
    val at: PastLabel?,
    val unread: Boolean,
    /** Opens in the browser (no screen for it in the app yet), so the row shows the open-outside mark. */
    val opensOutside: Boolean,
)

/** The day headings, newest first. */
enum class NoticeDay { Today, Yesterday, ThisWeek, Earlier }

@Immutable
data class NoticeGroup(val day: NoticeDay, val rows: List<NoticeRowModel>)

/**
 * Turns notifications into links. Web pages hang off the configured web base, and only its own
 * host is ever opened, so a notification can never send the person to another site.
 */
class NoticeLinks @Inject constructor(private val config: NetworkConfig) {
    @Suppress("ReturnCount") // One early answer per kind of link, in order of preference.
    fun destination(notice: Notice): NoticeDestination {
        val eventId = notice.eventId
        if (eventId != null) return NoticeDestination.Event(eventId)
        if (notice.opensRequests) return NoticeDestination.Requests
        // Tasks have their own page now; other web pages about a task still open on the web.
        val taskId = notice.taskId?.toIntOrNull()
        val webPath = notice.webPath
        // Only the task's own page; deeper web pages about it (files, history) stay on the web.
        if (taskId != null && (webPath == null || webPath.trimEnd('/') == "/task/$taskId")) {
            return NoticeDestination.Task(taskId)
        }
        val base = config.webBase
        val path = notice.webPath ?: notice.taskId?.let { "/task/$it" }
        val url = path?.let { base?.resolve(it) }?.takeIf { it.host == base?.host && it.scheme == base.scheme }
        return url?.let { NoticeDestination.Web(it.toString()) } ?: NoticeDestination.None
    }

    companion object {
        /** No web app address: only events open. */
        val Unconfigured = NoticeLinks(NetworkConfig(apiBaseUrl = "", webBaseUrl = ""))
    }
}

/**
 * Opens a notification from the inbox or Needs you: works out where it goes, then marks it read.
 * A failed mark-read puts the row back in the repository; it is not worth a message. Null when
 * the notification is no longer in the list.
 */
class NoticeOpener @Inject constructor(private val notices: NoticeRepository, val links: NoticeLinks) {
    suspend fun open(id: String, onDestination: suspend (NoticeDestination) -> Unit) {
        val notice = notices.observeNotices().first().data.items.firstOrNull { it.id == id } ?: return
        val destination = links.destination(notice)
        onDestination(destination)
        // A notification that could not be opened stays unread, so its reminder is not lost.
        if (!notice.read && destination != NoticeDestination.None) notices.markRead(id)
    }
}

/** Filtering, grouping and row rules for the inbox. Pure, so every rule is unit tested. */
object NoticeRules {
    private const val WEEK_DAYS = 6L

    fun matches(kind: NoticeKind, filter: NoticeFilter): Boolean = when (filter) {
        NoticeFilter.All -> true
        NoticeFilter.Tasks -> kind == NoticeKind.TaskAssigned || kind == NoticeKind.TaskActivity
        NoticeFilter.Approvals -> kind == NoticeKind.Approval || kind == NoticeKind.Signature
        NoticeFilter.Meetings -> kind == NoticeKind.Meeting
    }

    fun row(notice: Notice, links: NoticeLinks, now: Instant, zone: ZoneId) = NoticeRowModel(
        id = notice.id,
        kind = notice.kind,
        title = notice.title,
        message = notice.message,
        at = notice.createdAt?.let { TimeLabels.past(it, now, zone) },
        unread = !notice.read,
        opensOutside = links.destination(notice) is NoticeDestination.Web,
    )

    /** Newest first under Today, Yesterday, This week and Earlier, in the employee's zone. */
    fun groups(
        notices: List<Notice>,
        filter: NoticeFilter,
        links: NoticeLinks,
        now: Instant,
        zone: ZoneId,
    ): List<NoticeGroup> {
        val today = now.atZone(zone).toLocalDate()
        return notices
            .filter { matches(it.kind, filter) }
            .sortedWith(compareByDescending<Notice, Instant?>(nullsFirst()) { it.createdAt }.thenByDescending { it.id })
            .groupBy { day(it.createdAt?.atZone(zone)?.toLocalDate(), today) }
            .toSortedMap()
            .map { (day, items) -> NoticeGroup(day, items.map { row(it, links, now, zone) }) }
    }

    /**
     * Unread notifications that ask the person to act (a task, an approval, a signature, a survey),
     * newest first. A request to review is left out: its notification keeps saying "pending" after
     * another approver decides it, so Needs you lists those from the live pending list instead.
     */
    fun needsYou(notices: List<Notice>, links: NoticeLinks, now: Instant, zone: ZoneId): List<NeedsYouItem.Notice> =
        notices
            .filter { !it.read && it.asksForAction && !it.opensRequests }
            .sortedWith(compareByDescending<Notice, Instant?>(nullsFirst()) { it.createdAt })
            .map { n ->
                NeedsYouItem.Notice(
                    noticeId = n.id,
                    noticeKind = n.kind,
                    title = n.title,
                    message = n.message,
                    at = n.createdAt?.let { TimeLabels.past(it, now, zone) },
                    opensOutside = links.destination(n) is NoticeDestination.Web,
                )
            }

    fun day(date: LocalDate?, today: LocalDate): NoticeDay = when {
        date == null -> NoticeDay.Earlier
        !date.isBefore(today) -> NoticeDay.Today
        date == today.minusDays(1) -> NoticeDay.Yesterday
        !date.isBefore(today.minusDays(WEEK_DAYS)) -> NoticeDay.ThisWeek
        else -> NoticeDay.Earlier
    }
}
