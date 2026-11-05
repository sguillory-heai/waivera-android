package com.waivera.waivera.feature.tasks.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.waivera.waivera.core.designsystem.component.WaiveraButton
import com.waivera.waivera.core.designsystem.component.WaiveraButtonSize
import com.waivera.waivera.core.designsystem.component.WaiveraButtonVariant
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import com.waivera.waivera.feature.tasks.R

/**
 * This step, or every remaining stage at once. The everyday answer leads; the bigger one sits
 * under it with what it does, so the bar itself stays two buttons.
 */
@Composable
internal fun ChooseScopeContent(step: ReviewStep.ChooseScope, onChoose: (all: Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall)) {
        WaiveraButton(
            text = stringResource(
                if (step.submit) R.string.feature_tasks_review_submit else R.string.feature_tasks_review_scope_this,
            ),
            onClick = { onChoose(false) },
            leadingIcon = WaiveraIcons.Check,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
        WaiveraButton(
            text = if (step.submit) {
                stringResource(R.string.feature_tasks_review_submit_all)
            } else {
                pluralStringResource(R.plurals.feature_tasks_review_approve_all, step.stages, step.stages)
            },
            onClick = { onChoose(true) },
            variant = WaiveraButtonVariant.Secondary,
            fullWidth = true,
            size = WaiveraButtonSize.Large,
        )
        Text(
            text = stringResource(R.string.feature_tasks_review_scope_all_note),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
        )
    }
}
