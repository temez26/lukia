package io.lukia.features.translate.infrastructure

import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.failure
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TranslationSettings
import kotlin.coroutines.cancellation.CancellationException

/**
 * What to tell the reader when the request never came back at all.
 *
 * Running out of patience and finding nothing there are one failed request here
 * and two different situations to the person holding the phone. Told apart
 * because the advice is opposite: a server that is not running has to be
 * started, one that is thinking has to be waited for - and "check whether the
 * server is running" while it loads a model is how somebody restarts the thing
 * that was working. Every provider maps its transport failures through here so
 * the two never get the same words.
 */
internal fun Throwable.asTranslationFailure(
    settings: TranslationSettings,
): AppErrorException = when {
    isSilence -> TranslationFailureException(
        kind = TranslationFailureKind.TimedOut,
        explanation = settings.tooSlow(),
    )

    else -> TranslationFailureException(
        kind = TranslationFailureKind.Unreachable,
        explanation = settings.unreachable(),
    )
}

/**
 * Whether the connection was open and simply stopped producing anything.
 *
 * Both mean that; which is thrown depends on the engine and on where in the
 * request the waiting happened, not on anything the reader did.
 *
 * Deliberately not a connect timeout: nothing accepting a connection is a server
 * that is not there, however long it was given - and it is the one timeout the
 * reader cannot lengthen, opening a connection taking the same time everywhere.
 */
private val Throwable.isSilence: Boolean
    get() = this is SocketTimeoutException || this is HttpRequestTimeoutException

private fun TranslationSettings.tooSlow(): String = when (provider) {
    TranslationProvider.Local ->
        "$model did not answer within ${timeoutSeconds}s. A model still loading can " +
            "take longer than that the first time - allow it more time in Settings, or " +
            "choose a smaller model."

    else ->
        "The translation service did not answer within ${timeoutSeconds}s. Try again, or " +
            "allow it more time in Settings."
}

private fun TranslationSettings.unreachable(): String = when (provider) {
    TranslationProvider.Local ->
        "Could not reach $baseUrl. Check the server is running and that the address is " +
            "reachable from this device."

    else -> "Could not reach the translation service. Check your connection."
}

/**
 * Runs [block] and lets nothing out of it that the reader cannot read.
 *
 * Every provider had written this ladder out for itself, and the middle rung is
 * the one worth having once: a failure already turned into words - a rejected
 * key, a missing model - must not be turned into them again, or "check your API
 * key" becomes "could not reach the translation service".
 *
 * Cancellation is not a failure and is never described: a reader who turned the
 * page mid-sentence is told nothing went wrong, because nothing did.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> translating(
    settings: TranslationSettings,
    diagnostics: Diagnostics,
    block: () -> T,
): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        // Every provider comes through here, so this is the one place the app
        // has to write a failed request down - and the only detail worth
        // keeping is which provider it was. Not the address: a local server's
        // is the reader's own network. Not the prompt or the answer: those are
        // the book. The throwable's type says the rest, and tells a rejected
        // key apart from a server that was never there.
        diagnostics.failure(
            area = DiagnosticArea.Translation,
            event = "request-failed",
            detail = settings.provider.name,
            cause = failure,
        )

        throw when (failure) {
            is AppErrorException -> failure
            else -> failure.asTranslationFailure(settings)
        }
    }
