package com.waivera.waivera.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import com.waivera.waivera.core.designsystem.component.WaiveraHeroHeader
import com.waivera.waivera.core.designsystem.component.WaiveraHeroLogo
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * True while the soft keyboard takes up part of the window. Derived, so the keyboard's slide
 * recomposes only when it starts or finishes showing, not on every frame.
 */
@Composable
internal fun rememberImeVisible(): Boolean {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val visible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    return visible
}

/**
 * Keeps [requester] in view while the keyboard slides up and [active] (a field just above it has
 * focus). It follows the keyboard's inset outside composition, so the screen does not recompose
 * on each frame of the slide, and the last call wins once the keyboard settles.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeepInViewAboveKeyboard(requester: BringIntoViewRequester, active: Boolean) {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(requester, active, ime, density) {
        if (!active) return@LaunchedEffect
        snapshotFlow { ime.getBottom(density) }
            .filter { it > 0 }
            .collectLatest { requester.bringIntoView() }
    }
}

/**
 * Keeps [requester] (Sign in and Forgot password?) in view while a banner shows (UI9-02). A
 * banner appears after a tap on Sign in and pushes the button down; at 200% on a small phone it
 * went below the fold, so the person retyped the password and then had to hunt for it. The
 * scroll's extent follows the banner's expand frame by frame, outside composition, and each
 * change brings the button back into view; it does nothing while the button is already visible.
 * [banner] identifies what shows, so a new banner brings it into view again and none stops it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun KeepInViewWhileBannerShows(requester: BringIntoViewRequester, scrollState: ScrollState, banner: Any?) {
    LaunchedEffect(requester, scrollState, banner) {
        if (banner == null) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }.collectLatest { requester.bringIntoView() }
    }
}

/**
 * The sign-in frame: the brand header full bleed behind the status bar, and the curved sheet
 * rising over it with the form. One scroll holds both, so at 200% font everything is reachable,
 * and the frame pads for the keyboard before that scroll, so the focused field and the button
 * stay above it.
 *
 * The header drops to its compact height with the keyboard up, on a short window or at large
 * font, so the form starts high. The content after the form ([footer]) sits at the bottom of the
 * sheet when everything fits and follows the form when it does not.
 *
 * On first show the sheet rises and fades in and the logo fades in; under reduced motion both are
 * simply there, and the entrance never replays after rotation.
 */
