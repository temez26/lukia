package io.lukia.api

import kotlinx.serialization.Serializable

@Serializable
data class Credentials(
    val username: String,
    val password: String,
)

@Serializable
data class RedeemInviteRequest(
    val code: String,
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class NewPasswordRequest(
    val password: String,
)

/**
 * A signed-in session. Times are epoch milliseconds on the server's clock, for
 * the app to refresh ahead of rather than to compare against its own.
 */
@Serializable
data class TokenResponse(
    val accessToken: String,
    val accessExpiresAt: Long,
    val refreshToken: String,
    val refreshExpiresAt: Long,
)

@Serializable
data class AccountResponse(
    val username: String,
    val isAdmin: Boolean,
)

@Serializable
data class InviteResponse(
    val code: String,
    val expiresAt: Long,
)

/** What the admin decides about this server. */
@Serializable
data class ServerSettings(
    /** Whether anyone who can reach the server may create an account. Closed until the admin opens it. */
    val signUpOpen: Boolean,
)

/**
 * One account as the admin sees it in the list of people: who it is and how
 * much it reads here. Totals only - never which books.
 */
@Serializable
data class AccountSummary(
    val username: String,
    val isAdmin: Boolean,
    val createdAt: Long,
    /** Books this account uploaded that are still in the library. */
    val booksAdded: Int,
    /** Books it has opened, finished ones included. */
    val booksStarted: Int,
    val booksFinished: Int,
    val favourites: Int,
    val readingMs: Long,
    val sessions: Int,
    /** When its latest reading session ended; null if it has never read. */
    val lastReadAt: Long? = null,
)

/** What a username and a password must look like, checked by the server and shown by the app. */
object AccountRules {
    const val UsernamePattern = "^[a-z0-9_.-]{3,32}$"
    const val MinPasswordLength = 10

    /** Bounds the work one sign-in can ask of the password hash. */
    const val MaxPasswordLength = 1024
}
