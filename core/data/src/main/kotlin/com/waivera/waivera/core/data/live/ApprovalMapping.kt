package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.LeaveBalance
import com.waivera.waivera.core.data.model.LeaveImpact
import com.waivera.waivera.core.data.model.ProfileField
import com.waivera.waivera.core.data.model.TimeOffReview
import com.waivera.waivera.core.data.model.TrainingBlock
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.network.dto.LeaveBalanceResponse
import com.waivera.waivera.core.network.dto.LeaveIntelligenceResponse
import com.waivera.waivera.core.network.dto.ParticipantChangeResponse
import com.waivera.waivera.core.network.dto.TimeOffDetailsResponse
import com.waivera.waivera.core.network.dto.TrainingCreditResponse
import com.waivera.waivera.core.network.time.WaiveraTime
import java.time.ZoneOffset
import kotlinx.serialization.json.Json

/** The details a reviewer reads before deciding time off, profile changes and training credit. */
object ApprovalMapping {
    private const val PARTIAL_DAY = "PARTIAL_DAY"
    private const val FUTURE_TASK_REVIEW = "FUTURE_TASK_REVIEW"
    private const val SUPERVISION = "SUPERVISION"
    private const val SHORT_NOTICE_VACATION = "SHORT_NOTICE_VACATION"
    private const val EXTENDED_SICK_LEAVE = "EXTENDED_SICK_LEAVE"
    private const val MASK = "••••"
    private val lenient = Json { ignoreUnknownKeys = true }

    /** sprout_web `renderTimeOffDetails`: full days are UTC calendar dates, partial days instants. */
    fun timeOff(dto: TimeOffDetailsResponse): TimeOffReview {
        val start = WaiveraTime.parseInstant(dto.timeOffStartDate)
        val end = WaiveraTime.parseInstant(dto.timeOffEndDate)
        val saved = intelligence(dto.intelligenceSnapshotJson)
        val current = intelligence(dto.currentIntelligenceJson)
        return TimeOffReview(
            leaveType = dto.typeOfLeave?.trim()?.takeIf(String::isNotEmpty).orEmpty(),
            leaveTypeId = dto.typeOfLeaveId,
            partialDay = dto.requestType.equals(PARTIAL_DAY, ignoreCase = true),
            startDate = start?.atZone(ZoneOffset.UTC)?.toLocalDate(),
            endDate = end?.atZone(ZoneOffset.UTC)?.toLocalDate(),
            start = start,
            end = end,
            minutes = dto.durationMinutes,
            note = dto.comment?.trim()?.takeIf(String::isNotEmpty),
            impact = (current ?: saved)?.let(::impact),
            impactChanged = saved != null && current != null && visible(saved) != visible(current),
            acknowledged = if (dto.intelligenceSnapshotJson.isNullOrBlank()) {
                null
            } else {
                dto.impactAcknowledgedAt != null && dto.impactAcknowledgedRevision == dto.reviewRevision
            },
        )
    }

    private fun intelligence(raw: String?): LeaveIntelligenceResponse? = raw?.takeIf(String::isNotBlank)?.let {
        runCatching { lenient.decodeFromString(LeaveIntelligenceResponse.serializer(), it) }.getOrNull()
    }

    private fun isVisible(code: String) = code != FUTURE_TASK_REVIEW && !code.contains(SUPERVISION)

    private fun visible(review: LeaveIntelligenceResponse) = Triple(
        review.overdueCount,
        review.dueDuringLeaveCount,
        review.warnings.map { it.code.orEmpty() }.filter(::isVisible).sorted(),
    )

    internal fun impact(review: LeaveIntelligenceResponse) = LeaveImpact(
        dueBefore = review.overdueCount,
        dueDuring = review.dueDuringLeaveCount,
        shortNotice = review.warnings.any { it.code == SHORT_NOTICE_VACATION },
        // The phone words the codes it knows itself rather than showing the server's text.
        extendedSick = review.warnings.any { it.code == EXTENDED_SICK_LEAVE },
    )

    /** sprout_web `renderChangeDiff`, without the web's empty Waiver ID and with SSN digits masked. */
    fun profile(dto: ParticipantChangeResponse): List<ProfileField> {
        val old = dto.orignalProfile ?: return emptyList()
        val new = dto.newProfileChanegs ?: return emptyList()
        fun text(value: String?) = value?.trim()?.takeIf(String::isNotEmpty)
        fun date(value: String?) = WaiveraTime.parseLocalDate(value)?.toString()
        fun id(value: Int?) = value?.takeIf { it != 0 }?.toString()
        val fields = listOf(
            ProfileField.Field.DateOfBirth to (date(old.dateOfBirth) to date(new.dateOfBirth)),
            ProfileField.Field.FirstName to (text(old.firstName) to text(new.firstName)),
            ProfileField.Field.LastName to (text(old.lastName) to text(new.lastName)),
            ProfileField.Field.Email to (text(old.email) to text(new.email)),
            ProfileField.Field.Mobile to (text(old.mobileNumber) to text(new.mobileNumber)),
            ProfileField.Field.Waiver to (text(old.waiverName) to text(new.waiverName)),
            ProfileField.Field.WaiverId to (id(old.waiverId) to id(new.waiverId)),
            ProfileField.Field.Ssn to (text(old.participantSsnLast4) to text(new.participantSsnLast4)),
            ProfileField.Field.Certification to (date(old.certificationDate) to date(new.certificationDate)),
            ProfileField.Field.CpocStart to (date(old.cpocDate) to date(new.cpocDate)),
            ProfileField.Field.CpocEnd to (date(old.cpocEndDate) to date(new.cpocEndDate)),
        )
        return fields
            .filter { (_, values) -> values.first != null || values.second != null }
            .map { (field, values) ->
                val (previous, requested) = values
                val changed = previous != requested
                if (field == ProfileField.Field.Ssn) {
                    ProfileField(field, previous?.let { MASK }, requested?.let { MASK }, changed)
                } else {
                    ProfileField(field, previous, requested, changed)
                }
            }
    }

    fun balance(dto: LeaveBalanceResponse) = LeaveBalance(dto.id, dto.tracksBalance, dto.availableMinutes)

    fun training(requestId: Int, dto: TrainingCreditResponse): TrainingCredit {
        val blockedText = dto.approvalBlockedReason?.lowercase().orEmpty()
        val pending = dto.status.isNullOrBlank() || dto.status.equals("Pending", ignoreCase = true)
        return TrainingCredit(
            requestId = requestId,
            course = dto.courseTitle?.trim()?.takeIf(String::isNotEmpty).orEmpty(),
            employeeName = dto.employeeName?.trim()?.takeIf(String::isNotEmpty).orEmpty(),
            trainer = dto.trainerName?.trim()?.takeIf(String::isNotEmpty),
            provider = dto.provider?.trim()?.takeIf(String::isNotEmpty),
            completed = WaiveraTime.parseLocalDate(dto.completedDate),
            hours = dto.hours,
            pending = pending,
            revision = dto.revision,
            canApprove = dto.canApprove,
            needsApproveReason = dto.requiresOverrideReason,
            uploadedBy = dto.submittedByName?.trim()?.takeIf { dto.uploadedForEmployee && it.isNotEmpty() },
            // The server words these for the web; the phone says the same in its own strings.
            blocked = when {
                !pending || blockedText.isEmpty() -> null
                "your own" in blockedText -> TrainingBlock.OwnCredit
                "no eligible reviewer" in blockedText -> TrainingBlock.NoReviewer
                else -> TrainingBlock.NotReviewer
            },
            reviewOnWeb = dto.standaloneCreditId != null,
        )
    }
}
