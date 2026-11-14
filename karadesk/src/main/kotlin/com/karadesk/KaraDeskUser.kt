package com.karadesk

import java.time.Instant

/**
 * A signed-in user of your app.
 *
 * Provide at least an [id] or an [email]. When identity verification is turned on for your app in
 * KaraDesk, also provide the [userHash] your server computed for this user.
 *
 * @property id Your app's stable identifier for this user.
 * @property email The user's email address.
 * @property name The user's full name.
 * @property phone The user's phone number.
 * @property avatarUrl A public HTTPS URL of the user's avatar.
 * @property signedUpAt When the user signed up for your app.
 * @property userHash The HMAC-SHA256 of the user's [id] (or [email] when there is no id), computed on
 * your server with the space's identity verification secret.
 */
public data class KaraDeskUser(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val signedUpAt: Instant? = null,
    val userHash: String? = null,
) {
    internal fun merging(changes: KaraDeskUser): KaraDeskUser = KaraDeskUser(
        id = id ?: changes.id,
        email = changes.email ?: email,
        name = changes.name ?: name,
        phone = changes.phone ?: phone,
        avatarUrl = changes.avatarUrl ?: avatarUrl,
        signedUpAt = changes.signedUpAt ?: signedUpAt,
        userHash = changes.userHash ?: userHash,
    )

    internal val isIdentifiable: Boolean
        get() = !id.isNullOrEmpty() || !email.isNullOrEmpty()
}
