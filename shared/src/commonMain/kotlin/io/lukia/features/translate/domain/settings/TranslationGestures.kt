package io.lukia.features.translate.domain.settings

import io.lukia.core.domain.text.PassageUnit

/**
 * What a tap and a long press on the book ask for.
 *
 * Each does exactly one thing, always. An earlier version had a tap mean the
 * sentence except on a word that had already been translated, where it meant the
 * word - which reads well as a sentence and terribly as a gesture: the same
 * finger in the same place did different things depending on what had happened
 * there before. A gesture the reader has set is a gesture that does that.
 *
 * Null is off, and off is where both of them start. A reading app that
 * translates the moment a thumb brushes the page is one that spends somebody's
 * money before they have said what it is for - and every one of these ends at a
 * service the reader has to set up themselves. So the book behaves like a book
 * until the reader asks for something else: they turn the tap on for sentences,
 * or the hold on for words, or neither.
 *
 * Repeating a gesture on a passage it has already been used on is what puts the
 * book's own words back, and repeating it again brings the translation back - so
 * nothing needs a second gesture to undo it.
 */
data class TranslationGestures(
    val tap: PassageUnit? = null,
    val longPress: PassageUnit? = null,
    /**
     * Whether holding a word that has already been looked up opens what else it
     * means - every sense a dictionary has for it, and a way through to a site
     * about it.
     *
     * Its own setting rather than something [longPress] implies, because the two
     * are not the same question. A reader who has set their hold to the sentence
     * has said what they want translated; they have said nothing about whether a
     * word they already looked up should be able to tell them more. Tied to the
     * unit, that reader could never reach any of it - which is exactly what
     * happened before this existed.
     *
     * So it applies whatever the hold is set to, including off, and it applies
     * only to a word that has an answer on the page already. A word nobody has
     * asked about is not a word with more to say.
     *
     * Off to begin with, like the two gestures above it: nothing here does
     * anything until the reader has said translating is something they want.
     */
    val wordDetails: Boolean = false,
) {

    /** Whether anything at all happens when the reader points at the book. */
    val translatesAnything: Boolean get() = tap != null || longPress != null

    companion object {
        val Default = TranslationGestures()
    }
}
