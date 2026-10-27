package com.waivera.waivera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.component.WaiveraCountBadge
import com.waivera.waivera.core.designsystem.component.WaiveraFilterChip
import com.waivera.waivera.core.designsystem.component.formatBadgeCount
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.network.dto.ChatActivityType
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.ActivityFilter
import com.waivera.waivera.feature.today.model.ActivityRow
import com.waivera.waivera.feature.today.model.ActivitySection
import com.waivera.waivera.feature.today.model.ActivitySummary
import com.waivera.waivera.feature.today.model.PastLabel

/** "Activity" header with the unread count across every loaded page and Mark all read. */
@Composable
internal fun ActivityHeader(section: ActivitySection, onMarkAllRead: () -> Unit, modifier: Modifier = Modifier) {
    TodaySectionHeader(
        title = stringResource(R.string.feature_today_section_activity),
        modifier = modifier,
        trailing = {
            UnreadCountBadge(count = section.unreadCount, lowerBound = section.unreadIsLowerBound)
            if (section.unreadCount > 0 || section.isMarkingAllRead) {
                WaiveraButton(
                    text = stringResource(R.string.feature_today_mark_all_read),
                    onClick = onMarkAllRead,
                    variant = WaiveraButtonVariant.Text,
                    loading = section.isMarkingAllRead,
                    leadingIcon = WaiveraIcons.MarkRead,
                )
            }
        },
    )
}

@Composable
private fun UnreadCountBadge(count: Int, lowerBound: Boolean) {
    val lowerBoundText = stringResource(R.string.feature_today_unread_lower_bound, count)
    val text = (if (lowerBound) lowerBoundText else formatBadgeCount(count)) ?: return
    val description = if (lowerBound) {
        pluralStringResource(R.plurals.feature_today_unread_lower_bound_a11y, count, count)
    } else {
        pluralStringResource(R.plurals.feature_today_unread_a11y, count, count)
    }
    // The same solid count badge as every other unread count (UI2-16).
    WaiveraCountBadge(text = text, contentDescription = description)
}

/** Type chips (All, Mentions, Replies, Reactions) and the Unread toggle; they combine with AND. */
@Composable
internal fun ActivityFilterRow(
    filter: ActivityFilter,
    unreadOnly: Boolean,
    onFilter: (ActivityFilter) -> Unit,
    onToggleUnread: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = WaiveraSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Unread is a separate on/off switch, not a fourth type: a check mark and a divider set it
        // apart, so "All" and "Unread" never read as two picks of one choice (UI2-19).
        WaiveraFilterChip(
            label = stringResource(R.string.feature_today_filter_unread),
            selected = unreadOnly,
            onClick = onToggleUnread,
            leadingIcon = if (unreadOnly) WaiveraIcons.Check else null,
        )
        VerticalDivider(
            modifier = Modifier.height(WaiveraSizes.Icon),
            color = WaiveraTheme.colors.border,
        )
        ActivityFilter.entries.forEach { option ->
            WaiveraFilterChip(label = filterLabel(option), selected = option == filter, onClick = { onFilter(option) })
        }
    }
}

@Composable
private fun filterLabel(filter: ActivityFilter): String = stringResource(
    when (filter) {
        ActivityFilter.ALL -> R.string.feature_today_filter_all
        ActivityFilter.MENTIONS -> R.string.feature_today_filter_mentions
        ActivityFilter.REPLIES -> R.string.feature_today_filter_replies
        ActivityFilter.REACTIONS -> R.string.feature_today_filter_reactions
    },
)

/** One activity: icon by type (reply icon for unknown types), summary, kind and time, unread dot. */
@Composable
internal fun ActivityItemRow(row: ActivityRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val formats = rememberTodayFormats()
    val summary = summaryText(row.summary)
    val kind = activityKindText(row.type)
    val spoken = joined(joined(kind, summary), row.at?.let { pastSpokenText(it, formats) })
    TodayRow(
        headline = summary,
        description = if (row.read) spoken else stringResource(R.string.feature_today_activity_unread_prefix, spoken),
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_today_open_message),
        supporting = joined(kind, row.at?.let { pastText(it, formats) }),
        emphasized = !row.read,
        showChevron = true,
        modifier = modifier,
        leading = { TodayIconTile(icon = activityIcon(row.type), muted = row.read) },
        trailing = { if (!row.read) UnreadDot() },
    )
}

private fun activityIcon(type: ChatActivityType): Int = when (type) {
    ChatActivityType.MENTION -> R.drawable.feature_today_ic_mention
    ChatActivityType.REACTION -> R.drawable.feature_today_ic_reaction
    ChatActivityType.THREAD_REPLY, ChatActivityType.UNKNOWN -> WaiveraIcons.Reply
}

/** Footer under the feed: a spinner while the next page loads, or an inline retry when it failed. */
@Composable
internal fun ActivityFooter(section: ActivitySection, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when {
        // Quiet, like the section failures above it: the loaded feed is still fine.
        section.loadMoreFailed -> SectionError(
            message = stringResource(R.string.feature_today_activity_more_failed),
            onRetry = onRetry,
            modifier = modifier,
        )
        section.isLoadingMore -> {
            val loading = stringResource(R.string.feature_today_activity_loading_more)
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(WaiveraSpacing.Medium)
                    .semantics { contentDescription = loading },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = WaiveraTheme.colors.accent,
                    strokeWidth = WaiveraSizes.ProgressStroke,
                    modifier = Modifier.size(WaiveraSizes.ProgressSmall),
                )
            }
        }
    }
}

@WaiveraPreviews
@Composable
private fun ActivityRowsPreview() {
    val section = ActivitySection(unreadCount = 31, unreadIsLowerBound = true, totalCount = 40, isReady = true)
    WaiveraTheme {
        Column(Modifier.background(WaiveraTheme.colors.background)) {
            ActivityHeader(section = section, onMarkAllRead = {})
            ActivityFilterRow(ActivityFilter.ALL, unreadOnly = false, onFilter = {}, onToggleUnread = {})
            ActivityItemRow(
                ActivityRow(
                    id = 1,
                    type = ChatActivityType.REACTION,
                    summary = ActivitySummary.Reaction("Dana", "🎉"),
                    at = PastLabel.MinutesAgo(3),
                    read = false,
                    conversationId = 7,
                    messageId = 9,
                    threadRootMessageId = null,
                ),
                onClick = {},
            )
            ActivityFooter(section.copy(loadMoreFailed = true), onRetry = {})
        }
    }
}
