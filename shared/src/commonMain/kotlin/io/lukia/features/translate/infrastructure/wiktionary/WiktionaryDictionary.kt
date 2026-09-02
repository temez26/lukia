package io.lukia.features.translate.infrastructure.wiktionary

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.lookup.WordDictionary
import io.lukia.features.translate.domain.lookup.WordEntry
import io.lukia.features.translate.domain.settings.TargetLanguage

/**
 * How much of an entry the reader gets when they ask for the entry.
 *
 * Far more than fits over a word in the book and still a ceiling, because a
 * Wiktionary page is not only the senses: below them are inflection tables,
 * derived terms and references, and past the point where the senses stop this
 * would be handing back the furniture. Enough to cover a common word's readings
 * in one language and to make clear when a word has several.
 */
private const val MAX_SENSES = 12

/**
 * The whole of what Wiktionary has to say about one word.
 *
 * The same pages the reader's in-line lookup reads and a different amount of
 * them - see [WiktionaryTranslator], which keeps three. This is what answers
 * when the reader holds a word they have already looked up and asks what else it
 * means, so cutting the answer short would be cutting away the reason they
 * asked.
 *
 * Offered whatever the word lookup is set to, because it costs nothing and needs
 * nothing set up: somebody translating with a model still has a word the model
 * chose one sense of, and this is where the others are.
 */
internal class WiktionaryDictionary(
    private val pages: WiktionaryPages,
) : WordDictionary {

    override suspend fun define(word: String, targetLanguage: TargetLanguage): WordEntry {
        val term = word.trim()

        val senses = pages.entry(term, targetLanguage)?.senses(term, MAX_SENSES)

        if (senses.isNullOrEmpty()) {
            throw AppErrorException(AppError.Unexpected("No dictionary entry for “$term”."))
        }

        return WordEntry(word = term, senses = senses)
    }
}
