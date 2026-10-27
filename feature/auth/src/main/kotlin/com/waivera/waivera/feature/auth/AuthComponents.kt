package com.waivera.waivera.feature.auth

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.waivera.waivera.core.designsystem.component.WaiveraIconButton
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraTextField
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Full-screen frame for the gate screens. It is the single insets owner: the default [insets]
 * (`safeDrawing`) cover the status bar, cutout, navigation bar and the keyboard, and the column
 * scrolls so a focused field is never hidden behind the IME. Under a top bar, pass insets
 * without the top side. Content is width-capped so
 * the form stays readable on tablets and in landscape.
 */
@Composable
internal fun AuthScaffold(
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WaiveraTheme.colors.background)
            .windowInsetsPadding(insets),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = WaiveraSizes.SheetMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = WaiveraSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Medium),
            content = content,
        )
    }
}

/** Gutter padding for scaffold children that are not full-width banners. */
internal val GutterPadding = Modifier.padding(horizontal = WaiveraSpacing.Gutter)

/** Icon tile, heading and supporting text at the top of a gate screen. */
@Composable
internal fun AuthHeader(
    title: String,
    message: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(GutterPadding),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        WaiveraIconTile(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
    }
}

/** Expands and fades banners in and out with the shared motion tokens (a fade when reduced). */
@Composable
internal fun AuthAnimatedVisibility(visible: Boolean, content: @Composable () -> Unit) {
    val motion = WaiveraTheme.motion
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(motion.layout()) + fadeIn(motion.state()),
        exit = shrinkVertically(motion.layout()) + fadeOut(motion.state()),
    ) {
        content()
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
