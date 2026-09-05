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
 * Only the seconds form is read. The HTTP date form is allowed by the spec and
 * is not what any of these services send; parsing a date against a device clock
 * that may be wrong, to decide how long to wait before translating a sentence,
 * would be more ways to be wrong than the feature has uses for. An unreadable
 * value is treated as no value rather than as zero, because zero here would mean
 * "immediately" - the one answer a service asking for a pause did not give.
 */
internal fun HttpResponse.retryAfterSeconds(): Int? =
    headers[HttpHeaders.RetryAfter]?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
