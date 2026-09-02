package io.lukia.features.translate.domain.lookup

import io.lukia.features.translate.domain.settings.TargetLanguage

/**
 * A dictionary's whole answer about one word, as opposed to the line of it that
 * fits over the word in the book.
 *
 * The same source as
 * [io.lukia.features.translate.domain.text.WordLookup.Wiktionary] and a
 * different question. Translating a word has to end in one thing the reader can
 * put in place of it, so the entry is cut to its first sense and the rest
 * becomes a footnote; this is what somebody asks when that was not enough, and
 * cutting it would be cutting away the reason they asked.
 */
data class WordEntry(
    val word: String,
    val senses: List<String>,
)

/**
 * Whatever can say what a word means, at length.
 *
 * A port of its own rather than another [io.lukia.features.translate.domain.Translator],
 * because the two answer different questions and a translator's shape says so:
 * it takes a passage and the book around it and returns one rendering, and none
 * of that is what a dictionary entry is. Nothing about this is per provider -
 * a dictionary needs no key and no address - so there is one implementation and
 * room for another.
 *
 * Throws rather than returning a [Result], as the translator does, so that the
 * network and configuration failures are narrowed in one place - see
 * [io.lukia.features.translate.application.LookUpWordUseCase].
 */
fun interface WordDictionary {

    suspend fun define(word: String, targetLanguage: TargetLanguage): WordEntry
}
