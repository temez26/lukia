package io.lukia.features.translate.infrastructure.wiktionary

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator

/**
 * How many senses of a word are worth putting in front of somebody who is
 * reading.
 *
 * A dictionary's whole advantage over a translator is that it does not choose,
 * and its whole risk is that it does not choose: a common word has a dozen
 * entries, and a dozen entries spliced into a sentence is not something anybody
 * can read. The first few answer most of the time, and the reader who wanted the
 * rest can open the entry - see [WiktionaryDictionary].
 */
private const val MAX_SENSES = 3

/**
 * Looks a word up in Wiktionary, in the language the reader is reading into.
 *
 * A dictionary rather than a translator, which is the point of offering it: it
 * gives the senses a word has instead of choosing one, which is often what
 * somebody learning a language wants from a word they do not know.
 *
 * Which wikis are asked and how the page is read are [WiktionaryPages]' and
 * [senses]' business. What is left here is the one decision this has to make -
 * how much of an entry fits over a word in a book, which is not much.
 */
internal class WiktionaryTranslator(
    private val pages: WiktionaryPages,
) : Translator {

    override suspend fun translate(
        request: TranslationRequest,
        onPartial: (String) -> Unit,
    ): Translation {
        val word = request.text.trim()

        val senses = pages.entry(word, request.targetLanguage)?.senses(word, MAX_SENSES)

        if (senses.isNullOrEmpty()) {
            throw AppErrorException(AppError.Unexpected("No dictionary entry for “$word”."))
        }

        return Translation(
            text = senses.first(),
            // The rest go where a translator's remark goes, under the paragraph:
            // a dictionary's second meaning is an aside about the word, not a
            // second thing to read in place of it.
            note = senses.drop(1).takeIf { it.isNotEmpty() }?.joinToString("  •  "),
        )
    }
}
