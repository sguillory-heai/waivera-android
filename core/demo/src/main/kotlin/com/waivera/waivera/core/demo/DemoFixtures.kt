package com.waivera.waivera.core.demo

import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.decodeNormalized
import kotlinx.serialization.DeserializationStrategy

/** Reads the fixtures bundled in this module's resources (`demo-fixtures/`). */
internal object DemoFixtures {
    fun text(name: String): String {
        val stream = DemoFixtures::class.java.classLoader?.getResourceAsStream("demo-fixtures/$name")
            ?: error("Missing demo fixture $name")
        return stream.bufferedReader().use { it.readText() }
    }

    fun <T> decode(name: String, deserializer: DeserializationStrategy<T>): T =
        WaiveraJson.decodeNormalized(deserializer, text(name))
}
