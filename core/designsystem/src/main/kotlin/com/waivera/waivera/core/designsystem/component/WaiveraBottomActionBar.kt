package com.waivera.waivera.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The primary and secondary actions of a one-tap screen (sign in, notification primer, password
 * change). [insets] pads the bar for the navigation bar and keyboard when the screen does not
 * already own those insets. The hairline divider is for bars that sit over scrolling content
 * ([showDivider]); inside [WaiveraBottomActionLayout] nothing scrolls under the bar, so it is off.
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
 * A [content] column with [actions] under it, in one scroll.
 *
 * When everything fits, a flexible spacer pushes the actions to the bottom of the screen, in
 * thumb reach, so they look pinned. When it does not fit (a small phone, a banner, 200% font,
 * the keyboard), the actions flow after the content instead of covering it, so no line is ever
 * cut under a bar. While more content is below the viewport edge, a fade shows that the screen
 * scrolls. [insets] shrink the viewport (navigation bar, keyboard) when the screen does not own
 * them already. Text fields inside bring themselves into view when focused or in error (see
 * [WaiveraTextField]).
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(insets),
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .heightIn(min = viewportHeight),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = WaiveraSpacing.Large),
                verticalArrangement = contentArrangement,
                content = content,
            )
            // Weight inside a scrolling column fills up to the min height only: it pins the
            // actions to the bottom when the content is short and collapses to 0 otherwise.
            Spacer(modifier = Modifier.weight(1f))
            WaiveraBottomActionBar(showDivider = false, content = actions)
        }
        WaiveraScrollFadeEdge(
            visible = scrollState.canScrollForward,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * A fade from transparent to the screen background at a viewport's bottom edge, shown while
 * more content is below. It draws only; touches pass through to the content.
 */
@Composable
fun WaiveraScrollFadeEdge(visible: Boolean, modifier: Modifier = Modifier) {
    val motion = WaiveraTheme.motion
    val background = WaiveraTheme.colors.background
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.state()),
        exit = fadeOut(motion.state()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WaiveraSizes.FadeEdge)
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
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

@WaiveraPreviews
@Composable
private fun WaiveraBottomActionLayoutPreview() {
    WaiveraTheme {
        WaiveraBottomActionLayout(
            actions = {
                WaiveraButton(text = "Sign in", onClick = {}, fullWidth = true)
                WaiveraButton(
                    text = "Forgot password?",
                    onClick = {},
                    variant = WaiveraButtonVariant.Text,
                    fullWidth = true,
                )
            },
        ) {
            val gutter = Modifier.padding(horizontal = WaiveraSpacing.Gutter)
            WaiveraTextField(value = "", onValueChange = {}, label = "Email", modifier = gutter)
            WaiveraTextField(value = "", onValueChange = {}, label = "Password", modifier = gutter)
        }
    }
}
