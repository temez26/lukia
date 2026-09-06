package io.lukia.features.translate.domain.settings

/**
 * What answers when the reader asks about a single word.
 *
 * Only a word. A sentence out of a book is not a sentence out of nowhere - which
 * sense of "piece" is meant is settled by the paragraph around it and by what the
 * book is - and that is the whole reason the translator is given all of it. A
 * word is the one case where something that knows none of that can still be
 * right, and where a reader may well prefer it: a dictionary gives every sense a
 * word has rather than the one a model chose.
 */
enum class WordLookup {

    /**
     * The translator the reader has already set up, asked the same way it is
     * asked about a sentence - with the book and the paragraph attached.
     */
    Translator,

    /**
     * Google Translate, which needs nothing set up and knows nothing about the
     * book.
     */
    Google,

    /**
     * Wiktionary, which is a dictionary rather than a translator: it gives the
     * senses a word has instead of choosing one.
     */
    Wiktionary,
    ;

    companion object {
        val Default = Translator
    }
}
