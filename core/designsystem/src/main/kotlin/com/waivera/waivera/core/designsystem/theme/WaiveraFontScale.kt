package com.waivera.waivera.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/**
 * Font scale above which layouts switch to their large-text form: banners go compact, secondary
 * copy drops and actions stack. 130% sits between the system's "Large" and "Largest" steps, so
 * the default and one step up keep the regular layout.
 */
const val LARGE_FONT_SCALE: Float = 1.3f

/** True when the person's font scale is above [LARGE_FONT_SCALE]. */
@Composable
@ReadOnlyComposable
fun isLargeFontScale(): Boolean = LocalDensity.current.fontScale > LARGE_FONT_SCALE

/**
 * Font scale from which a layout takes its largest-text form: 150%, halfway to the 200% the app
 * supports. Short fixed words (tab labels, counts) stop growing here, and rows that could break
 * a word stack instead.
 */
const val EXTRA_LARGE_FONT_SCALE: Float = 1.5f

/** True when the person's font scale is [EXTRA_LARGE_FONT_SCALE] or more. */
@Composable
@ReadOnlyComposable
fun isExtraLargeFontScale(): Boolean = LocalDensity.current.fontScale >= EXTRA_LARGE_FONT_SCALE
