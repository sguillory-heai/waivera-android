package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A full screen with a top bar and an empty state. Feature modules use it for screens that are
 * routed but not built yet, so navigation, insets and deep links can be checked end to end.
 */
@Composable
fun WaiveraPlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onBack: (() -> Unit)? = null,
    account: TopBarAccount? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
    ) {
        WaiveraTopBar(title = title, onBack = onBack, account = account)
        WaiveraEmptyState(
            title = title,
            message = message,
            icon = icon,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraPlaceholderScreenPreview() {
    WaiveraTheme {
        WaiveraPlaceholderScreen(
            title = "Schedule",
            message = "Your shifts and events will appear here.",
            icon = WaiveraIcons.Calendar,
            account = TopBarAccount(name = "Ada Lovelace", onClick = {}),
        )
    }
}
