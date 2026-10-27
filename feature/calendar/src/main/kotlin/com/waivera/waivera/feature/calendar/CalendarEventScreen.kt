package com.waivera.waivera.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the event screen. Owned by `:feature:calendar`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun CalendarEventScreen(
    eventId: String,
    onBack: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    onOpenPerson: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_calendar_calendar_event_title),
        message = stringResource(R.string.feature_calendar_calendar_event_placeholder) + "\n" +
            stringResource(R.string.feature_calendar_calendar_event_reference, eventId.toString()),
        icon = WaiveraIcons.Schedule,
        onBack = onBack,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun CalendarEventScreenPreview() {
    WaiveraTheme {
        CalendarEventScreen(
            eventId = "preview",
            onBack = {},
            onOpenConversation = { _ -> },
            onOpenPerson = { _ -> },
        )
    }
}
