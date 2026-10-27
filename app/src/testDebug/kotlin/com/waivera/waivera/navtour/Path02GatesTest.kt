package com.waivera.waivera.navtour

import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.demo.DemoData
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.TodayRoute
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Path 2: the gates between Firebase and the tabs (app-shell.md "Auth gate"): required password
 * change, bootstrap failure with retry, unfinished onboarding and missing configuration.
 */
@HiltAndroidTest
class Path02GatesTest : NavTourHarness() {

    @Test
    fun path02_requiredPasswordChange_unlocksTheTabs() {
        world.session().force(SessionState.PasswordChangeRequired(DemoData.currentUser()))
        launch()

        expectGate(AppGate.PasswordChange)
        expectScreen("Set a new password")
        shot("path_02_password_01_required")
        shotDark("path_02_password_01_required")

        click("Update password")
        expectScreen("Enter your current password.")
        expectGate(AppGate.PasswordChange)
        shot("path_02_password_02_validation")

        typeInto("Current password", "demo")
        typeInto("New password", "a-new-password-26")
        typeInto("Confirm new password", "a-new-password-26")
        click("Update password")

        expectGate(AppGate.Main)
        expectStack(TodayRoute)
        expectScreen("Needs you")
        shot("path_02_password_03_today")
    }

    @Test
    fun path02_requiredPasswordChange_backNeverReachesTheTabs() {
        world.session().force(SessionState.PasswordChangeRequired(DemoData.currentUser()))
        launch()
        expectGate(AppGate.PasswordChange)

        systemBack()
        val finishing = onActivity { it.isFinishing }
        assertTrue("Back skipped the password gate: ${navSummary()}", finishing || shell.gate == AppGate.PasswordChange)
    }

    @Test
    fun path02_requiredPasswordChange_signOutReturnsToSignIn() {
        world.session().force(SessionState.PasswordChangeRequired(DemoData.currentUser()))
        launch()
        expectGate(AppGate.PasswordChange)

        click("Sign out")
        expectGate(AppGate.SignIn())
        expectScreen("Welcome back.")
        shot("path_02_password_04_signed_out")
    }

    @Test
    fun path02_bootstrapFailed_retryUntilItWorks() {
        world.session().force(SessionState.BootstrapFailed(WaiveraError.Server(SERVICE_UNAVAILABLE)))
        world.session().failNextRefresh(WaiveraError.Server(SERVICE_UNAVAILABLE))
        launch()

        expectGate(AppGate.BootstrapFailed)
        expectScreen("We couldn't reach Waivera")
        shot("path_02_bootstrap_01_failed")
        shotDark("path_02_bootstrap_01_failed")

        click("Try again")
        expectGate(AppGate.BootstrapFailed)
        expectScreen("We couldn't reach Waivera")
        shot("path_02_bootstrap_02_retry_failed")

        click("Try again")
        expectGate(AppGate.Main)
        expectStack(TodayRoute)
        expectScreen("Needs you")
        shot("path_02_bootstrap_03_today")
    }

    @Test
    fun path02_bootstrapFailedOffline_saysOffline() {
        world.session().force(SessionState.BootstrapFailed(WaiveraError.Network()))
        launch()
        expectGate(AppGate.BootstrapFailed)
        expectScreen("You're offline")
        shot("path_02_bootstrap_04_offline")
    }

    @Test
    fun path02_onboardingRequired_checkAgainOpensTheTabs() {
        world.session().force(SessionState.SignedIn(DemoData.currentUser().copy(requiresOnboarding = true)))
        launch()

        expectGate(AppGate.OnboardingRequired)
        expectScreen("Finish setting up your account")
        shot("path_02_onboarding_01_required")

        click("I've finished, check again")
        expectGate(AppGate.Main)
        expectStack(TodayRoute)
        shot("path_02_onboarding_02_today")
    }

    /** Only release builds reach this gate; the routing itself is the same in every build. */
    @Test
    fun path02_configurationMissing_routesToTheUnconfiguredSignIn() {
        world.session().force(SessionState.ConfigurationMissing(listOf("waivera.firebase.apiKey")))
        launch()

        expectGate(AppGate.SignIn(configurationMissing = true))
        expectScreen("This build isn't set up yet")
        shot("path_02_configuration_01_missing")
        assertBackLeavesApp()
    }

    private companion object {
        const val SERVICE_UNAVAILABLE = 503
    }
}
