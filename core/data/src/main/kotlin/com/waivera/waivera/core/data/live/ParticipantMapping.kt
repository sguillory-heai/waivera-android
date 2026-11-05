package com.waivera.waivera.core.data.live

import com.waivera.waivera.core.data.model.ParticipantProfile
import com.waivera.waivera.core.network.dto.ParticipantInfoResponse
import com.waivera.waivera.core.network.time.WaiveraTime

/** sprout_api `ParticipantInfoResponse` in the phone's terms. */
object ParticipantMapping {
    fun profile(dto: ParticipantInfoResponse, changePending: Boolean): ParticipantProfile {
        val first = dto.firstName?.trim().orEmpty()
        val last = dto.lastName?.trim().orEmpty()
        return ParticipantProfile(
            id = dto.id,
            name = listOf(first, last).filter(String::isNotEmpty).joinToString(" "),
            initials = listOf(first, last).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString(""),
            waiver = dto.waiver?.waiverName?.trim()?.takeIf(String::isNotEmpty)
                ?: dto.waiver?.code?.trim()?.takeIf(String::isNotEmpty),
            region = dto.region?.regionName?.trim()?.takeIf(String::isNotEmpty),
            mobile = dto.mobileNumber?.trim()?.takeIf(String::isNotEmpty),
            email = dto.email?.trim()?.takeIf(String::isNotEmpty),
            planStart = WaiveraTime.parseLocalDate(dto.cpocDate),
            planEnd = WaiveraTime.parseLocalDate(dto.cpocEndDate),
            certification = WaiveraTime.parseLocalDate(dto.certificationDate),
            linkage = WaiveraTime.parseLocalDate(dto.linkageDate),
            birthDate = WaiveraTime.parseLocalDate(dto.dateOfBirth),
            intake = dto.intakeType?.trim()?.takeIf(String::isNotEmpty),
            deactivated = dto.deactivated,
            compliant = dto.isCompliant,
            changePending = changePending,
        )
    }
}
