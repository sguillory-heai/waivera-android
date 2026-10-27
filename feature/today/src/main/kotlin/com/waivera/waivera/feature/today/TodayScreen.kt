package com.waivera.waivera.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.TopBarAccount
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the today screen. Owned by `:feature:today`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun TodayScreen(
    onAccountClick: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_today_today_title),
        message = stringResource(R.string.feature_today_today_placeholder),
        icon = WaiveraIcons.Today,
        account = TopBarAccount(
            name = stringResource(R.string.feature_today_account_name),
            onClick = onAccountClick,
        ),
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun TodayScreenPreview() {
    WaiveraTheme {
        TodayScreen(
            onAccountClick = {},
            onOpenConversation = { _ -> },
            onOpenSchedule = {},
            onOpenEvent = { _ -> },
        )
    }
}
