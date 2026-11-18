package com.karadesk.internal.store

import android.util.Log
import com.karadesk.KaraDesk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.util.Date

internal object EventMetadata {
    fun toJson(metadata: Map<String, Any?>): JsonObject = JsonObject(
        metadata.mapNotNull { (key, value) -> convert(key, value)?.let { key to it } }.toMap(),
    )

    private fun convert(path: String, value: Any?): JsonElement? = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> value.toDouble().takeIf { it.isFinite() }?.let { JsonPrimitive(value) } ?: JsonNull
        is Date -> JsonPrimitive(value.toInstant().toString())
        is Instant -> JsonPrimitive(value.toString())
        is Map<*, *> -> JsonObject(
            value.entries.mapNotNull { (key, item) ->
                val name = key as? String

                if (name == null) {
                    skip("$path.$key")
                    null
                } else {
                    convert("$path.$name", item)?.let { converted -> name to converted }
                }
            }.toMap(),
        )
        is Iterable<*> -> JsonArray(value.mapIndexedNotNull { index, item -> convert("$path[$index]", item) })
        is Array<*> -> JsonArray(value.mapIndexedNotNull { index, item -> convert("$path[$index]", item) })
        else -> skip(path)
    }

    private fun skip(path: String): JsonElement? {
        Log.w(KaraDesk.TAG, "trackEvent skipped metadata $path: unsupported value")
        return null
    }
}
