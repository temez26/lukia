package io.lukia.api

import kotlinx.serialization.Serializable

/**
 * The body of every failed response.
 *
 * [code] is what the app branches on and turns into its own error; [message] is
 * for a person reading a log or `curl` output, and the app never shows it.
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

/** Every [ApiError.code] the server sends. An app meeting one it does not know treats it as unexpected. */
object ApiErrorCodes {
    const val NotFound = "not_found"
    const val BadRequest = "bad_request"
    const val Internal = "internal"

    /** No token, or one that has expired or been revoked: refresh, or sign in again. */
    const val Unauthorized = "unauthorized"

    /** Signed in, but not as an admin. */
    const val Forbidden = "forbidden"

    /** The username and password do not match an account. Never says which of the two was wrong. */
    const val InvalidCredentials = "invalid_credentials"

    /** Too many failed sign-ins for this username or address; try again later. */
    const val TooManyAttempts = "too_many_attempts"

    const val InvalidUsername = "invalid_username"
    const val InvalidPassword = "invalid_password"
    const val UsernameTaken = "username_taken"

    /** The invite code is unknown, used or expired. */
    const val InvalidInvite = "invalid_invite"

    /** `/setup` after the first account exists. */
    const val SetupDone = "setup_done"

    /** The last admin may not delete their account: nobody could invite anyone again. */
    const val LastAdmin = "last_admin"

    /** The upload is larger than the server accepts. */
    const val TooLarge = "too_large"

    /** The upload's file name is not a format the app reads. */
    const val UnsupportedFormat = "unsupported_format"
}
