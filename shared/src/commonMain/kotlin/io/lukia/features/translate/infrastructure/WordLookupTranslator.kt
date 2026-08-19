package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.WordLookup

/**
 * Sends a single word wherever the reader has said single words should go, and
 * everything larger to the translator.
 *
 * The split is at the word rather than anywhere else because that is where the
 * book stops mattering. Which sense of "piece" a sentence means is settled by
 * the paragraph around it, and a service that is handed neither cannot do better
 * than guess - so a sentence goes to the translator whatever this is set to. A
 * word on its own is the one case where a dictionary is not a worse answer but a
 * different and often better one: it gives every sense rather than choosing.
 *
 * Read per request, like everything else here that is a setting: a reader
 * comparing a dictionary against a model does it with the book open.
 */
class WordLookupTranslator(
    private val lookup: () -> WordLookup,
    private val translator: Translator,
    private val google: Translator,
    private val dictionary: Translator,
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        if (request.unit != PassageUnit.Word) return translator.translate(request, onPartial)

        val answering = when (lookup()) {
            WordLookup.Translator -> translator
            WordLookup.Google -> google
            WordLookup.Wiktionary -> dictionary
        }

        return answering.translate(request, onPartial)
    }
}
