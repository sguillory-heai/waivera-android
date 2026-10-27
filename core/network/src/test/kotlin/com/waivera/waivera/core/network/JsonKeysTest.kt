package com.waivera.waivera.core.network

import com.waivera.waivera.core.network.json.withCamelCaseKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class JsonKeysTest {
    @Test
    fun pascalKeysBecomeCamelCaseAndAcronymsSurvive() {
        val normalized = WaiveraJson.parseToJsonElement(
            """{"MessageId":1,"OwnerUserID":"u","avatarURL":"a","Nested":[{"IsBookmarked":true}]}""",
        ).withCamelCaseKeys().jsonObject

        assertEquals(setOf("messageId", "ownerUserID", "avatarURL", "nested"), normalized.keys)
        val nested = normalized.getValue("nested").jsonArray.first().jsonObject
        assertEquals("true", nested["isBookmarked"]?.jsonPrimitive?.content)
    }

    @Test
    fun camelCaseWinsOverItsPascalTwin() {
        val normalized = WaiveraJson.parseToJsonElement("""{"Message":"pascal","message":"camel"}""")
            .withCamelCaseKeys().jsonObject

        assertEquals("camel", normalized["message"]?.jsonPrimitive?.content)
    }
}
