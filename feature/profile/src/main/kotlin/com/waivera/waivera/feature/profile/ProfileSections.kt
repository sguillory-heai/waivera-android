package com.waivera.waivera.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.waivera.waivera.core.data.model.EmployeeFile
import com.waivera.waivera.core.data.model.TimeOffRequest
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.designsystem.component.WaiveraIconTile
import com.waivera.waivera.core.designsystem.component.WaiveraListRow
import com.waivera.waivera.core.designsystem.component.WaiveraStatusBadge
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTextStyles
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import java.text.NumberFormat
import java.time.ZoneId

@Composable
internal fun TimeOffSection(section: Section<TimeOffSummary>, onRetry: () -> Unit) {
    val title = stringResource(R.string.feature_profile_time_off_title)
    when (section) {
        Section.Loading -> {
            if (rememberDelayedSkeleton(loading = true)) {
                ProfileCard(title = title) { SectionSkeleton(lines = 3) }
            }
        }
        is Section.Failed -> ProfileCard(title = title) { SectionError(section.error, onRetry) }
        is Section.Ready -> if (TimeOffMath.hasContent(section.value)) {
            ProfileCard(title = title) { TimeOffBody(section.value) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeOffBody(summary: TimeOffSummary) {
    val colors = WaiveraTheme.colors
    val balance = summary.balance
    if (balance != null) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WaiveraSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.Small),
        ) {
            BalanceFigure(R.string.feature_profile_time_off_remaining, balance.remainingDays ?: 0.0)
            balance.allocated?.let { BalanceFigure(R.string.feature_profile_time_off_allocated, it) }
            balance.usedDays?.let { BalanceFigure(R.string.feature_profile_time_off_used, it) }
            balance.pending?.let { BalanceFigure(R.string.feature_profile_time_off_pending_count, it) }
        }
        TimeOffMath.usedPercentage(balance)?.let { used ->
            val remaining = TimeOffMath.remainingFraction(used)
            val description = stringResource(
                R.string.feature_profile_time_off_progress_a11y,
                (remaining * PERCENT).toInt(),
            )
            LinearProgressIndicator(
                progress = { remaining },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.ExtraSmall)
                    .semantics { contentDescription = description },
                color = colors.accent,
                trackColor = colors.surfaceMuted,
                drawStopIndicator = {},
            )
        }
    }
    summary.requests.take(TimeOffMath.VISIBLE_REQUESTS).forEach { request -> TimeOffRow(request) }
    val hidden = TimeOffMath.hiddenCount(summary)
    if (hidden > 0) {
        Text(
            text = pluralStringResource(R.plurals.feature_profile_time_off_more, hidden, hidden),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium),
        )
    }
}

@Composable
private fun BalanceFigure(@StringRes label: Int, value: Double) {
    val colors = WaiveraTheme.colors
    val text = formatDays(value)
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(text = text, style = MaterialTheme.typography.titleLarge, color = colors.text)
        Text(text = stringResource(label), style = WaiveraTextStyles.Eyebrow, color = colors.textSecondary)
    }
}

private fun formatDays(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
}.format(value)

@Composable
private fun TimeOffRow(request: TimeOffRequest) {
    val colors = WaiveraTheme.colors
    val status = TimeOffStatus.of(request)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WaiveraSpacing.Medium, vertical = WaiveraSpacing.ExtraSmall)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(WaiveraSpacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            Text(
                text = request.leaveType?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.feature_profile_time_off_default_type),
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
            ProfileFormat.dateRange(request.start, request.end)?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
        WaiveraStatusBadge(text = stringResource(status.label), tone = status.tone)
    }
}

@Composable
internal fun FilesSection(section: Section<List<EmployeeFile>>, zone: ZoneId, onRetry: () -> Unit) {
    ProfileCard(title = stringResource(R.string.feature_profile_files_title)) {
        when (section) {
            Section.Loading -> if (rememberDelayedSkeleton(loading = true)) SectionSkeleton(lines = 3)
            is Section.Failed -> SectionError(section.error, onRetry)
            is Section.Ready -> {
                val visible = section.value.take(ProfileViewModel.VISIBLE_FILES)
                if (visible.isEmpty()) {
                    Text(
                        text = stringResource(R.string.feature_profile_files_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WaiveraTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium),
                    )
                }
                // Files cannot be opened yet, so the rows are plainly non-interactive (no chevron).
                visible.forEach { file ->
                    val added = file.addedAt?.let { ProfileFormat.instantDate(it, zone) }
                    WaiveraListRow(
                        headline = file.fileName?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.feature_profile_files_untitled),
                        supporting = listOfNotNull(
                            added?.let { stringResource(R.string.feature_profile_files_added, it) },
                            file.category?.takeIf(String::isNotBlank),
                        ).joinToString(" · ").takeIf(String::isNotEmpty),
                        leading = { WaiveraIconTile(icon = R.drawable.feature_profile_ic_file) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun OrganizationSection(section: Section<OrganizationInfo?>, onRetry: () -> Unit) {
    ProfileCard(title = stringResource(R.string.feature_profile_organization_title)) {
        when (section) {
            Section.Loading -> if (rememberDelayedSkeleton(loading = true)) SectionSkeleton(lines = 2)
            is Section.Failed -> SectionError(section.error, onRetry)
            is Section.Ready -> {
                val org = section.value
                if (org == null) {
                    Text(
                        text = stringResource(R.string.feature_profile_organization_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = WaiveraTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = WaiveraSpacing.Medium),
                    )
                } else {
                    InfoRow(stringResource(R.string.feature_profile_organization_name), org.name)
                    if (org.legalName != null) {
                        InfoRow(stringResource(R.string.feature_profile_organization_legal_name), org.legalName)
                    }
                    InfoRow(stringResource(R.string.feature_profile_organization_address), org.address)
                    InfoRow(stringResource(R.string.feature_profile_organization_contact), org.contactNumber)
                }
            }
        }
    }
}

private const val PERCENT = 100
