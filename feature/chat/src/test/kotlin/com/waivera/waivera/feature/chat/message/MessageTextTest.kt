package com.waivera.waivera.feature.chat.message

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class MessageTextTest {
    @Test
    fun outgoingMessageNormalisesLineEndingsAndTrims() {
        val outgoing = requireNotNull(createOutgoingMessage("  first\r\nsecond\rthird  \n"))
        assertEquals("first\nsecond\nthird", outgoing.text)
    }

    @Test
    fun outgoingMessageIsOneParagraphWithHardBreaks() {
        val outgoing = requireNotNull(createOutgoingMessage("a\n\nb"))
        val paragraph = (outgoing.doc as JsonObject).getValue("content").jsonArray.single().jsonObject
        val types = paragraph.getValue("content").jsonArray.map { it.jsonObject.getValue("type").jsonPrimitive.content }
        assertEquals(listOf("text", "hardBreak", "hardBreak", "text"), types)
        assertEquals("a\n\nb", TipTapParser.plainText(outgoing.doc as JsonObject))
    }

    @Test
    fun typedMarkupStaysText() {
        val outgoing = requireNotNull(createOutgoingMessage("**bold** <b>x</b>"))
        assertEquals("**bold** <b>x</b>", TipTapParser.plainText(outgoing.doc as JsonObject))
    }

    @Test
    fun blankInputSendsNothing() {
        assertNull(createOutgoingMessage("  \r\n  "))
    }

    @Test
    fun onlyHttpAndHttpsLinksWithAHostOpen() {
        assertTrue(SafeLinks.isAllowed("https://waivera.example/a"))
        assertTrue(SafeLinks.isAllowed("HTTP://waivera.example"))
        assertFalse(SafeLinks.isAllowed("javascript:alert(1)"))
        assertFalse(SafeLinks.isAllowed("file:///etc/hosts"))
        assertFalse(SafeLinks.isAllowed("intent://scan#Intent;end"))
        assertFalse(SafeLinks.isAllowed("waivera://chat/7"))
        assertFalse(SafeLinks.isAllowed("https:///nohost"))
        assertFalse(SafeLinks.isAllowed(""))
        assertFalse(SafeLinks.isAllowed(null))
    }
}
