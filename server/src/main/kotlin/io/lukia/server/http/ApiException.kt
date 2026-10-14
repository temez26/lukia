package io.lukia.server.http

import io.ktor.http.HttpStatusCode

/**
 * A failure the server means to report, thrown from wherever it is found and
 * turned into an `ApiError` body by `StatusPages` - so a handler never has to
 * thread a response back up through the code that discovered the problem.
 *
 * Thrown inside a database transaction it also rolls that transaction back,
 * which is how a half-made account is undone when its invite turns out spent.
 */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)
