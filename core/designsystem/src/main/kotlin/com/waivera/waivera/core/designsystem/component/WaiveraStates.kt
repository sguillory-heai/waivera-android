package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.waivera.waivera.core.designsystem.R
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/** Small brand illustrations for empty states: abstract, in the brand gradient, no people. */
enum class WaiveraIllustration(@param:DrawableRes internal val drawable: Int) {
    /** No conversations yet. */
    Chats(R.drawable.core_designsystem_illustration_chats),

    /** Nothing scheduled. */
    Schedule(R.drawable.core_designsystem_illustration_schedule),

    /** Everything done: the reward art. */
    CaughtUp(R.drawable.core_designsystem_illustration_caught_up),

    /** A search or filter matched nothing. */
    Search(R.drawable.core_designsystem_illustration_search),

    /** Nothing saved yet. */
    Bookmarks(R.drawable.core_designsystem_illustration_bookmarks),

    /** No activity or notifications yet. */
    Activity(R.drawable.core_designsystem_illustration_activity),

    /** No people to show. */
    Connections(R.drawable.core_designsystem_illustration_connections),
}

/**
 * The shared empty-state block: a small brand illustration (or an icon tile), title, message
 * and one clear action that invites the next step. In concept A's type: the title is the sheet's
 * 18 / 800 heading and the action is the CTA gradient. Every screen uses this so copy and spacing
 * stay consistent. [illustration] wins over [icon] when both are set.
 */
@Composable
fun WaiveraEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustration: WaiveraIllustration? = null,
) {
    StateBlock(
        title = title,
        message = message,
        modifier = modifier,
        icon = icon,
        actionLabel = actionLabel,
        onAction = onAction,
        illustration = illustration,
    )
}

/**
 * Error state with a user-facing message (never raw server text) and a retry action. The icon
 * sits on the danger container, so an error never looks like an empty state at a glance.
 *
 * [dense] trims the block's own top and bottom space, for a state inside a sheet that is
 * already padded: at 200% on a small phone the full space pushed "Try again" past the sheet's
 * edge (UI6-05).
 */
@Composable
fun WaiveraErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.core_designsystem_error_title),
    dense: Boolean = false,
) {
    StateBlock(
        title = title,
        message = message,
        modifier = modifier,
        icon = WaiveraIcons.Error,
        actionLabel = stringResource(R.string.core_designsystem_try_again),
        onAction = onRetry,
        tone = StateTone.Error,
        dense = dense,
    )
}

/** Inline loading block for small areas. Lists use [SkeletonList] instead. */
@Composable
fun WaiveraLoadingState(caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(WaiveraSpacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraBusyIndicator(size = WaiveraSizes.ProgressLarge, strokeWidth = WaiveraSizes.ProgressStrokeLarge)
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = WaiveraTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** The one full-screen loading screen, shown only by the auth gate before the first session state. */
@Composable
fun WaiveraLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        WaiveraLoadingState(caption = stringResource(R.string.core_designsystem_loading_app))
    }
}

private enum class StateTone { Neutral, Error }

@Composable
private fun StateBlock(
    title: String,
    message: String,
    modifier: Modifier,
    @DrawableRes icon: Int?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    tone: StateTone = StateTone.Neutral,
    dense: Boolean = false,
    illustration: WaiveraIllustration? = null,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = WaiveraSpacing.Large,
                vertical = if (dense) WaiveraSpacing.Small else WaiveraSpacing.ExtraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        if (illustration != null) {
            Image(
                painter = painterResource(illustration.drawable),
                contentDescription = null,
                modifier = Modifier.size(WaiveraSizes.Illustration),
            )
        } else if (icon != null) {
            WaiveraIconTile(
                icon = icon,
                tone = if (tone == StateTone.Error) WaiveraTileTone.Danger else WaiveraTileTone.Brand,
                size = WaiveraSizes.StateIconTile,
                iconSize = WaiveraSizes.StateIconGlyph,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            WaiveraButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = WaiveraSpacing.Small),
            )
        }
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraStatesPreview() {
    WaiveraTheme {
        Column {
            WaiveraEmptyState(
                title = "No conversations yet",
                message = "Start one with a teammate and it will show up here.",
                illustration = WaiveraIllustration.Chats,
                actionLabel = "New chat",
                onAction = {},
            )
            WaiveraEmptyState(
                title = "Nothing here yet",
                message = "New messages and mentions will show up here.",
                icon = WaiveraIcons.Check,
            )
            WaiveraErrorState(message = "We couldn't load your conversations. Check your connection.", onRetry = {})
        }
    }
}
