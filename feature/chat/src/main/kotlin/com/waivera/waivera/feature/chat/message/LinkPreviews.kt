package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.designsystem.component.BadgeTone
import com.waivera.waivera.core.designsystem.component.SkeletonText
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.component.rememberSkeletonPulse
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.chat.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * A Waivera link in a message, resolved for the person reading it. Titles and details are always
 * one line. A link at the end of a message leaves the text once it has a preview; a link inside a
 * sentence stays, shown by its short name (a task's number, a resource or article title), so the
 * reader can tell which preview goes with which part of the message ([ChatLinks.present]).
 */
@Immutable
sealed interface LinkPreview {
    val kind: LinkKind

    data class Loading(override val kind: LinkKind) : LinkPreview

    /** The person can't see it, or it no longer exists; the server doesn't say which. */
    data class Unavailable(override val kind: LinkKind) : LinkPreview

    data class Task(
        val id: Int,
        val key: String,
        val title: String,
        val state: TaskState,
        /** A literal calendar date. */
        val due: LocalDate?,
        /** The due date falls in another year than the reader's today, so it shows the year. */
        val dueShowsYear: Boolean = false,
    ) : LinkPreview {
        override val kind = LinkKind.Task
    }

    data class Resource(val title: String, val provider: String?, val place: String?) : LinkPreview {
        override val kind = LinkKind.Resource
    }

    data class Faq(val title: String, val summary: String?) : LinkPreview {
        override val kind = LinkKind.Faq
    }
}

/** One link of a message and its preview; [href] is the link as written, which opening resolves. */
@Immutable
data class MessageLink(val href: String, val preview: LinkPreview)

enum class LinkKind { Task, Resource, Faq }

/**
 * A bubble's body with its previews attached underneath, on the surface colour (white in light
 * mode) whatever the bubble's own tint, edge to edge and down to the bubble's bottom edge; the
 * bubble clips them to its corners. The previews are never narrower than a photo or file card
 * ([ChatDimens.FileCardWidth]), so every preview in a chat has the same width, and they widen
 * with the bubble when the text above is wider. [bleed] is the bubble's side padding and
 * [bottomBleed] its bottom padding, which the previews reach into.
 */
@Composable
internal fun BodyWithAttachedLinks(
    links: List<MessageLink>,
    onOpen: (href: String) -> Unit,
    bleed: Dp,
    bottomBleed: Dp,
    body: @Composable () -> Unit,
) {
    val colors = WaiveraTheme.colors
    Layout(
        contents = listOf(body, {
            Column(Modifier.background(colors.surface)) {
                links.forEach { (href, preview) ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(WaiveraSpacing.Hairline)
                            .background(colors.border),
                    )
                    LinkSection(preview, onOpen = { onOpen(href) }, inset = bleed)
                }
            }
        }),
    ) { (bodies, sections), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bodyPlaced = bodies.map { it.measure(loose) }
        val bodyWidth = bodyPlaced.maxOfOrNull { it.width } ?: 0
        val width = maxOf(bodyWidth, minOf(ChatDimens.FileCardWidth.roundToPx(), constraints.maxWidth))
        val side = bleed.roundToPx()
        val sectionPlaced = sections.map { it.measure(Constraints.fixedWidth(width + side * 2)) }
        val bodyHeight = bodyPlaced.sumOf { it.height }
        val gapPx = if (bodyHeight > 0) WaiveraSpacing.Small.roundToPx() else 0
        // The last preview's bottom padding sits in the bubble's own bottom padding.
        val height = bodyHeight + gapPx + sectionPlaced.sumOf { it.height } - bottomBleed.roundToPx()
        layout(width, height) {
            var y = 0
            bodyPlaced.forEach {
                it.placeRelative(0, y)
                y += it.height
            }
            y += gapPx
            sectionPlaced.forEach {
                it.placeRelative(-side, y)
                y += it.height
            }
        }
    }
}

/**
 * One preview: the kind and a small glyph on a quiet line with the open arrow at its end, then
 * the title in bold over the full width, then one line of detail. Text starts where the
 * message text starts, so the bubble keeps one left edge. Tasks open in the app (chevron);
 * resources and FAQs on the web (open-external arrow).
 */
