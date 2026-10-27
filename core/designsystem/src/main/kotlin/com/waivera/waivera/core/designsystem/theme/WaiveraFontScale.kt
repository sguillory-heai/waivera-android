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
