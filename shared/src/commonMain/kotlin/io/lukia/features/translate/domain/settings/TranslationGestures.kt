package io.lukia.features.translate.domain.settings

import io.lukia.core.domain.text.PassageUnit

/**
 * What a tap and a long press on the book ask for.
 *
 * Each does exactly one thing, always. An earlier version had a tap mean the
 * sentence except on an already-translated word, where it meant the word - which
 * reads well as a sentence and terribly as a gesture: the same finger in the
 * same place did different things depending on what had happened there before.
 *
 * Everything here starts off. An app that translates the moment a thumb brushes
 * the page spends somebody's money before they have said what it is for, so the
 * book behaves like a book until the reader asks for something else.
 *
 * Repeating a gesture on a passage it has already been used on is what puts the
 * book's own words back, and repeating it again brings the translation back - so
 * nothing needs a second gesture to undo it.
 */
data class TranslationGestures(
    /**
     * Whether a tap translates what is under it at all.
     *
     * Its own field, apart from [tapUnit], because the two are set in two places
     * and must not undo each other. Whether a tap translates is a switch on the
     * Translating page, where it belongs to everybody - a word goes to a
     * dictionary that needs nothing set up. How much it asks for is on the AI
     * translation page, because only a model answers a sentence.
     *
     * They were one nullable field once, written by both controls in turn - so
     * setting the unit row to "off" turned the other page's switch off, and
     * touching the unit row with the switch off turned translating on unasked.
     */
    val tapTranslates: Boolean = false,
    /**
     * How much of the book a tap asks for when it does translate.
     *
     * The word unless the reader has raised it, and remembered while the tap is
     * off - so turning the tap back on gives them the setting they chose rather
     * than silently dropping to the word.
     */
    val tapUnit: PassageUnit = PassageUnit.Word,
    /**
     * What a long press asks for, or null for nothing.
     *
     * One nullable field rather than the tap's two, because a hold is set in one
     * place only. Holding a word that has already been answered opens what else
     * it means - see [wordDetails] - and translating something larger by holding
     * it is offered on the AI translation page and nowhere else, so there is no
     * second control for its own off to disagree with.
     */
    val longPress: PassageUnit? = null,
    /**
     * Whether holding a word that has already been looked up opens what else it
     * means - every sense a dictionary has for it, and a way through to a site
     * about it.
     *
     * Its own setting rather than something [longPress] implies, because they
     * are different questions. A reader who set their hold to the sentence has
     * said what they want translated, not whether an already-looked-up word
     * should tell them more - and tied to the unit, that reader could never
     * reach any of it, which is what happened before this existed.
     *
     * So it applies whatever the hold is set to, including off, and only to a
     * word that already has an answer on the page.
     */
    val wordDetails: Boolean = false,
) {

    /** What a tap actually asks for, or null when it asks for nothing. */
    val tap: PassageUnit? get() = tapUnit.takeIf { tapTranslates }

    /**
     * The tap set to one unit or turned off, as a single control would set it -
     * the reader's own panel offers exactly that where a translation service is
     * configured.
     *
     * Turning it off keeps [tapUnit], so the unit the reader chose survives the
     * gesture being switched off and on again.
     */
    fun tapping(unit: PassageUnit?): TranslationGestures =
        copy(tapTranslates = unit != null, tapUnit = unit ?: tapUnit)

    /**
     * These gestures as they behave with no translation service behind them: a
     * tap asks for the word, and a hold translates nothing.
     *
     * A view rather than a change. Nothing but a model answers a sentence or a
     * paragraph, so with none set up - or AI switched off - a gesture set to one
     * could only come back as an error. What the reader chose stays in the
     * settings and returns the moment there is a service again.
     *
     * A tap still translates if it was translating, and holding a word for what
     * else it means still works: both are answered by a dictionary that needs
     * nothing.
     */
    fun withoutAModel(): TranslationGestures = copy(tapUnit = PassageUnit.Word, longPress = null)

    companion object {

        val Default = TranslationGestures()

        /**
         * A reader whose tap asks for [tap] and whose hold asks for [longPress],
         * written the way both used to be constructed.
         *
         * Kept because "what does a tap do" is one question everywhere except in
         * the two settings controls that set its halves separately, and spelling
         * out both fields at every one of those places would say the same thing
         * twice and let the two disagree.
         */
        fun of(
            tap: PassageUnit? = null,
            longPress: PassageUnit? = null,
            wordDetails: Boolean = false,
        ): TranslationGestures = TranslationGestures(
            tapTranslates = tap != null,
            tapUnit = tap ?: PassageUnit.Word,
            longPress = longPress,
            wordDetails = wordDetails,
        )
    }
}
