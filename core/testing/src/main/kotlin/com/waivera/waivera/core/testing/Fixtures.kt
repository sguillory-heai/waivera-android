package com.waivera.waivera.core.testing

import com.waivera.waivera.core.network.WaiveraJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads the shared spec fixtures copied into `core/testing/src/main/resources/fixtures`
 * (index in that folder's README.md). Never read the spec mount from code.
 */
object Fixtures {
    /** The raw text of `fixtures/<name>`, for example `rest-chat-get-all.json`. */
    fun text(name: String): String {
        val stream = Fixtures::class.java.classLoader?.getResourceAsStream("fixtures/$name")
            ?: error("Missing fixture $name")
        return stream.bufferedReader().use { it.readText() }
    }

    /** A hub fixture split into its target and positional arguments. */
    fun hub(name: String): HubInvocation {
        val root = WaiveraJson.parseToJsonElement(text(name)).jsonObject
        return HubInvocation(
            target = root.getValue("target").jsonPrimitive.content,
            arguments = root.getValue("arguments").jsonArray,
        )
    }

    /** Every fixture name with the given prefix, for exhaustive decode tests. */
    fun names(prefix: String): List<String> = ALL.filter { it.startsWith(prefix) }

    /** `index.txt` lists every fixture; regenerate it when fixtures are added. */
    private val ALL: List<String> by lazy { text("index.txt").lines().map(String::trim).filter(String::isNotEmpty) }
}

/** A SignalR invocation as stored in the hub fixtures. */
data class HubInvocation(val target: String, val arguments: JsonArray) {
    val firstObject: JsonObject get() = arguments.first().jsonObject
}
