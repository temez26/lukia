package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.settings.WordLookup

/**
 * Sends a single word wherever the reader has said single words should go, and
 * everything larger to the translator.
 *
 * The split is at the word because that is where the book stops mattering. Which
 * sense of "piece" a sentence means is settled by the paragraph around it, and a
 * service handed neither can only guess - so a sentence goes to the translator
 * whatever this is set to. A word alone is the one case where a dictionary is
 * not a worse answer but a different and often better one: every sense rather
 * than one chosen.
 *
 * Read per request, like everything else here that is a setting: a reader
 * comparing a dictionary against Google does it with the book open.
 *
 * [translator] is therefore never asked about a word. It is here for everything
 * larger, which has nowhere else to go - see [WordLookup] for why the service
 * stopped being one of the choices for a word.
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
            WordLookup.Google -> google
            WordLookup.Wiktionary -> dictionary
        }

        return answering.translate(request, onPartial)
    }
}
