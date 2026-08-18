package io.lukia.features.translate.domain

/**
 * What a tap and a long press on the book ask for.
 *
 * Each does exactly one thing, always. An earlier version had a tap mean the
 * sentence except on a word that had already been translated, where it meant the
 * word - which reads well as a sentence and terribly as a gesture: the same
 * finger in the same place did different things depending on what had happened
 * there before. A gesture the reader has set is a gesture that does that.
 *
 * Null is off. A reader who only ever wants to look words up turns the tap off
 * and stops translating sentences by accident; one who never wants to wait for a
 * whole paragraph turns the long press off. Both off leaves a reading app that
 * does not translate, which is a perfectly reasonable thing to want.
 *
 * Repeating a gesture on a passage it has already been used on is what puts the
 * book's own words back, and repeating it again brings the translation back - so
 * nothing needs a second gesture to undo it.
 */
data class TranslationGestures(
    val tap: PassageUnit? = PassageUnit.Sentence,
    val longPress: PassageUnit? = PassageUnit.Word
) {

    /** Whether anything at all happens when the reader points at the book. */
    val translatesAnything: Boolean get() = tap != null || longPress != null

    companion object {
        val Default = TranslationGestures()
    }
}