@Composable
internal fun SignInFrame(
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val hero = WaiveraHero.colors
    val motion = WaiveraTheme.motion
    val imeVisible = rememberImeVisible()
    val topInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues().calculateTopPadding()
    val entrance = rememberSignInEntrance()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(hero.sheet)
            .windowInsetsPadding(WindowInsets.ime),
    ) {
        val compact = imeVisible || isLargeFontScale() || maxHeight < WaiveraSizes.CompactWindowHeight
        val headerHeight by animateDpAsState(
            targetValue = if (compact) WaiveraHero.HeaderHeightCompact else WaiveraHero.HeaderHeight,
            animationSpec = motion.layout(),
            label = "signInHeader",
        )
        val sheetMinHeight = (maxHeight - topInset - headerHeight).coerceAtLeast(WaiveraSpacing.None)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Box {
                SignInHeader(
                    height = topInset + headerHeight,
                    compact = compact,
                    logoAlpha = { entrance.logo },
                )
                SignInSheet(
                    top = topInset + headerHeight,
                    minHeight = sheetMinHeight,
                    rise = { entrance.sheet },
                    footer = footer,
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SignInHeader(height: Dp, compact: Boolean, logoAlpha: () -> Float) {
    // Drawn under the sheet's curve too, so the corners show plum, not the page.
    WaiveraHeroHeader(modifier = Modifier.height(height + WaiveraHero.SheetOverlap)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(bottom = WaiveraHero.SheetOverlap),
            contentAlignment = Alignment.Center,
        ) {
            WaiveraHeroLogo(
                height = if (compact) WaiveraHero.LockupCompact else WaiveraHero.Lockup,
                contentDescription = stringResource(R.string.feature_auth_brand),
                modifier = Modifier.graphicsLayer { alpha = logoAlpha() },
            )
        }
    }
}

@Composable
private fun SignInSheet(
    top: Dp,
    minHeight: Dp,
    rise: () -> Float,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hero = WaiveraHero.colors
    val density = LocalDensity.current
    val risePx = with(density) { WaiveraHero.EntranceRise.toPx() }
    Column(
        modifier = Modifier
            .padding(top = top)
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .graphicsLayer {
                val progress = rise()
                translationY = (1f - progress) * risePx
                alpha = progress.coerceIn(0f, 1f)
            }
            .clip(WaiveraHero.SheetShape)
            .background(hero.sheet)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(top = WaiveraSpacing.MediumLarge, bottom = WaiveraSpacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = WaiveraSizes.SheetMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
            content = content,
        )
        // Fills up to the sheet's minimum height only: the footer sits at the bottom when the form
        // is short and follows it otherwise.
        Box(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .widthIn(max = WaiveraSizes.SheetMaxWidth)
                .fillMaxWidth()
                .padding(top = WaiveraSpacing.Large),
            content = footer,
        )
    }
}

/** The entrance progress: [sheet] (spatial, may settle with a little spring) and [logo] (opacity). */
private class SignInEntrance(
    val sheetAnimatable: Animatable<Float, AnimationVector1D>,
    val logoAnimatable: Animatable<Float, AnimationVector1D>,
) {
    val sheet: Float get() = sheetAnimatable.value
    val logo: Float get() = logoAnimatable.value.coerceIn(0f, 1f)
}

@Composable
private fun rememberSignInEntrance(): SignInEntrance {
    val motion = WaiveraTheme.motion
    var played by rememberSaveable { mutableStateOf(false) }
    val start = if (played || motion.reduced) 1f else 0f
    val entrance = remember { SignInEntrance(Animatable(start), Animatable(start)) }
    LaunchedEffect(entrance) {
        if (!played) {
            played = true
            coroutineScope {
                launch { entrance.logoAnimatable.animateTo(1f, motion.state()) }
                entrance.sheetAnimatable.animateTo(1f, motion.layout())
            }
        }
    }
    return entrance
}

/**
 * "By continuing, you agree to our Terms of Service and Privacy Policy." as one typeset sentence
 * (UI9-03): every word is its own item in one flow, spaced by a real space, so it wraps where
 * text would. "our" travels with the Terms link and the full stop with Privacy Policy, so neither
 * is ever left alone on a line. Each name is a link with a full 48dp target that opens the page
 * in a Custom Tab (the app's link handler); the targets overlap the lines above and below
 * instead of spreading the sentence apart. Hidden when the build has no web base.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LegalFooter(links: LegalLinks, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodySmall.copy(color = WaiveraTheme.colors.textSecondary)
    val space = rememberWordSpace(style)
    val lead = stringResource(R.string.feature_auth_legal_lead)
    val leadWords = remember(lead) { lead.split(' ').filter(String::isNotEmpty) }
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .then(GutterPadding)
            // Room for the last line's link target, which reaches below the line.
            .padding(bottom = WaiveraSpacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(space, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // TalkBack reads the lead once, as a sentence, not word by word.
        leadWords.forEachIndexed { index, word ->
            Text(
                text = word,
                style = style,
                modifier = Modifier.clearAndSetSemantics { if (index == 0) contentDescription = lead },
            )
        }
        LegalLink(
            text = stringResource(R.string.feature_auth_legal_terms),
            lead = stringResource(R.string.feature_auth_legal_terms_lead),
            style = style,
            space = space,
            onClick = { onOpen(links.termsOfService) },
        )
        Text(text = stringResource(R.string.feature_auth_legal_and), style = style)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegalLink(
                text = stringResource(R.string.feature_auth_legal_privacy),
                style = style,
                space = space,
                onClick = { onOpen(links.privacyPolicy) },
            )
            Text(
                text = stringResource(R.string.feature_auth_legal_end),
                style = style,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/**
 * One legal link: [lead] (plain, "our") and the underlined accent name. Its target is a full
 * 48dp tall, but it reports the text's line height to the sentence around it, so the lines stay
 * at body spacing and the target overlaps them, which Compose allows.
 */
@Composable
private fun LegalLink(text: String, style: TextStyle, space: Dp, onClick: () -> Unit, lead: String? = null) {
    val lineHeight = with(LocalDensity.current) {
        if (style.lineHeight.isSp) style.lineHeight.toDp() else WaiveraSpacing.MinTouchTarget
    }
    WaiveraPressable(
        onClick = onClick,
        onClickLabel = stringResource(R.string.feature_auth_legal_open),
        shape = WaiveraShapes.Chip,
        modifier = Modifier.reportHeightAtMost(lineHeight),
    ) {
        Row(
            modifier = Modifier.heightIn(min = WaiveraSpacing.MinTouchTarget),
            horizontalArrangement = Arrangement.spacedBy(space),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lead != null) Text(text = lead, style = style)
            Text(
                text = text,
                style = style,
                color = WaiveraTheme.colors.accent,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

/** Lays the content out at its own size but tells the parent it is at most [height] tall, centred. */
private fun Modifier.reportHeightAtMost(height: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val reported = minOf(placeable.height, height.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, reported) { placeable.place(x = 0, y = (reported - placeable.height) / 2) }
}

/** The width of one space in [style], so words set as separate items sit as they would in text. */
@Composable
private fun rememberWordSpace(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, measurer, density) {
        val spaced = measurer.measure(text = "a a", style = style).size.width
        val joined = measurer.measure(text = "aa", style = style).size.width
        with(density) { (spaced - joined).coerceAtLeast(0).toDp() }
    }
}
