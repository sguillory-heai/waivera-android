package com.waivera.waivera.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraColors
import com.waivera.waivera.core.designsystem.theme.WaiveraElevation
import com.waivera.waivera.core.designsystem.theme.WaiveraPalette
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import com.waivera.waivera.core.designsystem.theme.swapEnterFade
import com.waivera.waivera.core.designsystem.theme.swapExitFade

/**
 * The v5 family (owner pick, option C): [Primary] is brand violet with a 1dp light top edge, a
 * faint sheen over its top half and a soft violet glow below, with white text; [Secondary] is a
 * raised white button (the surface with a hairline and a faint shadow) with the body ink; and
 * [Destructive] is the primary's construction in red. The sheen is a white highlight fading out
 * on a solid fill, never the brand gradient, and it is tuned so the white label keeps 4.5:1 at
 * its brightest point in both themes.
 *
 * [Tonal] is the quiet filled style (accent-soft fill) for an action that matters but should not
 * shout, such as joining a call from a banner. [TonalOnWash] is the same quiet style drawn on the
 * brand wash ("Send wishes" on a celebration card), where the accent-soft fill vanished (about
 * 1.05:1, UI9-08): its fill keeps 1.3:1 against every wash point. [DestructiveOutline] is a destructive action that
 * a confirmation sheet already guards (Sign out), so the button itself stays light.
 *
 * [Selected] and [Unselected] are the two sides of a single-choice pair (Going / Not going): the
 * chosen one has the accent-soft fill and an accent outline, the other a subtle outline and
 * regular-weight secondary text, so the chosen one is always the heavier of the two, in dark
 * mode too (UI4-04).
 */
enum class WaiveraButtonVariant {
    Primary,
    Secondary,
    Tonal,
    TonalOnWash,
    Destructive,
    DestructiveOutline,
    Text,
    Selected,
    Unselected,
}

/**
 * How big a [WaiveraButton] is: [Large] is the 56dp full-width action a screen exists for (Sign in).
 * [Compact] keeps the 48dp height and the full-size label but tightens the side padding, the icon
 * and its gap, for a short run of actions that has to share one line in a narrow column (a failed
 * send's Discard, Edit and Retry on a 360dp phone).
 */
enum class WaiveraButtonSize { Regular, Large, Compact }

/**
 * The app's button.
 *
 * Disabled buttons keep a readable label (`textSecondary` on `surfaceMuted` with a hairline
 * border, the Material on-surface pattern) instead of fading the accent, so the person can
 * still read what the button does. A non-null [loadingText] ("Signing out…") is the loading
 * state: the button keeps its size, the label fades out as a small spinner and the text pop in
 * its place, it ignores taps and announces it (never a blocking overlay). Loading has no other switch, so a busy button
 * always says what it is doing (UI2-21).
 *
 * An enabled [Primary][WaiveraButtonVariant.Primary] button is the brand violet with white text
 * in both themes: 6.8 / 5.3:1 on the plain fill, and 5.5 / 4.6:1 under the sheen's brightest
 * point (v5 decision 2, option C). A disabled button drops the sheen, the edge, the glow and the
 * lift; a busy one keeps them, as its fill stays. Never dark ink on a pastel fill.
 *
 * [size] [WaiveraButtonSize.Large] is the screen's one big action: 56dp, full width, CTA corners.
 *
 * [maxLines] caps the label for a short fixed label that must not wrap inside its own button
 * ("Send wishes" on a narrow card, UI10-05); it ends in an ellipsis rather than a second line.
 */
