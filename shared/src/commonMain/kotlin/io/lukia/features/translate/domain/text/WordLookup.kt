package io.lukia.features.translate.domain.text

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
enum class WordLookup(
    val label: String,
    /** What is worth saying under the choice, which differs for each. */
    val footnote: String,
) {

    /**
     * The translator the reader has already set up, asked the same way it is
     * asked about a sentence - with the book and the paragraph attached.
     */
    Translator(
        label = "Translator",
        footnote = "Words go to the same service as sentences, with the paragraph " +
            "around them - so the answer is the sense the book meant.",
    ),

    /**
     * Google Translate, which needs nothing set up and knows nothing about the
     * book.
     */
    Google(
        label = "Google",
        footnote = "Fast and free, and it never sees the book - only the word. " +
            "Add a Cloud Translation key below to use Google's supported API " +
            "rather than the public endpoint.",
    ),

    /**
     * Wiktionary, which is a dictionary rather than a translator: it gives the
     * senses a word has instead of choosing one.
     */
    Wiktionary(
        label = "Dictionary",
        footnote = "Wiktionary's own entry for the word: every sense it has, " +
            "rather than the one sense something guessed at. Free, and nothing " +
            "to set up.",
    ),
    ;

    companion object {
        val Default = Translator
    }
}
