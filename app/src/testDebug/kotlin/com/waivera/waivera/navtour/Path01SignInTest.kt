package com.waivera.waivera.navtour

import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.feature.auth.R as AuthR
import com.waivera.waivera.navigation.AppGate
import com.waivera.waivera.navigation.TodayRoute
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Path 1: a cold start while signed out, through sign-in (empty, invalid, wrong password,
 * connecting), the one-time notification primer, to Today.
 */
@HiltAndroidTest
class Path01SignInTest : NavTourHarness() {
    // The primer only shows on Android 13+ while the permission is not granted.
    override val notificationPermissionGranted: Boolean = false

    @Test
    fun path01_signedOutColdStart_signInPrimerToday() {
        world.session().force(SessionState.SignedOut())
        launch()

        expectGate(AppGate.SignIn())
        expectScreen(stringOf(AuthR.string.feature_auth_sign_in_title))
        shot("path_01_signin_01_empty")
        shotDark("path_01_signin_01_empty")

        click("Sign in")
        expectScreen("Enter your email.")
        expectScreen("Enter your password.")
        expectGate(AppGate.SignIn())
        shot("path_01_signin_02_validation")

        typeInto("Email", "alex.rivera")
        typeInto("Password", "demo")
        click("Sign in")
        expectScreen(stringOf(AuthR.string.feature_auth_error_email_invalid))
        shot("path_01_signin_03_invalid_email")

        typeInto("Email", "alex.rivera@example.com")
        typeInto("Password", "not-the-password")
        click("Sign in")
        expectScreen("Couldn't sign in")
        expectGate(AppGate.SignIn())
        shot("path_01_signin_04_wrong_password")

        val bootstrap = world.session().holdNextSignIn()
        typeInto("Password", "demo")
        click("Sign in")
        // The bootstrap runs through Initializing; the sign-in screen stays and its button says so.
        expectScreen(stringOf(AuthR.string.feature_auth_connecting))
        expectGate(AppGate.SignIn())
        shot("path_01_signin_05_connecting")

        bootstrap.complete(Unit)
        expectGate(AppGate.NotificationPrimer)
        expectScreen("Stay in the loop")
        shot("path_01_signin_06_primer")
        shotDark("path_01_signin_06_primer")

        click("Not now")
        expectGate(AppGate.Main)
        expectStack(TodayRoute)
        expectScreen("Needs you")
        shot("path_01_signin_07_today")

        assertBackLeavesApp()
    }

    @Test
    fun path01_backOnSignIn_leavesTheApp() {
        world.session().force(SessionState.SignedOut())
        launch()
        expectGate(AppGate.SignIn())
        assertBackLeavesApp()
    }

    @Test
    fun path01_primerIsNotShownAgainAfterNotNow() {
        launch()
        expectGate(AppGate.NotificationPrimer)
        click("Not now")
        expectGate(AppGate.Main)
        world.session().force(SessionState.SignedOut())
        expectGate(AppGate.SignIn())
        typeInto("Email", "alex.rivera@example.com")
        typeInto("Password", "demo")
        click("Sign in")
        expectGate(AppGate.Main)
        expectStack(TodayRoute)
        shot("path_01_signin_08_second_sign_in_skips_primer")
    }
}
