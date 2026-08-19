package io.lukia.features.translate.infrastructure

import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings

/**
 * What to tell the reader when the request never came back at all.
 *
 * Running out of patience and finding nothing there are one failed request to
 * this code and two completely different situations to the person holding the
 * phone. Told apart here because the advice is opposite: a server that is not
 * running has to be started, and one that is thinking has to be waited for -
 * and being told to check whether a server is running while it is busy loading
 * a model is how somebody ends up restarting the thing that was working.
 *
 * That confusion is what this whole file exists to end. Every provider maps its
 * transport failures through here so the two never get the same words again.
 */
internal fun Throwable.asTranslationFailure(
    settings: TranslationSettings
): AppErrorException = AppErrorException(AppError.Unexpected(settings.explain(this)))

private fun TranslationSettings.explain(failure: Throwable): String = when {
    failure.isSilence -> tooSlow()
    else -> unreachable()
}

/**
 * Whether the connection was open and simply stopped producing anything.
 *
 * Both of these mean that; which is thrown depends on the engine and on where
 * in the request the waiting happened rather than on anything the reader did.
 *
 * Deliberately not a connect timeout. Nothing accepting a connection is a
 * server that is not there, however long it was given - and it is the one
 * timeout the reader cannot lengthen, since opening a connection is the part
 * that takes the same time everywhere.
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
