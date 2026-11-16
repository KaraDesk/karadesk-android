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

    /** The device couldn't reach KaraDesk. [offline] is true when the device has no internet connection. */
    public class Network(cause: Throwable? = null, public val offline: Boolean = false) : KaraDeskException(describe(cause, offline), cause) {
        /** Whether the request gave up waiting for KaraDesk to respond. */
        public val timedOut: Boolean
            get() = !offline && (cause is java.net.SocketTimeoutException || cause?.message?.contains("timeout", ignoreCase = true) == true)

        private companion object {
            fun describe(cause: Throwable?, offline: Boolean): String = when {
                offline -> "You're offline. Check your connection and try again."
                cause is java.net.SocketTimeoutException || cause?.message?.contains("timeout", ignoreCase = true) == true ->
                    "This is taking longer than usual. Please try again."
                else -> "Can't connect right now. Check your connection and try again."
            }
        }
    }

    /** KaraDesk returned an error. */
    public class Server(public val statusCode: Int, message: String) : KaraDeskException(message)

    /** KaraDesk returned a response this version of the SDK couldn't read. */
    public class UnexpectedResponse : KaraDeskException("Something went wrong. Please try again.")

    /** Whether the device has no usable internet connection. */
    public val isOffline: Boolean
        get() = this is Network && offline

    /** Whether trying the same request again might succeed. */
    public val isRetryable: Boolean
        get() = this is Network || this is Server || this is UnexpectedResponse
}
