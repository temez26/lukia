package io.lukia.features.translate.domain.text

/**
 * How much of the book one gesture asks about.
 *
 * The three are not interchangeable, and which one a reader wants is not
 * something this app can decide for them: somebody reading a language they know
 * well wants the one word they do not, somebody a chapter into a language they
 * are learning wants the sentence, and somebody skimming a page of a language
 * they barely have wants the paragraph. So it is a setting, and this is what it
 * is set to.
 *
 * [Word] is also the only one a plain dictionary can answer, which is why it is
 * the only one whose provider can be chosen - see [WordLookup].
 */
enum class PassageUnit {

    /** One word, on its own. */
    Word,

    /** The sentence it sits in. */
    Sentence,

    /** The whole paragraph, replaced at once. */
    Paragraph,
}
