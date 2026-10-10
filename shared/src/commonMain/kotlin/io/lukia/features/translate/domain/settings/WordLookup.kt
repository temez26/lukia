package io.lukia.features.translate.domain.settings

/**
 * What answers when the reader asks about a single word.
 *
 * Only a word. Which sense of "piece" a sentence means is settled by the
 * paragraph around it and by what the book is, which is why the translator is
 * given all of it. A word is the one case where something knowing none of that
 * can still be right, and where a reader may prefer it: a dictionary gives every
 * sense rather than the one a model chose.
 *
 * Both are ordinary web APIs needing nothing set up, which is what makes a word
 * the one thing Lukia can always answer. The translation service used to be a
 * third choice here: it is the page the reader has to configure and the one that
 * costs money, and offering it for the unit that never needed it made the app
 * look unable to translate anything until a key was pasted in.
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
