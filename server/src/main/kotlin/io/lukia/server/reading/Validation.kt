package io.lukia.server.reading

import io.ktor.http.HttpStatusCode
import io.lukia.api.ApiErrorCodes
import io.lukia.server.http.ApiException
import io.lukia.server.library.isBookId

internal inline fun invalidUnless(condition: Boolean, message: () -> String) {
    if (!condition) throw ApiException(HttpStatusCode.BadRequest, ApiErrorCodes.BadRequest, message())
}

internal fun requireBookId(bookId: String) = invalidUnless(isBookId(bookId)) {
    "A book is named by the SHA-256 of its file, in lower-case hex."
}

/** A device's clock in epoch milliseconds - wrong by minutes at worst, but never before 1970. */
internal fun requireChangedAt(changedAt: Long) = invalidUnless(changedAt > 0) { "Give the time of the change." }
