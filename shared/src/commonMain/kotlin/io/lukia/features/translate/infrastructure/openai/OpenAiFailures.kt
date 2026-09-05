package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.infrastructure.TranslationFailureException
import io.lukia.features.translate.infrastructure.TranslationFailureKind
import io.lukia.features.translate.infrastructure.retryAfterSeconds
import kotlinx.serialization.json.Json

/**
 * What went wrong, in words a reader can act on and a kind this app can.
 *
 * Every case here is one where the obvious reading of the response is the wrong
 * one, and each of them sends the reader somewhere different: a rejected key is
 * a trip to Settings, an account with no credit is a billing page, a rate limit
 * is a moment's wait, and a model this server has never heard of is a name to
 * correct. The kind travels beside the words so that only the failure worth
 * asking again about is asked again - see
 * [io.lukia.features.translate.infrastructure.RetryingTranslator].
 */
internal suspend fun HttpResponse.asTranslationFailure(
    json: Json,
    configured: TranslationSettings,
): TranslationFailureException {
    val reported = readError(json)

    return when {
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden -> failure(
            TranslationFailureKind.KeyRejected,
            "That API key was not accepted. Check it in Settings.",
        )

        status == HttpStatusCode.NotFound -> reported.asMissingFailure(configured)

        // Being out of credit and asking too often arrive as the same status and
        // are completely different problems, so they are told apart by what the
        // server said rather than by which status it said it with - an account
        // refused as a 400 is still an account with no credit.
        status == HttpStatusCode.TooManyRequests || reported?.isOutOfCredit == true ->
            reported.asBusyFailure(retryAfterSeconds())

        status == HttpStatusCode.PayloadTooLarge || reported?.isTooLong == true -> failure(
            TranslationFailureKind.TooLarge,
            reported?.message ?: "That passage is too long for ${configured.model}.",
        )

        status.value >= HttpStatusCode.InternalServerError.value -> failure(
            TranslationFailureKind.ServiceUnavailable,
            "The translation service is unavailable. Try again.",
            retryAfterSeconds(),
        )

        // A server that does not know the model is the usual 400 here, and it
        // says so far better than this code could guess.
        else -> failure(TranslationFailureKind.Rejected, reported?.message)
    }
}

/**
 * A failure a server reported on a stream it had already answered 200 to.
 *
 * Nothing about it is a status, so it would otherwise arrive as an answer with
 * no translation in it - which is what a server explaining itself clearly used
 * to look like from here.
 */
internal fun ChatErrorBody.asTranslationFailure(): TranslationFailureException = failure(
    kind = when {
        isTooLong -> TranslationFailureKind.TooLarge
        isUnknownModel -> TranslationFailureKind.ModelNotFound
        else -> TranslationFailureKind.Rejected
    },
    explanation = message,
)

/**
 * A 404, which is two completely different mistakes wearing one status: nothing
 * listening at the path, and a server that answered and does not have that
 * model. Sending somebody to correct an address that was right all along -
 * because the model name was the wrong one - is how a working setup gets taken
 * apart looking for a fault one field further down the page.
 *
 * Neither is worth asking again about, which is why both are the same kind.
 */
private fun ChatErrorBody?.asMissingFailure(
    configured: TranslationSettings,
): TranslationFailureException = when {
    this?.isUnknownModel == true -> failure(
        TranslationFailureKind.ModelNotFound,
        message ?: "That server has no model called ${configured.model}.",
    )

    else -> failure(
        TranslationFailureKind.ModelNotFound,
        "Nothing answered at ${configured.baseUrl}. Check the address in Settings - " +
            "it should end in /v1.",
    )
}

/**
 * Asking too often, or an account with nothing to spend. One is solved by
 * waiting a moment and the other never is, so reporting both as "try again
 * shortly" sends somebody off to wait for a limit that was never the reason.
 */
private fun ChatErrorBody?.asBusyFailure(retryAfter: Int?): TranslationFailureException = when {
    this?.isOutOfCredit == true -> failure(
        TranslationFailureKind.NoCredit,
        // Unnamed, because naming it would mean holding the service's own name
        // in the domain for one sentence written here, where no string resource
        // can be read. The reader is looking at the settings for the one service
        // they have configured, so "your account" is not ambiguous.
        "Your account is out of API credit. API billing is separate from any " +
            "subscription - add credit in your account's billing settings.",
    )

    else -> failure(
        TranslationFailureKind.RateLimited,
        this?.message ?: "Too many translations at once. Try again in a moment.",
        retryAfter,
    )
}

/** The service's own account of what went wrong, when it sent one. */
private suspend fun HttpResponse.readError(json: Json): ChatErrorBody? = runCatching {
    json.decodeFromString<ChatError>(bodyAsText()).error
}.getOrNull()

private fun failure(
    kind: TranslationFailureKind,
    explanation: String?,
    retryAfterSeconds: Int? = null,
) = TranslationFailureException(kind, explanation, retryAfterSeconds)
