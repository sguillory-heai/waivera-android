package com.waivera.waivera.core.data

import com.waivera.waivera.core.data.live.ParticipantMapping
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.dto.ParticipantInfoResponse
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParticipantMappingTest {
    @Test
    fun maps_the_server_participant_and_ignores_fields_the_phone_never_shows() {
        // Shaped like sprout_api ParticipantInfoResponse; ssn and device fields are dropped on decode.
        val dto = WaiveraJson.decodeFromString<ParticipantInfoResponse>(
            """
            {"id":501,"firstName":" Jordan ","lastName":"Ellis","dateOfBirth":"1988-06-02T00:00:00",
             "intakeType":"Transition","region":{"regionId":4,"regionName":"Region 4"},"deactivated":false,
             "linkageDate":"2025-02-10T00:00:00","cpocDate":"2026-04-01T00:00:00","cpocEndDate":"2027-03-31T00:00:00",
             "certificationDate":null,"email":"","mobileNumber":"(555) 010-2231",
             "waiver":{"id":2,"waiverName":null,"code":"CC"},"isCompliant":true,
             "ssn":"123-45-6789","deviceToken":"abc","newField":1}
            """.trimIndent(),
        )
        val profile = ParticipantMapping.profile(dto, changePending = true)
        assertEquals("Jordan Ellis", profile.name)
        assertEquals("JE", profile.initials)
        assertEquals("CC", profile.waiver)
        assertEquals("Region 4", profile.region)
        assertNull(profile.email)
        assertNull(profile.certification)
        assertEquals(LocalDate.of(2027, 3, 31), profile.planEnd)
        assertEquals(LocalDate.of(1988, 6, 2), profile.birthDate)
        assertTrue(profile.compliant)
        assertTrue(profile.changePending)
        assertFalse(profile.deactivated)
    }
}
