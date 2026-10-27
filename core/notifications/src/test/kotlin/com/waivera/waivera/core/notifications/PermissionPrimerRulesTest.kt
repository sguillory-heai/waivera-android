package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.permission.NotificationPermissionStatus
import com.waivera.waivera.core.notifications.permission.PermissionPrimerRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionPrimerRulesTest {
    @Test
    fun `primer shows once on Android 13 and later when permission is missing`() {
        assertTrue(
            PermissionPrimerRules.shouldShowPrimer(sdkInt = 33, permissionGranted = false, primerCompleted = false),
        )
        assertFalse(
            PermissionPrimerRules.shouldShowPrimer(sdkInt = 35, permissionGranted = false, primerCompleted = true),
        )
        assertFalse(
            PermissionPrimerRules.shouldShowPrimer(sdkInt = 35, permissionGranted = true, primerCompleted = false),
        )
    }

    @Test
    fun `no primer before Android 13 where notifications need no runtime permission`() {
        assertFalse(
            PermissionPrimerRules.shouldShowPrimer(sdkInt = 32, permissionGranted = true, primerCompleted = false),
        )
        assertFalse(
            PermissionPrimerRules.shouldShowPrimer(sdkInt = 26, permissionGranted = false, primerCompleted = false),
        )
    }

    @Test
    fun `status for settings`() {
        assertEquals(NotificationPermissionStatus.Granted, PermissionPrimerRules.status(33, true, true, true))
        assertEquals(NotificationPermissionStatus.Granted, PermissionPrimerRules.status(30, true, true, false))
        assertEquals(NotificationPermissionStatus.NotRequested, PermissionPrimerRules.status(34, false, false, false))
        assertEquals(NotificationPermissionStatus.Denied, PermissionPrimerRules.status(34, false, false, true))
        // Notifications blocked for the app in system settings, on any version.
        assertEquals(NotificationPermissionStatus.Denied, PermissionPrimerRules.status(30, false, true, true))
        assertEquals(NotificationPermissionStatus.Denied, PermissionPrimerRules.status(34, false, true, true))
    }
}
