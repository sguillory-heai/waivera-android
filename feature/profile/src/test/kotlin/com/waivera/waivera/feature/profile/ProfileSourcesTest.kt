package com.waivera.waivera.feature.profile

import com.waivera.waivera.core.data.model.Organization
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.demo.DemoGraph
import com.waivera.waivera.core.network.error.WaiveraError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ProfileSourcesTest {
    @Test
    fun organizationComesFromTheRepositoryWithAddressAndContact() = runTest {
        val profiles = FakeProfileRepository()
        profiles.organizationResult = Result.success(Organization(7, "Waivera Care", "900 Example Ave", "+1-555-0190"))
        val org = RepositoryOrganizationSource(profiles).current().getOrThrow()
        val expected = OrganizationInfo(7, "Waivera Care", address = "900 Example Ave", contactNumber = "+1-555-0190")
        assertEquals(expected, org)
    }

    @Test
    fun anUnmatchedOrganizationStaysUnavailableAndAFailureIsPassedOn() = runTest {
        val profiles = FakeProfileRepository()
        profiles.organizationResult = Result.success(null)
        assertNull(RepositoryOrganizationSource(profiles).current().getOrThrow())

        profiles.organizationResult = Result.failure(WaiveraError.Network())
        assertIs<WaiveraError.Network>(RepositoryOrganizationSource(profiles).current().exceptionOrNull())
    }

    @Test
    fun personDetailsCarryRolesAndTheOrganizationName() = runTest {
        val graph = DemoGraph(backgroundScope)
        val details = RepositoryPersonDetailsSource(graph.people).details(DemoData.PRIYA).getOrThrow()
        assertEquals(listOf("Direct Support Professional"), details.roles)
        assertEquals(DemoData.currentUser().organizationName, details.organization)
        assertTrue(details.displayName.orEmpty().isNotBlank())
    }

    @Test
    fun anUnknownPersonIsNotFound() = runTest {
        val graph = DemoGraph(backgroundScope)
        val result = RepositoryPersonDetailsSource(graph.people).details("usr_nobody")
        assertIs<WaiveraError.NotFound>(result.exceptionOrNull())
    }
}
