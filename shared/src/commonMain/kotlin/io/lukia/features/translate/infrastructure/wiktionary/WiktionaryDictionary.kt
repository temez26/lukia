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
 * The same pages the in-line lookup reads and a different amount of them - see
 * [WiktionaryTranslator], which keeps three. This answers a reader holding a
 * word they have already looked up to ask what else it means, so cutting the
 * answer short cuts away the reason they asked.
 *
 * Offered whatever the word lookup is set to, costing nothing and needing
 * nothing set up: somebody translating with a model still has a word the model
 * chose one sense of, and this is where the others are.
 */
internal class WiktionaryDictionary(
    private val pages: WiktionaryPages,
) : WordDictionary {

    /**
     * An entry that says only what form the word is has still answered, which is
     * why nothing here asks for a sense.
     *
     * That is the one thing the panel exists to show and the one thing that
     * cannot go over a word in the book - see
     * [io.lukia.features.translate.domain.lookup.WordEntry.form]. Refusing it
     * here tells the reader twice that the word could not be translated, with
     * the answer thrown away in between.
     */
    override suspend fun define(word: String, targetLanguage: TargetLanguage): WordEntry {
        val term = word.trim()

        val entry = pages.entryFor(term, targetLanguage, MAX_SENSES)

        if (entry == null || (entry.hasNoMeaning && entry.form == null)) {
            throw AppErrorException(AppError.Unexpected("No dictionary entry for “$term”."))
        }

        return entry
    }
}
