package com.waivera.waivera.notifications

import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.notifications.channel.WaiveraChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesMappingTest {
    @Test
    fun everyChannelHasACategory() {
        WaiveraChannel.entries.forEach { channel ->
            assertTrue(channel.id, NotificationCategory.entries.any { it.id == channel.id })
        }
    }

    @Test
    fun disabledCategoryDisablesItsChannelOnly() {
        val prefs = NotificationPreferences(
            categories = NotificationCategory.entries.associateWith { it != NotificationCategory.GROUP_ACTIVITY },
        ).toClient()
        assertFalse(prefs.allows(WaiveraChannel.GroupActivity))
        assertTrue(prefs.allows(WaiveraChannel.Messages))
        assertEquals(WaiveraChannel.entries.size - 1, prefs.enabledChannels.size)
    }

    @Test
    fun masterSwitchDisablesEverything() {
        val prefs = NotificationPreferences(enabled = false).toClient()
        WaiveraChannel.entries.forEach { assertFalse(prefs.allows(it)) }
    }
}
