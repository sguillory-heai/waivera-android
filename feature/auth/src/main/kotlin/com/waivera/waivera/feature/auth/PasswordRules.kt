package com.waivera.waivera.feature.auth

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.waivera.waivera.core.designsystem.icon.WaiveraIcons
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.theme.WaiveraSizes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme

/**
 * The server's password rules as a checklist under the new-password field, ticked off while the
 * person types (the web shows the same list). [showUnmet] colours the open rules as errors once
 * a submit was refused for them.
 */
@Composable
internal fun PasswordRulesChecklist(password: String, showUnmet: Boolean, modifier: Modifier = Modifier) {
    val unmet = remember(password) { AuthValidation.unmetPasswordRules(password) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
        Text(
            text = stringResource(R.string.feature_auth_password_rules_title),
            style = MaterialTheme.typography.bodySmall,
            color = WaiveraTheme.colors.textSecondary,
        )
        PasswordRule.entries.forEach { rule ->
            PasswordRuleRow(rule = rule, met = rule !in unmet, showUnmet = showUnmet)
        }
    }
}

@Composable
private fun PasswordRuleRow(rule: PasswordRule, met: Boolean, showUnmet: Boolean) {
    val label = if (rule == PasswordRule.Length) {
        stringResource(rule.label, AuthValidation.MIN_NEW_PASSWORD_LENGTH)
    } else {
        stringResource(rule.label)
    }
    val spoken = stringResource(
        if (met) R.string.feature_auth_password_rule_met else R.string.feature_auth_password_rule_unmet,
        label,
    )
    val colors = WaiveraTheme.colors
    val color = when {
        met -> colors.successText
        showUnmet -> colors.dangerText
        else -> colors.textSecondary
    }
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
    ) {
        Crossfade(targetState = met, animationSpec = WaiveraTheme.motion.state(), label = "passwordRule") { done ->
            Box(modifier = Modifier.size(WaiveraSizes.IconSmall), contentAlignment = Alignment.Center) {
                if (done) {
                    Icon(
                        painter = painterResource(WaiveraIcons.Check),
                        contentDescription = null,
                        tint = colors.successText,
                        modifier = Modifier.size(WaiveraSizes.IconSmall),
                    )
                } else {
                    Box(modifier = Modifier.size(RuleDot).background(color, CircleShape))
                }
            }
        }
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** An open rule's dot: half the small icon it swaps with. */
private val RuleDot: Dp = WaiveraSizes.IconSmall / 2

@WaiveraPreviews
@Composable
private fun PasswordRulesChecklistPreview() {
    WaiveraTheme {
        PasswordRulesChecklist(password = "Morning_walk", showUnmet = false)
    }
}

@WaiveraPreviews
@Composable
private fun PasswordRulesChecklistErrorPreview() {
    WaiveraTheme {
        PasswordRulesChecklist(password = "morning", showUnmet = true)
    }
}