@Composable
fun WaiveraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WaiveraButtonVariant = WaiveraButtonVariant.Primary,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
    loadingText: String? = null,
    maxLines: Int = Int.MAX_VALUE,
    size: WaiveraButtonSize = WaiveraButtonSize.Regular,
) {
    val colors = WaiveraTheme.colors
    val style = variant.styleFrom(colors)
    val large = size == WaiveraButtonSize.Large
    val compact = size == WaiveraButtonSize.Compact
    val shape = if (large) WaiveraShapes.Cta else WaiveraShapes.Button
    val interactionSource = remember { MutableInteractionSource() }
    val press = rememberPressFeedback(interactionSource)
    val loading = loadingText != null
    val finish = if (enabled) variant.finish else ButtonFinish.Flat
    val motion = WaiveraTheme.motion
    // A choice that flips (Going / Not going) or a button that becomes enabled changes its fill on
    // the state token instead of snapping; at rest the colours are exactly the variant's.
    val container by animateColorAsState(
        targetValue = if (enabled) style.container else style.disabledContainer,
        animationSpec = motion.state(),
        label = "buttonContainer",
    )
    val content by animateColorAsState(
        targetValue = if (enabled) style.content else colors.textSecondary,
        animationSpec = motion.state(),
        label = "buttonContent",
    )
    // The label fades out as the progress pops in, in the same space, so the size never changes.
    // Fade-through order (M2-02): whichever side leaves drops off first and the arriving side's
    // fade waits for it, so the label and the progress never show at half strength together.
    val labelAlpha by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = if (loading) motion.swapExitFade() else motion.swapEnterFade(),
        label = "buttonLabelAlpha",
    )
    val progressLabel = rememberLatestNonNull(loadingText)
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .pressFeedback(press, enabled = enabled, shape = shape)
            .buttonLift(finish, shape, colors)
            .heightIn(min = if (large) WaiveraSizes.CtaHeight else WaiveraSizes.ButtonMinHeight)
            .then(if (fullWidth || large) Modifier.fillMaxWidth() else Modifier)
            .buttonSheen(finish, shape, colors)
            .semantics { if (loadingText != null) stateDescription = loadingText },
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        border = buttonBorder(variant, enabled, colors),
        contentPadding = PaddingValues(
            horizontal = if (compact) WaiveraSpacing.MediumSmall else WaiveraSpacing.MediumLarge,
            vertical = WaiveraSpacing.MediumSmall,
        ),
        interactionSource = interactionSource,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ButtonLabel(
                text = text,
                maxLines = maxLines,
                leadingIcon = leadingIcon,
                regularWeight = variant == WaiveraButtonVariant.Unselected,
                compact = compact,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            )
            WaiveraPopVisibility(visible = loading, swapsInPlace = true) {
                ButtonProgress(
                    label = progressLabel.orEmpty(),
                    color = if (enabled) style.content else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ButtonLabel(
    text: String,
    maxLines: Int,
    @DrawableRes leadingIcon: Int?,
    regularWeight: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compact) WaiveraSpacing.ExtraSmall else WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A glyph that arrives (the check on a chosen answer) pops in rather than appearing.
        val iconPop = rememberPopScale(key = leadingIcon, enabled = leadingIcon != null)
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier
                    .size(if (compact) WaiveraSizes.IconSmall else WaiveraSizes.Icon)
                    .popScale(iconPop),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (regularWeight) FontWeight.Normal else null,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Spinner and the progress label, side by side. The button's semantics announce it. At large
 * font the label can wrap, and a spinner beside a wrapped, centred label sat stranded at the
 * edge (UI5-10), while stacking it above the label read as a layout fault; there the label
 * alone says what is happening, and the button still ignores taps.
 */
@Composable
private fun ButtonProgress(label: String, color: Color) {
    Row(
        modifier = Modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The fill stays solid while busy; the spinner is the label's ink on a faint track of it.
        if (!isLargeFontScale()) {
            WaiveraBusyIndicator(
                color = color,
                trackColor = color.copy(alpha = BUSY_TRACK_ALPHA),
                size = WaiveraSizes.IconSmall,
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
    }
}

/**
 * How a button is finished beyond its fill (v5 decision 2, option C): [Sheen] is a filled button's
 * sheen and top edge, [Glow] adds the primary's violet glow, and [Raised] is the white
 * secondary's faint lift. Everything else is [Flat].
 */
private enum class ButtonFinish { Flat, Sheen, Glow, Raised }

private val WaiveraButtonVariant.finish: ButtonFinish
    get() = when (this) {
        WaiveraButtonVariant.Primary -> ButtonFinish.Glow
        WaiveraButtonVariant.Destructive -> ButtonFinish.Sheen
        WaiveraButtonVariant.Secondary -> ButtonFinish.Raised
        else -> ButtonFinish.Flat
    }

/** The primary's soft violet glow and the secondary's faint lift, drawn under the button. */
private fun Modifier.buttonLift(finish: ButtonFinish, shape: Shape, colors: WaiveraColors): Modifier = when (finish) {
    ButtonFinish.Glow -> shadow(
        elevation = WaiveraElevation.ButtonGlow,
        shape = shape,
        clip = false,
        ambientColor = colors.buttonGlow,
        spotColor = colors.buttonGlow,
    )
    ButtonFinish.Raised -> shadow(
        elevation = WaiveraElevation.ButtonRaise,
        shape = shape,
        clip = false,
        ambientColor = colors.buttonRaise,
        spotColor = colors.buttonRaise,
    )
    else -> this
}

/**
 * The filled button's light: over the drawn button, a 1dp [WaiveraColors.buttonEdge] along the
 * top and a [WaiveraColors.buttonSheen] that fades out by [WaiveraPalette.BUTTON_SHEEN_END] of
 * the height, clipped to the button's shape. It is drawn over the label too; white over the white
 * label leaves it white, so the label's contrast is the white against the lightened fill.
 */
private fun Modifier.buttonSheen(finish: ButtonFinish, shape: Shape, colors: WaiveraColors): Modifier {
    if (finish != ButtonFinish.Sheen && finish != ButtonFinish.Glow) return this
    val edge = colors.buttonEdge
    val sheen = colors.buttonSheen
    return drawWithContent {
        drawContent()
        if (size.height <= 0f) return@drawWithContent
        val edgeEnd = (WaiveraSpacing.Hairline.toPx() / size.height).coerceIn(0f, WaiveraPalette.BUTTON_SHEEN_END)
        val brush = Brush.verticalGradient(
            0f to edge,
            edgeEnd to edge,
            edgeEnd to sheen,
            WaiveraPalette.BUTTON_SHEEN_END to sheen.copy(alpha = 0f),
        )
        drawOutline(outline = shape.createOutline(size, layoutDirection, this), brush = brush)
    }
}

private fun buttonBorder(variant: WaiveraButtonVariant, enabled: Boolean, colors: WaiveraColors): BorderStroke? = when {
    variant == WaiveraButtonVariant.Text -> null
    !enabled -> BorderStroke(WaiveraSpacing.Hairline, colors.border)
    variant == WaiveraButtonVariant.Secondary -> BorderStroke(WaiveraSpacing.Hairline, colors.border)
    variant == WaiveraButtonVariant.DestructiveOutline -> BorderStroke(WaiveraSpacing.Hairline, colors.danger)
    variant == WaiveraButtonVariant.Selected -> BorderStroke(WaiveraSizes.SelectedOutline, colors.accent)
    variant == WaiveraButtonVariant.Unselected -> BorderStroke(WaiveraSpacing.Hairline, colors.border)
    else -> null
}

/** The busy spinner's track inside a button, as a share of the label's ink (v5: white at 35%). */
private const val BUSY_TRACK_ALPHA = 0.35f

private class ButtonStyle(val container: Color, val content: Color, val disabledContainer: Color)

private fun WaiveraButtonVariant.styleFrom(colors: WaiveraColors): ButtonStyle = when (this) {
    WaiveraButtonVariant.Primary -> ButtonStyle(colors.accentFill, colors.onAccent, colors.surfaceMuted)
    // v5 option C: the raised white button, the surface with the body ink, a hairline and a lift.
    WaiveraButtonVariant.Secondary -> ButtonStyle(colors.surface, colors.text, colors.surfaceMuted)
    WaiveraButtonVariant.Tonal -> ButtonStyle(colors.accentSoft, colors.onAccentSoft, colors.surfaceMuted)
    WaiveraButtonVariant.TonalOnWash -> ButtonStyle(colors.tonalOnWash, colors.onAccentSoft, colors.surfaceMuted)
    WaiveraButtonVariant.Destructive -> ButtonStyle(colors.dangerFill, colors.onDangerFill, colors.surfaceMuted)
    WaiveraButtonVariant.DestructiveOutline -> ButtonStyle(colors.surface, colors.dangerText, colors.surfaceMuted)
    WaiveraButtonVariant.Text -> ButtonStyle(Color.Transparent, colors.accent, Color.Transparent)
    WaiveraButtonVariant.Selected -> ButtonStyle(colors.accentSoft, colors.onAccentSoft, colors.surfaceMuted)
    // Primary text, not secondary: in dark mode the secondary grey matched a disabled button and
    // the other answer no longer read as tappable (UI5-13).
    WaiveraButtonVariant.Unselected -> ButtonStyle(colors.surface, colors.text, colors.surface)
}

@WaiveraPreviews
@Composable
private fun WaiveraButtonPreview() {
    WaiveraTheme {
        Column(
            Modifier.padding(WaiveraSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            WaiveraButton(text = "Sign in", onClick = {}, size = WaiveraButtonSize.Large)
            WaiveraButton(text = "Send", onClick = {}, fullWidth = true)
            WaiveraButton(text = "Sign out", onClick = {}, loadingText = "Signing out…")
            WaiveraButton(text = "Cancel", onClick = {}, variant = WaiveraButtonVariant.Secondary)
            WaiveraButton(text = "Join on web", onClick = {}, variant = WaiveraButtonVariant.Tonal)
            WaiveraButton(text = "Delete message", onClick = {}, variant = WaiveraButtonVariant.Destructive)
            WaiveraButton(text = "Sign out", onClick = {}, variant = WaiveraButtonVariant.DestructiveOutline)
            WaiveraButton(text = "Not now", onClick = {}, variant = WaiveraButtonVariant.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small)) {
                WaiveraButton(
                    text = "Not going",
                    onClick = {},
                    variant = WaiveraButtonVariant.Unselected,
                    modifier = Modifier.weight(1f),
                )
                WaiveraButton(
                    text = "Going",
                    onClick = {},
                    variant = WaiveraButtonVariant.Selected,
                    leadingIcon = WaiveraIcons.Check,
                    modifier = Modifier.weight(1f),
                )
            }
            WaiveraButton(text = "New chat", onClick = {}, leadingIcon = WaiveraIcons.Add, enabled = false)
        }
    }
}
