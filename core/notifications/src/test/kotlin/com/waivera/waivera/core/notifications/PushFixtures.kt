package com.waivera.waivera.core.notifications

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A push fixture reduced to what `onMessageReceived` sees: the data map and the notification block. */
data class PushFixture(val data: Map<String, String>, val title: String?, val body: String?)

object PushFixtures {
    fun load(name: String): PushFixture {
        val text = requireNotNull(
            javaClass.classLoader?.getResource("push/$name"),
        ) { "missing fixture $name" }.readText()
        val root = Json.parseToJsonElement(text).let { if (it is JsonArray) it.first() else it } as JsonObject
        val message = root["message"] as? JsonObject
        val data = (message?.get("data") ?: root["data"] ?: root) as JsonObject
        val notification = (message?.get("notification") ?: root["notification"]) as? JsonObject
        return PushFixture(
            // FCM delivers every data value as a string; the Expo fixture has numbers, stringified here.
            data = data.mapNotNull { (key, value) -> value.asString()?.let { key to it } }.toMap(),
            title = notification?.get("title")?.asString() ?: (root["title"] as? JsonPrimitive)?.contentOrNull,
            body = notification?.get("body")?.asString() ?: (root["body"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
}
