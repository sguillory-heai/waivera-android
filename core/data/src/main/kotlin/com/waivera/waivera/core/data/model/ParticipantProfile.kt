package com.waivera.waivera.core.data.model

import java.time.LocalDate

/**
 * A participant as the web's Participant Profile tab shows them, read only. Dates are calendar
 * days, never shifted by a zone. [changePending] is the viewer's own profile change waiting on
 * approval, as the web's "Update Pending Approval".
 */
data class ParticipantProfile(
    val id: Int,
    val name: String,
    val initials: String,
    val waiver: String?,
    val region: String?,
    val mobile: String?,
    val email: String?,
    val planStart: LocalDate?,
    val planEnd: LocalDate?,
    val certification: LocalDate?,
    val linkage: LocalDate?,
    val birthDate: LocalDate?,
    val intake: String?,
    val deactivated: Boolean,
    val compliant: Boolean,
    val changePending: Boolean = false,
)
