package com.waivera.waivera.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Shared spring specs. Springs keep gesture velocity and can be interrupted, so
 * they are the default for all UI motion. Bounce stays low for everyday UI.
 */
object WaiveraMotion {
    /** Position and size changes: list items settling, sheets, shared elements. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Quick spatial feedback such as a pressed control scaling down. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Colour and opacity changes. Never overshoots. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
