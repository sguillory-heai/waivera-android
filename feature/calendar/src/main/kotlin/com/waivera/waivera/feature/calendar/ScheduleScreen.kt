package com.waivera.waivera.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the schedule screen. Owned by `:feature:calendar`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun ScheduleScreen(onAccountClick: () -> Unit, onOpenEvent: (eventId: String) -> Unit, modifier: Modifier = Modifier) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_calendar_schedule_title),
        message = stringResource(R.string.feature_calendar_schedule_placeholder),
        icon = WaiveraIcons.Calendar,
        account = TopBarAccount(
            name = stringResource(R.string.feature_calendar_account_name),
            onClick = onAccountClick,
        ),
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun ScheduleScreenPreview() {
    WaiveraTheme {
        ScheduleScreen(onAccountClick = {}, onOpenEvent = { _ -> })
    }
}
