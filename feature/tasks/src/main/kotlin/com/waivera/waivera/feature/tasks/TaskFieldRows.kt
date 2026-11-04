package com.waivera.waivera.feature.tasks

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraInsetDivider
import com.waivera.waivera.core.designsystem.component.WaiveraPressable
import com.waivera.waivera.core.designsystem.component.WaiveraTileTone
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * A labelled fact with its tile, as on the task page. A row that opens something takes
 * [onClick] and shows a chevron; [onClickLabel] names what it opens for TalkBack.
 */
@Composable
internal fun InfoRow(
    label: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tone: WaiveraTileTone = WaiveraTileTone.Brand,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    value: @Composable () -> Unit,
) {
    val row = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WaiveraSpacing.MinTouchTarget)
                .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSizes.FieldTileGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaiveraIconTile(
                icon = icon,
                tone = tone,
                size = WaiveraSizes.FieldTile,
                iconSize = WaiveraSizes.FieldTileGlyph,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                Text(text = label, style = WaiveraTextStyles.FieldLabel, color = WaiveraTheme.colors.textSecondary)
                value()
            }
            if (onClick != null) {
                Icon(
                    painter = painterResource(WaiveraIcons.ChevronRight),
                    contentDescription = null,
                    tint = WaiveraTheme.colors.textSecondary,
                )
            }
        }
    }
    if (onClick == null) {
        Column(modifier) { row() }
    } else {
        WaiveraPressable(
            onClick = onClick,
            onClickLabel = onClickLabel,
            shape = WaiveraShapes.SheetCard,
            modifier = modifier.fillMaxWidth(),
        ) { row() }
    }
}

@Composable
internal fun InfoValue(text: String) {
    Text(text = text, style = WaiveraTextStyles.FieldValue, color = WaiveraTheme.colors.text)
}

@Composable
internal fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = WaiveraTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
internal fun InfoDivider() {
    WaiveraInsetDivider(
        inset = WaiveraSpacing.Medium + WaiveraSizes.FieldDividerInset,
        modifier = Modifier.padding(end = WaiveraSpacing.Medium),
    )
}

/** A grouped card inside a sheet: the sheet has the gutter, a hairline marks the card's edge. */
@Composable
internal fun SheetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(WaiveraTheme.colors.surface, WaiveraShapes.SheetCard)
            .border(WaiveraSpacing.Hairline, WaiveraTheme.colors.border, WaiveraShapes.SheetCard)
            .clip(WaiveraShapes.SheetCard)
            .padding(vertical = WaiveraSpacing.Small),
        content = content,
    )
}

/** A sheet's context: the task, then one fact, on two lines so a separator never wraps. */
@Composable
internal fun SheetSubtitle(first: String, second: String?) {
    Column(Modifier.padding(bottom = WaiveraSpacing.ExtraSmall)) {
        Text(first, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.textSecondary)
        if (!second.isNullOrEmpty()) {
            Text(second, style = MaterialTheme.typography.bodyMedium, color = WaiveraTheme.colors.textSecondary)
        }
    }
}
