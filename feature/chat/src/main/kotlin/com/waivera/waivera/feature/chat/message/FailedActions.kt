package com.waivera.waivera.feature.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.feature.chat.R

/**
 * How a failed send's actions sit: [OneRow] is the board's single end-aligned line (Discard,
 * Edit, Retry). When that line does not fit, Retry, the one way forward, keeps a line of its own
 * on top and the secondaries go under it ([RetryAbove]), or one per line ([Stacked]) if even the
 * two of them cannot share one. The primary never wraps below the secondaries.
 */
internal enum class FailedActionsArrangement { OneRow, RetryAbove, Stacked }

internal fun failedActionsArrangement(
    secondaryWidths: List<Int>,
    retryWidth: Int,
    gap: Int,
    maxWidth: Int,
): FailedActionsArrangement {
    val secondaryRow = secondaryWidths.sum() + gap * (secondaryWidths.size - 1).coerceAtLeast(0)
    val oneRow = secondaryRow + retryWidth + if (secondaryWidths.isEmpty()) 0 else gap
    return when {
        oneRow <= maxWidth -> FailedActionsArrangement.OneRow
        secondaryRow <= maxWidth -> FailedActionsArrangement.RetryAbove
        else -> FailedActionsArrangement.Stacked
    }
}

/**
 * Discard and Edit (raised secondary) and Retry (primary with its refresh glyph), end-aligned, in
 * compact buttons so the three share one line in a 360dp phone's bubble column at 100% font
 * (v5 decision 2, option C). Edit is left out for an attachment, which cannot be edited.
 */
@Composable
internal fun FailedActionsRow(
    showEdit: Boolean,
    onDiscard: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            WaiveraButton(
                text = stringResource(R.string.feature_chat_failed_discard),
                onClick = onDiscard,
                variant = WaiveraButtonVariant.Secondary,
                size = WaiveraButtonSize.Compact,
            )
            if (showEdit) {
                WaiveraButton(
                    text = stringResource(R.string.feature_chat_failed_edit),
                    onClick = onEdit,
                    variant = WaiveraButtonVariant.Secondary,
                    size = WaiveraButtonSize.Compact,
                )
            }
            // Retry is always the last child, so the layout below can find it.
            WaiveraButton(
                text = stringResource(R.string.feature_chat_failed_retry),
                onClick = onRetry,
                leadingIcon = WaiveraIcons.Refresh,
                size = WaiveraButtonSize.Compact,
            )
        },
    ) { measurables, constraints ->
        val gap = WaiveraSpacing.ExtraSmall.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(loose) }
        val retry = placeables.last()
        val secondaries = placeables.dropLast(1)
        val arrangement = failedActionsArrangement(
            secondaryWidths = secondaries.map { it.width },
            retryWidth = retry.width,
            gap = gap,
            maxWidth = constraints.maxWidth,
        )
        // Each line, top to bottom, holds placeables laid out start to end and is end-aligned.
        val lines = when (arrangement) {
            FailedActionsArrangement.OneRow -> listOf(placeables)
            FailedActionsArrangement.RetryAbove -> listOf(listOf(retry), secondaries)
            FailedActionsArrangement.Stacked -> listOf(listOf(retry)) + secondaries.map { listOf(it) }
        }.filter { it.isNotEmpty() }
        val lineWidths = lines.map { line -> line.sumOf { it.width } + gap * (line.size - 1) }
        val lineHeights = lines.map { line -> line.maxOf { it.height } }
        val width = lineWidths.max().coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (lineHeights.sum() + gap * (lines.size - 1)).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            lines.forEachIndexed { index, line ->
                var x = width - lineWidths[index]
                line.forEach { placeable ->
                    placeable.placeRelative(x, y + (lineHeights[index] - placeable.height) / 2)
                    x += placeable.width + gap
                }
                y += lineHeights[index] + gap
            }
        }
    }
}
