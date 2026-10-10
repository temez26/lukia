package io.lukia.features.translate.infrastructure.anthropic

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.lukia.features.translate.infrastructure.TranslationFailureException
import io.lukia.features.translate.infrastructure.TranslationFailureKind
import io.lukia.features.translate.infrastructure.retryAfterSeconds
import kotlinx.serialization.json.Json

/** What the service calls an account that cannot be billed. */
private const val BILLING = "billing"

/** How an account with nothing left to spend describes itself in prose. */
private const val CREDIT_BALANCE = "credit balance"

/**
 * What went wrong, in words a reader can act on and a kind this app can.
 *
 * A rejected key, a busy service and an account with no credit are one HTTP
 * failure here and three different afternoons to the person holding the phone: a
 * trip to Settings, a moment's wait, a billing page. Told apart rather than all
 * becoming "something went wrong", with the kind travelling beside the words so
 * that only the one worth trying again is - see
 * [io.lukia.features.translate.infrastructure.RetryingTranslator].
 */
internal suspend fun HttpResponse.asTranslationFailure(
    json: Json,
    model: String,
): TranslationFailureException {
    val reported = readError(json)

    return when {
        // Asked first, because an account with nothing to spend is refused as a
        // 400 by one service and a 403 by another - and being sent to check a
        // key that was accepted perfectly well is the least useful thing this
        // could say to somebody whose card expired.
        reported.isBilling -> failure(
            TranslationFailureKind.NoCredit,
            "Your account is out of API credit. API billing is separate from any " +
                "subscription - add credit in your account's billing settings.",
        )

        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden -> failure(
            TranslationFailureKind.KeyRejected,
            "That API key was not accepted. Check it in Settings.",
        )

        status == HttpStatusCode.NotFound -> failure(
            TranslationFailureKind.ModelNotFound,
            reported?.message ?: "There is no model called $model. Check it in Settings.",
        )

        // The service says which of "too often" and "nothing left to spend"
        // this is far better than a guess from the status code, and it says
        // how long to wait far better than any backoff written here.
        status == HttpStatusCode.TooManyRequests -> failure(
            TranslationFailureKind.RateLimited,
            reported?.message ?: "Too many translations at once. Try again in a moment.",
            retryAfterSeconds(),
        )

        status == HttpStatusCode.PayloadTooLarge -> failure(
            TranslationFailureKind.TooLarge,
            "That passage is too long to translate.",
        )

        // 500 and the 529 the service sends while shedding load. Both are the
        // service's minute rather than the reader's request.
        status.value >= HttpStatusCode.InternalServerError.value -> failure(
            TranslationFailureKind.ServiceUnavailable,
            "The translation service is unavailable. Try again.",
            retryAfterSeconds(),
        )

        else -> failure(TranslationFailureKind.Rejected, reported?.message)
    }
}

/**
 * A failure the stream reported after the response had already succeeded.
 *
 * An overloaded service that accepted the request and then gave up on it arrives
 * this way rather than as a status, which is the whole reason this exists: read
 * as content it is an answer with nothing in it.
 */
internal fun ErrorBody.asTranslationFailure(): TranslationFailureException = failure(
    kind = if (type.orEmpty().startsWith(OVERLOADED)) {
        TranslationFailureKind.ServiceUnavailable
    } else {
        TranslationFailureKind.Rejected
    },
    explanation = message,
)

private const val OVERLOADED = "overloaded"

private val ErrorBody?.isBilling: Boolean
    get() = this != null &&
        (type.orEmpty().contains(BILLING) || message.orEmpty().contains(CREDIT_BALANCE, true))

/** The service's own explanation, when it sent one worth repeating. */
private suspend fun HttpResponse.readError(json: Json): ErrorBody? = runCatching {
    json.decodeFromString<ErrorResponse>(bodyAsText()).error
}.getOrNull()

private fun failure(
    kind: TranslationFailureKind,
    explanation: String?,
    retryAfterSeconds: Int? = null,
) = TranslationFailureException(kind, explanation, retryAfterSeconds)
