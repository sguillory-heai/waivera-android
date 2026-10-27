package com.waivera.waivera.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the person screen. Owned by `:feature:profile`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun PersonDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenConversation: (conversationId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_profile_person_detail_title),
        message = stringResource(R.string.feature_profile_person_detail_placeholder) + "\n" +
            stringResource(R.string.feature_profile_person_detail_reference, userId.toString()),
        icon = WaiveraIcons.Person,
        onBack = onBack,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun PersonDetailScreenPreview() {
    WaiveraTheme {
        PersonDetailScreen(userId = "preview", onBack = {}, onOpenConversation = { _ -> })
    }
}
