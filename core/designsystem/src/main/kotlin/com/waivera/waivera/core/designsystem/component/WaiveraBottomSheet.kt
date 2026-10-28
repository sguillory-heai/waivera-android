package com.waivera.waivera.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withBreaksAfterHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * Modal bottom sheet with the app's shape (the tab sheet's 28dp curve), scrim and drag handle.
 * It opens fully expanded and applies the navigation bar inset itself, so content must not pad
 * for it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiveraBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WaiveraTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = WaiveraSizes.SheetMaxWidth,
        shape = WaiveraShapes.ModalSheet,
        containerColor = colors.surface,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderStrong) },
    ) {
        WaiveraSheetContent(title = title, content = content)
    }
}

/** Sheet body layout, separate from the window so screenshot tests can render it inline. */
@Composable
internal fun WaiveraSheetContent(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = WaiveraSizes.SheetMaxWidth)
            .padding(start = WaiveraSpacing.Gutter, end = WaiveraSpacing.Gutter, bottom = WaiveraSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
    ) {
        if (title != null) {
            // Sheet titles often name a person or channel: wrap after a hyphen, never mid-word (UI5-05).
            Text(
                text = title.withBreaksAfterHyphens(),
                style = MaterialTheme.typography.titleLarge,
                color = WaiveraTheme.colors.text,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

/**
 * A sheet drawn inline, without a window: the sheet shape, drag handle, title and body exactly
 * as [WaiveraBottomSheet] shows them. For previews and screenshot tests of sheet content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiveraSheetFrame(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, WaiveraShapes.ModalSheet),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetDefaults.DragHandle(color = colors.borderStrong)
        WaiveraSheetContent(title = title, content = content)
    }
}

@WaiveraPreviews
@Composable
private fun WaiveraSheetContentPreview() {
    WaiveraTheme {
        WaiveraSheetContent(title = "Message actions") {
            WaiveraListRow(headline = "Copy text", onClick = {})
            WaiveraListRow(headline = "Reply in thread", onClick = {})
        }
    }
}
