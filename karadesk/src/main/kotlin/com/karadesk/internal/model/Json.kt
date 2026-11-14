package com.karadesk.internal.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime

internal val KaraDeskJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

internal fun JsonObject.str(key: String): String? {
    val value = this[key] as? JsonPrimitive ?: return null

    if (value is JsonNull || !value.isString && value.content == "null") {
        return null
    }

    return value.content
}

internal fun JsonObject.str(key: String, default: String): String = str(key) ?: default

internal fun JsonObject.long(key: String): Long? {
    val value = this[key] as? JsonPrimitive ?: return null

    if (value is JsonNull) {
        return null
    }

    return value.longOrNull ?: value.doubleOrNull?.toLong() ?: value.content.toLongOrNull()
}

internal fun JsonObject.int(key: String): Int? = long(key)?.toInt()

internal fun JsonObject.int(key: String, default: Int): Int = int(key) ?: default

internal fun JsonObject.double(key: String): Double? {
    val value = this[key] as? JsonPrimitive ?: return null

    if (value is JsonNull) {
        return null
    }

    return value.doubleOrNull ?: value.content.toDoubleOrNull()
}

internal fun JsonObject.bool(key: String): Boolean? {
    val value = this[key] as? JsonPrimitive ?: return null

    if (value is JsonNull) {
        return null
    }

    return value.booleanOrNull ?: value.content.toBooleanStrictOrNull()
}

internal fun JsonObject.bool(key: String, default: Boolean): Boolean = bool(key) ?: default

internal fun JsonObject.url(key: String): String? = str(key)?.takeIf { it.isNotBlank() }

internal fun JsonObject.instant(key: String): Instant? = str(key)?.let(::parseInstant)

internal fun parseInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

internal inline fun <T> JsonObject.list(key: String, transform: (JsonObject) -> T?): List<T> =
    arr(key).mapNotNull { runCatching { transform(it) }.getOrNull() }

internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject
