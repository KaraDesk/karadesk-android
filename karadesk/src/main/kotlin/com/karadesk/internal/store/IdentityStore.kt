package com.karadesk.internal.store

import android.content.Context
import android.content.SharedPreferences
import com.karadesk.KaraDeskUser
import com.karadesk.internal.model.KaraDeskJson
import com.karadesk.internal.model.instant
import com.karadesk.internal.model.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

internal class IdentityStore(context: Context, spaceId: String, appId: String) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("com.karadesk.identity", Context.MODE_PRIVATE)
    private val scope = "$spaceId.$appId"

    fun visitorId(): String = prefs.getString(visitorKey, null)?.takeIf { it.isNotEmpty() } ?: rotateVisitorId()

    fun rotateVisitorId(): String {
        val id = "android-${UUID.randomUUID().toString().lowercase()}"
        prefs.edit().putString(visitorKey, id).apply()
        return id
    }

    fun user(): KaraDeskUser? {
        val raw = prefs.getString(userKey, null) ?: return null
        val json = runCatching { KaraDeskJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null

        return KaraDeskUser(
            id = json.str("id"),
            email = json.str("email"),
            name = json.str("name"),
            phone = json.str("phone"),
            avatarUrl = json.str("avatar_url"),
            signedUpAt = json.instant("signed_up_at"),
            userHash = json.str("user_hash"),
        )
    }

    fun setUser(user: KaraDeskUser?) {
        if (user == null) {
            prefs.edit().remove(userKey).apply()
            return
        }

        val json = buildJsonObject {
            user.id?.let { put("id", it) }
            user.email?.let { put("email", it) }
            user.name?.let { put("name", it) }
            user.phone?.let { put("phone", it) }
            user.avatarUrl?.let { put("avatar_url", it) }
            user.signedUpAt?.let { put("signed_up_at", it.toString()) }
            user.userHash?.let { put("user_hash", it) }
        }

        prefs.edit().putString(userKey, json.toString()).apply()
    }

    fun announcedMessageId(): String? = prefs.getString(announcedKey, null)

    fun setAnnouncedMessageId(id: String) {
        prefs.edit().putString(announcedKey, id).apply()
    }

    private val visitorKey get() = "visitor.$scope"
    private val userKey get() = "user.$scope"
    private val announcedKey get() = "announced.$scope"
}
