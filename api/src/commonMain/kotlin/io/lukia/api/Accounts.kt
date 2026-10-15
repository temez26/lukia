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

/** What a username and a password must look like, checked by the server and shown by the app. */
object AccountRules {
    const val UsernamePattern = "^[a-z0-9_.-]{3,32}$"
    const val MinPasswordLength = 10

    /** Bounds the work one sign-in can ask of the password hash. */
    const val MaxPasswordLength = 1024
}
