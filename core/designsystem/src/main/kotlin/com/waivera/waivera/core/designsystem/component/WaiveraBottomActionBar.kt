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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.LARGE_FONT_SCALE
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/**
 * The primary and secondary actions of a one-tap screen (sign in, notification primer, password
 * change). [insets] pads the bar for the navigation bar and keyboard when the screen does not
 * already own those insets. The hairline divider is for bars that sit over scrolling content
 * ([showDivider]); inside [WaiveraBottomActionLayout] the fade marks that edge instead, so it is off.
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
 * A [content] column with [actions] under it.
 *
 * Up to [LARGE_FONT_SCALE] the actions are pinned at the bottom of the screen, in thumb reach,
 * and the content scrolls in the space above them. While more content is below, a fade at the
 * bottom of that scrolling area (never over a button) shows that it scrolls; scrolled to the
 * end, the last line sits fully above the bar.
 *
 * Above [LARGE_FONT_SCALE] a pinned bar of two large buttons would leave too little room to
 * read, so the actions flow after the content in one scroll instead. A flexible spacer still
 * pushes them to the bottom when everything fits. The fade then shows only while the actions
 * are entirely below the viewport edge, so it never covers one.
 *
 * [insets] shrink the viewport (navigation bar, keyboard) when the screen does not own them
 * already. Text fields inside bring themselves into view when focused or in error (see
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
    val frame = modifier
        .fillMaxSize()
        .windowInsetsPadding(insets)
    if (bottomActionMode(isLargeFontScale()) == BottomActionMode.Pinned) {
        PinnedActionLayout(frame, scrollState, contentArrangement, actions, content)
    } else {
        InlineActionLayout(frame, scrollState, contentArrangement, actions, content)
    }
}

/** How [WaiveraBottomActionLayout] places its actions. */
internal enum class BottomActionMode {
    /** In a bar under the scrolling content. */
    Pinned,

    /** After the content, in the same scroll. */
    Inline,
}

/** Pinned up to [LARGE_FONT_SCALE]; inline above it, where a pinned bar would crowd the content out. */
internal fun bottomActionMode(largeFont: Boolean): BottomActionMode =
    if (largeFont) BottomActionMode.Inline else BottomActionMode.Pinned

@Composable
private fun PinnedActionLayout(
    modifier: Modifier,
    scrollState: ScrollState,
    contentArrangement: Arrangement.Vertical,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(vertical = WaiveraSpacing.Large),
                verticalArrangement = contentArrangement,
                content = content,
            )
            // Inside the scrolling area, so it fades content into the bar and never a button.
            WaiveraScrollFadeEdge(
                visible = scrollState.canScrollForward,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        WaiveraBottomActionBar(showDivider = false, content = actions)
    }
}

@Composable
private fun InlineActionLayout(
    modifier: Modifier,
    scrollState: ScrollState,
    contentArrangement: Arrangement.Vertical,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var viewportBottom by remember { mutableFloatStateOf(0f) }
    var actionsTop by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier = modifier.onGloballyPositioned { viewportBottom = it.boundsInRoot().bottom },
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
            WaiveraBottomActionBar(
                showDivider = false,
                modifier = Modifier.onGloballyPositioned { actionsTop = it.positionInRoot().y },
                content = actions,
            )
        }
        WaiveraScrollFadeEdge(
            visible = scrollState.canScrollForward && actionsTop >= viewportBottom,
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
