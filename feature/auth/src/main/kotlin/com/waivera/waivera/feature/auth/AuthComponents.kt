package com.waivera.waivera.feature.auth

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.waivera.waivera.core.designsystem.component.WaiveraBottomActionLayout
import com.waivera.waivera.core.designsystem.component.WaiveraHeroLogo
import com.waivera.waivera.core.designsystem.component.WaiveraHeroSection
import com.waivera.waivera.core.designsystem.component.WaiveraHeroTrail
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraSurfaceCard
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraHero
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.core.designsystem.theme.isLargeFontScale

/**
 * Full-screen frame for the gate screens. It is the single insets owner: the default [insets]
 * (`safeDrawing`) cover the status bar, cutout, navigation bar and the keyboard, and the column
 * scrolls so a focused field is never hidden behind the IME. Under a top bar, pass insets
 * without the top side. Content is width-capped so the form stays readable on tablets and in
 * landscape.
 *
 * With [hero], the screen opens on concept A's compact plum header with the Waivera lockup,
 * drawn behind the status bar, and the content sits on the page sheet that curves over it, so the
 * screens after sign-in carry on from it. The header shrinks to a band while the keyboard is up.
 *
 * [bottomBar] holds the screen's primary actions in a [WaiveraBottomActionLayout]: pinned at the
 * bottom of the screen, in thumb reach, with the content scrolling above them; after the content
 * at large font, unless [pinnedAtLargeFont] keeps a single action pinned there too.
 * [LocalCompactAuthWindow] tells the content when the window is short, so it can drop optional
 * copy.
 */
@Composable
internal fun AuthScaffold(
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.safeDrawing,
    hero: Boolean = false,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    pinnedAtLargeFont: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!hero) {
        AuthScaffoldBody(modifier, insets, bottomBar, pinnedAtLargeFont, content)
        return
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background),
    ) {
        val band = isLargeFontScale() || maxHeight < WaiveraSizes.CompactWindowHeight
        Column(modifier = Modifier.fillMaxSize()) {
            AuthHeroHeader(band = band)
            AuthScaffoldBody(
                modifier = Modifier.weight(1f),
                // The header owns the top inset.
                insets = insets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                bottomBar = bottomBar,
                pinnedAtLargeFont = pinnedAtLargeFont,
                content = content,
            )
        }
    }
}

/**
 * The compact hero: the lockup on the plum with the sign-in trails, ending in the sheet's curve.
 * It is a slim [band] at large font or on a short window, and while the keyboard is up, so the
 * form and the pinned actions keep the room.
 */
@Composable
private fun AuthHeroHeader(band: Boolean) {
    val keyboard = rememberImeVisible()
    val height by animateDpAsState(
        targetValue = if (keyboard || band) WaiveraHero.BarHeight else WaiveraHero.GateHeaderHeight,
        animationSpec = WaiveraTheme.motion.layout(),
        label = "gateHeader",
    )
    WaiveraHeroSection(trail = WaiveraHeroTrail.SignIn) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center,
        ) {
            WaiveraHeroLogo(
                height = WaiveraHero.LockupCompact,
                contentDescription = stringResource(R.string.feature_auth_brand),
            )
        }
    }
}

@Composable
private fun AuthScaffoldBody(
    modifier: Modifier,
    insets: WindowInsets,
    bottomBar: (@Composable ColumnScope.() -> Unit)?,
    pinnedAtLargeFont: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background)
            .windowInsetsPadding(insets),
        contentAlignment = Alignment.TopCenter,
    ) {
        val frame = Modifier
            .widthIn(max = WaiveraSizes.SheetMaxWidth)
            .fillMaxSize()
        CompositionLocalProvider(LocalCompactAuthWindow provides (maxHeight < WaiveraSizes.CompactWindowHeight)) {
            if (bottomBar != null) {
                WaiveraBottomActionLayout(
                    actions = bottomBar,
                    modifier = frame,
                    pinnedAtLargeFont = pinnedAtLargeFont,
                    content = content,
                )
            } else {
                Column(
                    modifier = frame
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = WaiveraSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
                    content = content,
                )
            }
        }
    }
}

/** True inside an [AuthScaffold] shorter than [WaiveraSizes.CompactWindowHeight]. */
internal val LocalCompactAuthWindow = compositionLocalOf { false }

/** Gutter padding for scaffold children that are not full-width banners. */
internal val GutterPadding = Modifier.padding(horizontal = WaiveraSpacing.Gutter)

/**
 * Icon tile, heading and supporting text at the top of a gate screen.
 *
 * [welcome] is for a screen that invites rather than reports (finishing signup, the
 * notification primer): the header sits on the brand-gradient wash. The Waivera lockup is in the
 * scaffold's hero above, so the header keeps its icon tile. Errors, offline and password screens
 * stay plain and calm.
 */
@Composable
internal fun AuthHeader(
    title: String,
    message: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tone: WaiveraTileTone = WaiveraTileTone.Brand,
    welcome: Boolean = false,
) {
    val colors = WaiveraTheme.colors
    val body: @Composable ColumnScope.() -> Unit = {
        WaiveraIconTile(icon = icon, tone = tone, size = WaiveraSizes.StateIconTile)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
    }
    val frame = modifier
        .fillMaxWidth()
        .then(GutterPadding)
    if (welcome) {
        // A plain card: under v5's gradient rule the wash is for celebrations only.
        WaiveraSurfaceCard(
            modifier = frame,
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
            content = body,
        )
    } else {
        Column(modifier = frame, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall), content = body)
    }
}

/**
 * Expands and fades banners in and out with the shared motion tokens (a fade when reduced). A
 * banner opens downward from its top, an even push of what is below, and its text fades in as
 * the space opens (M2-09). Nothing leaves where it arrives, so its fade starts at once, with no
 * fade-through wait over an empty, growing slot (M3-05).
 *
 * [gapBelow] is the space to whatever follows, for a column that does not space its children:
 * it opens and folds with the banner, so the form below never drops by the gap in one frame.
 */
@Composable
internal fun AuthAnimatedVisibility(visible: Boolean, gapBelow: Dp = 0.dp, content: @Composable () -> Unit) {
    val motion = WaiveraTheme.motion
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(motion.layout(), expandFrom = Alignment.Top) + fadeIn(motion.state()),
        exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
    ) {
        Box(Modifier.padding(bottom = gapBelow)) { content() }
    }
}

/**
 * Password input with a show/hide toggle. Visibility is UI-only state (kept across rotation)
 * and resets to hidden when the screen is recreated from scratch. Autocorrect and suggestions
 * are off, so the keyboard never learns the password.
 */
@Composable
internal fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    contentType: ContentType = ContentType.Password,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    WaiveraTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        modifier = modifier,
        errorText = errorText,
        enabled = enabled,
        contentType = contentType,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        leadingIcon = { FieldIcon(WaiveraIcons.Lock) },
        trailingIcon = {
            WaiveraIconButton(
                icon = if (visible) {
                    R.drawable.feature_auth_ic_visibility_off
                } else {
                    R.drawable.feature_auth_ic_visibility
                },
                contentDescription = stringResource(
                    if (visible) R.string.feature_auth_hide_password else R.string.feature_auth_show_password,
                ),
                onClick = { visible = !visible },
                enabled = enabled,
                tint = WaiveraTheme.colors.textSecondary,
            )
        },
    )
}

/** A field's leading glyph (concept A's mail and lock): decorative, the label names the field. */
@Composable
internal fun FieldIcon(@DrawableRes icon: Int) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(WaiveraSizes.Icon),
    )
}
