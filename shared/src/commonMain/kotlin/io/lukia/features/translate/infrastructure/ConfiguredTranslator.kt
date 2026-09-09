package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.provider.TranslationProvider

/**
 * Whichever translator the reader has set up, chosen per request.
 *
 * Per request rather than once at startup, because the choice is a setting: a
 * reader comparing a local model against a hosted one switches between them with
 * a book open, and one resolved at launch would keep answering as last time's
 * pick. Both read the rest of their configuration the same way, so this only has
 * to decide which speaks.
 */
class ConfiguredTranslator(
    private val provider: () -> TranslationProvider,
    private val anthropic: Translator,
    private val openAiCompatible: Translator,
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val translator = when (provider()) {
            TranslationProvider.Anthropic -> anthropic
            // OpenAI and a model on the reader's own machine are the same
            // request to the same shape of endpoint.
            TranslationProvider.OpenAi, TranslationProvider.Local -> openAiCompatible
        }

        return translator.translate(request, onPartial)
    }
}
