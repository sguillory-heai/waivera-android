package com.waivera.waivera.feature.today.model

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.ActivityItem
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.ConversationKind
import com.waivera.waivera.core.data.model.ConversationTitleText
import com.waivera.waivera.core.data.model.ConversationTitles
import com.waivera.waivera.core.data.model.HandledTally
import com.waivera.waivera.core.data.model.MessagePreview
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.designsystem.component.ConnectionBannerState
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.core.realtime.ConnectionState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the screen has done locally on top of the cached data (optimistic writes, chips, paging). */
data class TodayLocalState(
    val filter: ActivityFilter = ActivityFilter.ALL,
    val unreadOnly: Boolean = false,
    val needsYouExpanded: Boolean = false,
    /** Invitations answered optimistically; they leave Needs you at once and return if the call fails. */
    val answeredInvitations: Set<String> = emptySet(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val isMarkingAllRead: Boolean = false,
    /** The person whose "Send wishes" chat is opening, so their button shows it is working. */
    val openingWishesFor: String? = null,
    /** Set when the auto-loader stopped at its page cap while unread items may remain. */
    val autoPagingCapped: Boolean = false,
)

/** Cached sources, as the repositories emit them. [schedule] is null until its range is known. */
data class TodaySources(
    val conversations: Synced<List<Conversation>>,
    val activity: Synced<ActivityFeed>,
    val schedule: Synced<ScheduleFeed>?,
    val milestones: Synced<List<Milestone>>,
    /** Conversations with a message of mine the outbox gave up on. */
    val failedSends: Set<Long> = emptySet(),
)

/** Who, where and when the screen is rendered for. */
data class TodayContext(
    val accountName: String,
    val myUserId: String?,
    val zone: ZoneId,
    val now: Instant,
    val realtime: RealtimeStatus?,
    val local: TodayLocalState,
    /** The shared conversation-title copy, so a Today row names a chat the way the inbox does. */
    val titles: ConversationTitleText,
    /** Who the greeting names; null greets without a name. */
    val firstName: String? = null,
    /** The saved handled-today tally ([HandledTally]); null before the first one. */
    val handled: HandledTally? = null,
)

/** Builds [TodayUiState] from the cache. Pure, so every rule is unit tested without Android. */
object TodayAssembler {
    /** Data older than this gets the quiet "Updated N min ago" line. */
    val STALE_AFTER: Duration = Duration.ofMinutes(5)

    fun assemble(sources: TodaySources, context: TodayContext): TodayUiState {
        val today = context.now.atZone(context.zone).toLocalDate()
        val pinned = pinnedMentions(sources)
        val activity = activitySection(sources.activity, context, pinned.mapTo(mutableSetOf()) { it.id })
        val openItems = openItems(sources, context, today, pinned)
        val needsYou = needsYouSection(sources, context, openItems)
        val nextUp = ScheduleRules.nextUp(sources.schedule, context, today)
        val observation = HandledRules.observation(
            context = context,
            today = today,
            openItems = openItems,
            complete = needsYou.isReady && !needsYou.failed && nextUp.isReady && activity.isReady && !activity.failed,
        )
        val primary = listOf(sources.conversations, sources.activity, sources.schedule)
        val nothingCached = primary.all { it == null || !it.hasContent() }
        val stillLoading = primary.any { it == null || it.isLoading() }
        val firstError = primary.firstNotNullOfOrNull { it?.error }
        return TodayUiState(
            accountName = context.accountName,
            greeting = GreetingRules.greeting(context.now, context.zone, context.firstName),
            date = today,
            isInitialLoad = nothingCached && stillLoading,
            loadError = if (nothingCached && !stillLoading) firstError else null,
            connection = connectionBanner(context.realtime),
            staleSince = staleLabel(primary.filterNotNull(), context),
            savedAt = primary.mapNotNull { it?.lastSyncedAt }.minOrNull()?.atZone(context.zone)?.toLocalTime(),
            isRefreshing = context.local.isRefreshing,
            openingWishesFor = context.local.openingWishesFor,
            needsYou = needsYou,
            nextUp = nextUp,
            milestones = ScheduleRules.celebrations(sources.milestones.data, today)
                .map { it.copy(isMe = it.userId == context.myUserId) },
            milestonesFailed = sources.milestones.error != null && sources.milestones.data.isEmpty(),
            activity = activity,
            progress = HandledRules.progress(context, today, needsYou, observation),
            stats = DayRules.stats(sources.conversations, sources.schedule, context.zone, today),
            day = DayRules.timeline(sources.schedule, context.zone, today, context.now),
            handledObservation = observation,
        )
    }

    fun connectionBanner(status: RealtimeStatus?): ConnectionBannerState = when {
        status == null -> ConnectionBannerState.Connected
        !status.isOnline -> ConnectionBannerState.Offline
        status.state is ConnectionState.Reconnecting -> ConnectionBannerState.Reconnecting
        // Connecting and Disconnected follow the app lifecycle; a banner for them would only flash.
        else -> ConnectionBannerState.Connected
    }

    /**
     * Unread mentions shown under Needs you (and therefore left out of the feed below). A mention
     * inside a DM that is already listed as unread is not repeated; it stays in the feed.
     */
    private fun pinnedMentions(sources: TodaySources): List<ActivityItem> {
        // Any DM with unread messages, including one I answered last: a mention there stays in
        // the feed instead of coming back as "needs you" (see Conversation.isUnreadFor).
        val unreadDirects = sources.conversations.data
            .filter { it.kind == ConversationKind.DIRECT && it.unreadCount > 0 }
            .mapTo(mutableSetOf()) { it.id }
        return ActivityRules.merge(sources.activity.data.items).filter {
            it.type == ChatActivityType.MENTION && !it.read && it.conversationId !in unreadDirects
        }
    }

    /** Everything that needs the person, in Needs you order: failed sends, chats and mentions, invitations, tickets. */
    private fun openItems(
        sources: TodaySources,
        context: TodayContext,
        today: LocalDate,
        pinnedMentions: List<ActivityItem>,
    ): List<NeedsYouItem> {
        val conversations = sources.conversations.data
        val directs = conversations.filter { it.kind == ConversationKind.DIRECT && it.isUnreadFor(context.myUserId) }
        val dmRows = directs.map { c ->
            c.lastMessage?.createdAt to NeedsYouItem.DirectMessage(
                conversationId = c.id,
                name = ConversationTitles.format(c, context.myUserId, context.titles),
                snippet = snippet(c.lastMessage),
                unreadCount = c.unreadCount,
                at = c.lastMessage?.createdAt?.let { TimeLabels.past(it, context.now, context.zone) },
                otherUserId = c.others(context.myUserId).singleOrNull()?.id,
            )
        }
        val mentionRows = pinnedMentions.map { item -> item.createdAt to mentionItem(item, context) }
        val conversationRows = (dmRows + mentionRows)
            .sortedWith(compareByDescending(nullsFirst<Instant>()) { it.first })
            .map { it.second }
        val invitations = ScheduleRules.invitations(sources.schedule?.data, context, today)
        val tickets = conversations.filter { it.kind == ConversationKind.BOT && it.unreadCount > 0 }.map { c ->
            NeedsYouItem.Ticket(
                conversationId = c.id,
                snippet = snippet(c.lastMessage),
                unreadCount = c.unreadCount,
                at = c.lastMessage?.createdAt?.let { TimeLabels.past(it, context.now, context.zone) },
                ticketId = c.lastMessage?.ticket?.ticketId,
                ticketTitle = c.lastMessage?.ticket?.title,
            )
        }
        return notSentRows(sources, context) + conversationRows + invitations + tickets
    }

    private fun needsYouSection(
        sources: TodaySources,
        context: TodayContext,
        all: List<NeedsYouItem>,
    ): NeedsYouSection {
        val visible = if (context.local.needsYouExpanded) all else all.take(NeedsYouSection.COLLAPSED_COUNT)
        val ready = sources.conversations.hasContent() || sources.conversations.lastSyncedAt != null
        return NeedsYouSection(
            items = visible,
            totalCount = all.size,
            hiddenCount = all.size - visible.size,
            isExpanded = context.local.needsYouExpanded && all.size > NeedsYouSection.COLLAPSED_COUNT,
            isReady = ready,
            failed = !ready && sources.conversations.error != null,
        )
    }

    /** A message of mine that never went out comes first: only I can fix it (UI2-09). */
    private fun notSentRows(sources: TodaySources, context: TodayContext): List<NeedsYouItem.NotSent> =
        sources.conversations.data
            .filter { it.id in sources.failedSends }
            .map { c ->
                NeedsYouItem.NotSent(
                    conversationId = c.id,
                    name = ConversationTitles.format(c, context.myUserId, context.titles),
                )
            }

    private fun mentionItem(item: ActivityItem, context: TodayContext) = NeedsYouItem.Mention(
        activityId = item.id,
        conversationId = item.conversationId,
        messageId = item.messageId,
        threadRootMessageId = item.threadRootMessageId,
        actorName = item.actorName?.takeIf(String::isNotBlank),
        summary = ActivityRules.summary(item),
        at = item.createdAt?.let { TimeLabels.past(it, context.now, context.zone) },
    )

    private fun activitySection(
        synced: Synced<ActivityFeed>,
        context: TodayContext,
        pinnedMentions: Set<Long>,
    ): ActivitySection {
        val local = context.local
        val merged = ActivityRules.merge(synced.data.items)
        val rows = merged
            .filter { it.id !in pinnedMentions }
            .filter { ActivityRules.matches(it.type, it.read, local.filter, local.unreadOnly) }
            .map { item ->
                ActivityRow(
                    id = item.id,
                    type = item.type,
                    summary = ActivityRules.summary(item),
                    at = item.createdAt?.let { TimeLabels.past(it, context.now, context.zone) },
                    read = item.read,
                    conversationId = item.conversationId,
                    messageId = item.messageId,
                    threadRootMessageId = item.threadRootMessageId,
                )
            }
        val ready = synced.hasContent() || synced.lastSyncedAt != null
        val mayHoldMore = ActivityRules.olderPagesMayHoldUnread(merged, synced.data.hasMore) { it.read }
        return ActivitySection(
            rows = rows,
            filter = local.filter,
            unreadOnly = local.unreadOnly,
            unreadCount = merged.count { !it.read },
            unreadIsLowerBound = mayHoldMore && local.autoPagingCapped,
            totalCount = merged.size - pinnedMentions.size,
            hasMore = synced.data.hasMore && synced.lastSyncedAt != null,
            isLoadingMore = local.isLoadingMore,
            loadMoreFailed = local.loadMoreFailed,
            isMarkingAllRead = local.isMarkingAllRead,
            isReady = ready,
            failed = !ready && synced.error != null,
        )
    }

    /** The preview's first non-blank line, else what the attachment is (02 §4 preview rules). */
    fun snippet(preview: MessagePreview?): MessageSnippet {
        val firstLine = preview?.text?.lineSequence()?.map { it.trim() }?.firstOrNull(String::isNotEmpty)
        return when {
            preview == null -> MessageSnippet.None
            preview.isDeleted -> MessageSnippet.Deleted
            firstLine != null -> MessageSnippet.Text(firstLine.replace(WHITESPACE, " "))
            !preview.hasAttachment -> MessageSnippet.None
            preview.attachmentContentType?.startsWith("image/") == true -> MessageSnippet.Photo
            else -> MessageSnippet.File(preview.attachmentName?.takeIf(String::isNotBlank))
        }
    }

    /** "Updated N min ago" only when the oldest source is older than [STALE_AFTER] and no refresh is running. */
    private fun staleLabel(sources: List<Synced<*>>, context: TodayContext): PastLabel? {
        val oldest = sources.mapNotNull { it.lastSyncedAt }.minOrNull()
        val stale = oldest != null &&
            !context.local.isRefreshing &&
            Duration.between(oldest, context.now) >= STALE_AFTER
        return if (stale && oldest != null) TimeLabels.past(oldest, context.now, context.zone) else null
    }

    private val WHITESPACE = Regex("\\s+")
}