@Composable
private fun LinkSection(preview: LinkPreview, onOpen: () -> Unit, inset: Dp) {
    val colors = WaiveraTheme.colors
    val opens = preview !is LinkPreview.Loading && preview !is LinkPreview.Unavailable
    val unavailable = preview is LinkPreview.Unavailable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (opens) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(horizontal = inset, vertical = WaiveraSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall + WaiveraSpacing.XxSmall),
        ) {
            Icon(
                painter = painterResource(if (unavailable) WaiveraIcons.Lock else preview.kind.icon()),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WaiveraSizes.IconSmall),
            )
            Kicker(
                text = when (preview) {
                    is LinkPreview.Task -> "${preview.kind.label()} · ${preview.key}"
                    else -> preview.kind.label()
                },
                modifier = Modifier.weight(1f),
            )
            if (opens) {
                Icon(
                    painter = painterResource(
                        if (preview.kind == LinkKind.Task) WaiveraIcons.ChevronRight else WaiveraIcons.OpenExternal,
                    ),
                    contentDescription = null,
                    tint = colors.link,
                    modifier = Modifier.size(WaiveraSizes.IconSmall),
                )
            }
        }
        LinkBody(preview)
    }
}

/** Title and one line of detail, shared by every layout. */
@Composable
private fun LinkBody(preview: LinkPreview, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
        when (preview) {
            is LinkPreview.Loading -> {
                val pulse = rememberSkeletonPulse()
                SkeletonText(style = MaterialTheme.typography.titleSmall, widthFraction = 0.8f, pulse = pulse)
            }
            is LinkPreview.Unavailable -> Meta(stringResource(R.string.feature_chat_link_unavailable_short))
            is LinkPreview.Task -> {
                Title(preview.title)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaiveraStatusBadge(text = preview.state.label(), tone = preview.state.tone())
                    preview.due?.let {
                        Meta(stringResource(R.string.feature_chat_link_due, dueLabel(it, preview.dueShowsYear)))
                    }
                }
            }
            is LinkPreview.Resource -> {
                Title(preview.title)
                listOfNotNull(preview.provider, preview.place).takeIf { it.isNotEmpty() }
                    ?.let { Meta(it.joinToString(" · ")) }
            }
            is LinkPreview.Faq -> {
                Title(preview.title)
                preview.summary?.let { Meta(it) }
            }
        }
    }
}

@Composable
private fun Kicker(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    modifier = modifier,
    style = MaterialTheme.typography.labelSmall,
    color = WaiveraTheme.colors.textSecondary,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Title(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    color = WaiveraTheme.colors.text,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Meta(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = WaiveraTheme.colors.textSecondary,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun TaskState.label(): String = when (this) {
    TaskState.NotStarted -> stringResource(R.string.feature_chat_link_state_not_started)
    TaskState.InProgress -> stringResource(R.string.feature_chat_link_state_in_progress)
    TaskState.InReview -> stringResource(R.string.feature_chat_link_state_in_review)
    TaskState.Completed -> stringResource(R.string.feature_chat_link_state_completed)
    TaskState.Closed -> stringResource(R.string.feature_chat_link_state_closed)
    is TaskState.Other -> name.lowercase().replaceFirstChar(Char::uppercaseChar)
}

/** The Tasks tab's colours for the same states. */
private fun TaskState.tone(): BadgeTone = when (this) {
    TaskState.Completed, TaskState.Closed -> BadgeTone.Success
    TaskState.InProgress -> BadgeTone.Accent
    TaskState.InReview -> BadgeTone.Warning
    else -> BadgeTone.Neutral
}

/** "Nov 12", or "Nov 12, 2027" outside this year. */
@Composable
private fun dueLabel(day: LocalDate, withYear: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(day, withYear, locale) {
        val pattern = if (withYear) "MMM d, yyyy" else "MMM d"
        day.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

@Composable
private fun LinkKind.label(): String = stringResource(
    when (this) {
        LinkKind.Task -> R.string.feature_chat_link_task
        LinkKind.Resource -> R.string.feature_chat_link_resource
        LinkKind.Faq -> R.string.feature_chat_link_faq
    },
)

private fun LinkKind.icon(): Int = when (this) {
    LinkKind.Task -> WaiveraIcons.Check
    LinkKind.Resource -> WaiveraIcons.Location
    LinkKind.Faq -> WaiveraIcons.Info
}
