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
}
