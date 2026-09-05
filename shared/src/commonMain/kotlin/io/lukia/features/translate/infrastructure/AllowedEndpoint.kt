package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.settings.TranslationSettings

/**
 * Stops a passage leaving in the clear for somewhere that is not the reader's
 * own machine.
 *
 * Every request that carries a passage, a book's title or an API key asks this
 * first, and asks it here rather than each writing the refusal out for itself -
 * three copies of a sentence is three chances for one of them to say something
 * slightly different about the same rule.
 *
 * Raised in the app's own vocabulary rather than left to the platform. Android
 * would refuse some of these itself and iOS would refuse none of them, and
 * either way what the reader sees is a connection that mysteriously will not
 * open - where what they need is the reason and what to do instead.
 */
internal fun TranslationSettings.refuseCleartextToThePublicInternet() {
    if (isEndpointAllowed) return

    throw AppErrorException(
        AppError.Unexpected(
            "Translating over plain HTTP is only allowed to your own network. " +
                "Use https, or an address on this machine.",
        ),
    )
}
