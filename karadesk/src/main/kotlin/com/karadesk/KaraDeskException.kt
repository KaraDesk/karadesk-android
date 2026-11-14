package com.karadesk

/**
 * An error reported by the KaraDesk SDK.
 */
public sealed class KaraDeskException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** [KaraDesk.initialize] has not been called yet. */
    public class NotInitialized : KaraDeskException("KaraDesk.initialize must be called before using the messenger.")

    /** [KaraDesk.updateUser] was called while no user is signed in. */
    public class NotLoggedIn : KaraDeskException("KaraDesk.login must be called before KaraDesk.updateUser.")

    /** The space or app ID passed to [KaraDesk.initialize] is empty. */
    public class InvalidConfiguration : KaraDeskException("The KaraDesk space ID and app ID must not be empty.")

    /** Your app requires identity verification and the user hash is missing or doesn't match. */
    public class IdentityVerificationFailed(message: String) : KaraDeskException(message)

    /** The messenger is turned off for this audience, or your KaraDesk subscription is inactive. */
    public class Unavailable(message: String) : KaraDeskException(message)

    /** The device couldn't reach KaraDesk. */
    public class Network(cause: Throwable? = null) : KaraDeskException("Can't connect right now. Check your connection and try again.", cause)

    /** KaraDesk returned an error. */
    public class Server(public val statusCode: Int, message: String) : KaraDeskException(message)

    /** KaraDesk returned a response this version of the SDK couldn't read. */
    public class UnexpectedResponse : KaraDeskException("Something went wrong. Please try again.")
}
