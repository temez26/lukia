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
 *
 * Both of these are ordinary web APIs that need nothing set up, which is what
 * makes a word the one thing Lukia can always answer. The translation service
 * used to be a third choice here and is not any more: it is the page the reader
 * has to go and configure, it is the one that costs money, and offering it for
 * the one unit that never needed it made the app look as though it could not
 * translate anything until a key was pasted in.
 */
enum class WordLookup {

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

        /**
         * The dictionary, which is the one of the two that asks nobody for
         * anything: no key, no account, and no published endpoint being used in
         * a way its owner never agreed to - see `GoogleTranslator`.
         */
        val Default = Wiktionary
    }
}
