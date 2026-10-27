package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The primary and secondary actions of a one-tap screen (sign in, notification primer, password
 * change), pinned in the bottom half where a thumb reaches them. [insets] pads the bar for the
 * navigation bar and keyboard when the screen does not already own those insets. The hairline
 * divider shows only while content scrolls underneath ([showDivider]).
 */
@Composable
fun WaiveraBottomActionBar(
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    insets: WindowInsets = WindowInsets(0),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WaiveraTheme.colors.background)
            .windowInsetsPadding(insets),
    ) {
        if (showDivider) WaiveraDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WaiveraSpacing.Gutter, vertical = WaiveraSpacing.MediumSmall),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
            content = content,
        )
    }
}

/**
 * A scrolling [content] column with [actions] pinned under it in a [WaiveraBottomActionBar].
 * The content keeps its own scroll, so at 200% font everything above the bar stays reachable
 * and the primary action is always on screen.
 */
@Composable
fun WaiveraBottomActionLayout(
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    contentArrangement: Arrangement.Vertical = Arrangement.spacedBy(WaiveraSpacing.Medium),
    insets: WindowInsets = WindowInsets(0),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = WaiveraSpacing.Large),
            verticalArrangement = contentArrangement,
            content = content,
        )
        WaiveraBottomActionBar(
            showDivider = scrollState.canScrollForward,
            insets = insets,
            content = actions,
        )
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraBottomActionBarPreview() {
    WaiveraTheme {
        WaiveraBottomActionBar {
            WaiveraButton(text = "Turn on notifications", onClick = {}, fullWidth = true)
            WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text, fullWidth = true)
        }
    }
}
