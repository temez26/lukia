package io.lukia.features.translate.domain.lookup

import io.lukia.features.translate.domain.settings.TargetLanguage

/**
 * What a word means when it is used as one part of speech.
 *
 * A word is not one word. `mean` is three: an adjective that is `ilkeä`, a noun
 * that is `keskiarvo`, and a verb that is `tarkoittaa` - and which of them a
 * sentence meant is settled by the sentence, which a dictionary never sees.
 * Flattening them into one list would hand the reader `ilkeä` for a page about
 * averages with nothing to say that the other two readings were ever there.
 */
data class WordMeaning(
    /** What the page filed these senses under - `Verbi`, `Noun` - where it said. */
    val partOfSpeech: String?,
    val senses: List<String>,
)

/**
 * A dictionary's whole answer about one word, as opposed to the line of it that
 * fits over the word in the book.
 *
 * The same source as
 * [io.lukia.features.reader.domain.text.WordLookup.Wiktionary] and a
 * different question. Translating a word has to end in one thing the reader can
 * put in place of it, so what goes on the page is one reading of one sense; this
 * is what somebody asks when that was not enough, and cutting it would be
 * cutting away the reason they asked.
 *
 * [form] is here because a book is written in inflected words. A wiki's page for
 * one of them is usually not a definition - it says which word the form belongs
 * to and stops - so the entry that comes back is the base form's, and what the
 * held word actually was would be lost if the entry could not carry it. Which is
 * the half a language learner most wants: `arvo` answers "what does this say",
 * and `monikkomuoto sanasta value` answers "why does it look like that".
 */
data class WordEntry(
    /**
     * The word actually defined, which is not always the one that was asked
     * about - see [form].
     */
    val word: String,
    /** What the word means, kept apart by the part of speech it means it as. */
    val meanings: List<WordMeaning>,
    /**
     * What the held word is, in the wiki's own words - `monikkomuoto sanasta
     * value` - or null when the word defines itself.
     *
     * Grammar rather than meaning, which is why it is kept apart from the
     * meanings rather than counted as the first of them. Put over a word in a
     * paragraph it is noise; read on its own it is half of learning the word.
     */
    val form: String? = null,
    /** What the page listed beside the senses - related words and compounds. */
    val related: List<String> = emptyList(),
) {

    /** Every sense the entry has, in the order the page listed them. */
    val senses: List<String>
        get() = meanings.flatMap { it.senses }

    /**
     * Whether the page had nothing to say about what the word means.
     *
     * An entry rather than a failure even so: a page that only says which word
     * this is a form of has answered, and that answer belongs in the panel where
     * there is room to explain it - just not over the word in the book.
     */
    val hasNoMeaning: Boolean
        get() = meanings.all { it.senses.isEmpty() }
}

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
