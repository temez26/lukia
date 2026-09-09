package io.lukia.features.translate.infrastructure

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

/**
 * How long the service asked to be left alone for, in seconds.
 *
 * The only party that knows when a rate limit resets is the one that set it, so
 * when it says, it is obeyed - see [RetryingTranslator]. Null when it said
 * nothing, which is the ordinary case and is what the backoff there is for.
 *
 * Only the seconds form is read. The HTTP date form the spec allows is not what
 * these services send, and parsing a date against a possibly-wrong device clock
 * to decide how long to wait is more ways to be wrong than this needs. An
 * unreadable value is no value rather than zero, because zero would mean
 * "immediately" - the one answer a service asking for a pause did not give.
 */
internal fun HttpResponse.retryAfterSeconds(): Int? =
    headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
