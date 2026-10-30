package com.waivera.waivera.core.notifications

import com.waivera.waivera.core.notifications.presentation.InitialsAvatar
import kotlin.test.assertEquals
import org.junit.Test

class InitialsAvatarTest {
    @Test
    fun `initials are the first and last names`() {
        assertEquals("AR", InitialsAvatar.initials("Alex Rivera"))
        assertEquals("MR", InitialsAvatar.initials("  maria  fernanda   rodriguez "))
        assertEquals("N", InitialsAvatar.initials("North"))
    }

    @Test
    fun `an emoji initial is never split`() {
        assertEquals("🌱S", InitialsAvatar.initials("🌱 Sprout"))
    }

    @Test
    fun `a blank name has no initials`() {
        assertEquals("", InitialsAvatar.initials("   "))
    }
}
