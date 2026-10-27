package com.waivera.waivera.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraPlaceholderScreen
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Entry point for the sign in screen. Owned by `:feature:auth`; routed by `:app`.
 *
 * Every navigation lambda is part of the routed contract, even before the screen body uses it.
 */
@Suppress("UnusedParameter")
@Composable
fun SignInScreen(
    configurationMissing: Boolean,
    modifier: Modifier = Modifier,
) {
    WaiveraPlaceholderScreen(
        title = stringResource(R.string.feature_auth_sign_in_title),
        message = stringResource(R.string.feature_auth_sign_in_placeholder),
        icon = WaiveraIcons.Person,
        modifier = modifier,
    )
}

@WaiveraPreviews
@Composable
private fun SignInScreenPreview() {
    WaiveraTheme {
        SignInScreen(configurationMissing = false)
    }
}
