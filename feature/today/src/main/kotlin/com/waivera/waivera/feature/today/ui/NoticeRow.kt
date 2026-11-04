package com.waivera.waivera.feature.today.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.data.model.NoticeKind
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.component.tint
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.today.R
import com.waivera.waivera.feature.today.model.PastLabel
import java.time.LocalTime

/**
 * A notification row, in the inbox and under Needs you: the kind's tile, "Kind · time" in the
 * kind's colour, the title, then the message. Unread rows are bold; a row that opens on the web
 * app shows the open-outside mark instead of the chevron.
 */
@Composable
internal fun NoticeRow(
    kind: NoticeKind,
    title: String,
    message: String,
    at: PastLabel?,
    unread: Boolean,
    opensOutside: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formats = rememberTodayFormats()
    val style = kind.style
    val label = stringResource(style.label)
    val tint = style.tone.tint(WaiveraTheme.colors)
    val headline = title.ifBlank { label }
    val supporting = message.takeIf { it.isNotBlank() && it != headline }
    val spokenTime = at?.let { pastSpokenText(it, formats) }
    val spoken = joined(joined(joined(label, headline), supporting), spokenTime)
    TodayRow(
        headline = headline,
        description = if (unread) stringResource(R.string.feature_today_activity_unread_prefix, spoken) else spoken,
        onClick = onClick,
        onClickLabel = stringResource(
            if (opensOutside) R.string.feature_today_notice_open_web else R.string.feature_today_notice_open,
        ),
        eyebrow = joined(label, at?.let { pastText(it, formats) }),
        eyebrowColor = tint.content,
        supporting = supporting,
        emphasized = unread,
        showChevron = !opensOutside,
        modifier = modifier,
        leading = { TodayTile(icon = style.icon, tone = style.tone) },
        trailing = if (opensOutside) {
            {
                Icon(
                    painter = painterResource(WaiveraIcons.OpenExternal),
                    contentDescription = null,
                    tint = WaiveraTheme.colors.textSecondary,
                    modifier = Modifier.padding(WaiveraSpacing.ExtraSmall),
                )
            }
        } else {
            null
        },
    )
}

internal data class NoticeStyle(
    @param:DrawableRes val icon: Int,
    val tone: WaiveraTileTone,
    @param:StringRes val label: Int,
)

internal val NoticeKind.style: NoticeStyle
    get() = when (this) {
        NoticeKind.TaskAssigned ->
            NoticeStyle(
                R.drawable.feature_today_ic_task,
                WaiveraTileTone.Amber,
                R.string.feature_today_notice_task_assigned,
            )
        NoticeKind.TaskActivity ->
            NoticeStyle(
                R.drawable.feature_today_ic_activity,
                WaiveraTileTone.Amber,
                R.string.feature_today_notice_task_activity,
            )
        NoticeKind.Approval ->
            NoticeStyle(R.drawable.feature_today_ic_task, WaiveraTileTone.Rose, R.string.feature_today_notice_approval)
        NoticeKind.Meeting -> NoticeStyle(
            WaiveraIcons.Calendar,
            WaiveraTileTone.Brand,
            R.string.feature_today_notice_meeting,
        )
        NoticeKind.Signature ->
            NoticeStyle(
                R.drawable.feature_today_ic_signature,
                WaiveraTileTone.Rose,
                R.string.feature_today_notice_signature,
            )
        NoticeKind.Survey -> NoticeStyle(
            WaiveraIcons.Announcement,
            WaiveraTileTone.Danger,
            R.string.feature_today_notice_survey,
        )
        NoticeKind.TimeOff -> NoticeStyle(
            WaiveraIcons.Check,
            WaiveraTileTone.Teal,
            R.string.feature_today_notice_time_off,
        )
        NoticeKind.Other -> NoticeStyle(
            WaiveraIcons.Notifications,
            WaiveraTileTone.Brand,
            R.string.feature_today_notice_update,
        )
    }

@WaiveraPreviews
@Composable
private fun NoticeRowPreview() {
    WaiveraTheme {
        NoticeRow(
            kind = NoticeKind.Approval,
            title = "Task 42 is ready for your approval",
            message = "Priya Raman submitted task 42",
            at = PastLabel.TodayAt(LocalTime.of(14, 14)),
            unread = true,
            opensOutside = true,
            onClick = {},
        )
    }
}
