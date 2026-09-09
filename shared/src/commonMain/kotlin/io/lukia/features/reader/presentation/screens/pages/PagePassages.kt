package io.lukia.features.reader.presentation.screens.pages

import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.reader.domain.page.PageSentence
import io.lukia.features.reader.domain.page.PageWord
import io.lukia.features.reader.domain.page.sentenceAt
import io.lukia.features.reader.domain.text.TextAnchor
import io.lukia.features.reader.presentation.translation.PassageSelection
import io.lukia.features.reader.presentation.translation.SentenceTranslations
import io.lukia.features.reader.presentation.translation.replacesTheOriginal
import io.lukia.features.translate.domain.MAX_TRANSLATABLE_CHARS

/**
 * What one page of a fixed-layout document can be asked about, and how a point
 * on it becomes a passage.
 *
 * Its own value rather than callbacks inside the page, for the same reason
 * [rememberPageActions] is one for set text: the page already draws a picture
 * and answers a pinch, and where a finger landed in a book is not a drawing
 * problem. Held here it can be reasoned about, and tested, uncomposed.
 *
 * Everything it is asked is in the *file's* own units. What relates those to the
 * pixels on screen is the one scale the page keeps, and it belongs there.
 */
internal class PagePassages(
    private val pageIndex: Int,
    private val sentences: List<PageSentence>,
) {

    /**
     * Every word on the page joined up, which is the context a sentence on it
     * travels with - a page has no chapter of its own to name.
     *
     * Built once per page rather than once per tap: a reader works through a
     * page a tap at a time.
     */
    private val pageText: String = sentences.joinToString(separator = " ") { it.text }

    /** Each inferred paragraph of the page, joined back up - see [PageSentence.blockIndex]. */
    private val blockText: Map<Int, String> = sentences
        .groupBy { it.blockIndex }
        .mapValues { (_, inBlock) -> inBlock.joinToString(separator = " ") { it.text } }

    val isEmpty: Boolean get() = sentences.isEmpty()

    /**
     * The whole page, as the passages a standing order asks about: its
     * paragraphs, one request each, rather than every sentence on it.
     *
     * A page is thirty or forty sentences and a handful of paragraphs, and the
     * difference is most of what translating a page costs - see
     * `ReaderPageActions.bulkPassages`. A block too long to send whole falls
     * back to its own sentences - see [MAX_TRANSLATABLE_CHARS].
     */
    fun all(): List<PassageSelection> = sentences
        .groupBy { it.blockIndex }
        .flatMap { (blockIndex, inBlock) -> bulk(blockIndex, inBlock) }

    private fun bulk(blockIndex: Int, inBlock: List<PageSentence>): List<PassageSelection> {
        val paragraph = paragraph(blockIndex)

        if (paragraph == null || paragraph.passage.length > MAX_TRANSLATABLE_CHARS) {
            return inBlock.map { passage(it, word = null) }
        }

        return listOf(paragraph)
    }

    /**
     * What a gesture at a point on the page asks about, or null when it asks
     * about nothing - which is a gesture that is turned off, one that landed in
     * a margin or on a figure, and one set to the word that landed on the
     * punctuation between two of them, since that belongs to neither.
     */
    fun at(
        x: Float,
        y: Float,
        unit: PassageUnit?,
        translations: SentenceTranslations,
    ): PassageSelection? {
        if (unit == null) return null

        val sentence = sentences.sentenceAt(x, y) ?: return null

        // What the reader asked for is the last question, not the first. A
        // passage already spoken for answers as itself however the gesture is
        // set - a paragraph because it is one passage now and the sentences it
        // was made of are not separately on offer, and anything with its
        // translation drawn over it because its own words are not on the page to
        // be pointed at. Only what still shows the page's own words is read as
        // the unit the reader chose.
        return wholeParagraph(sentence, unit, translations)
            ?: covered(sentence, x, y, translations)
            ?: chosen(sentence, x, y, unit)
    }

    /**
     * The passage whose translation is drawn at this point, if one is.
     *
     * This is what a tap on the translation itself asks, and it takes no gesture
     * setting because there is nothing ambiguous left to settle: a passage with
     * its translation over it has none of its own words on screen, so pointing
     * there can only mean that passage, and what the reader wants is the book's
     * words back.
     *
     * Asked separately because the translation is not drawn inside the words it
     * replaces: a page cannot be re-set the way a reflowable book can, so the
     * panel is laid over it, starting at the passage's corner and free to run
     * wider and further down than the passage did. Hit-testing the original's
     * rectangles therefore misses most of what the reader can see - and misses a
     * single word almost entirely, its panel being several times the word's size.
     */
    fun translationAt(x: Float, y: Float, translations: SentenceTranslations): PassageSelection? {
        val sentence = sentences.sentenceAt(x, y) ?: return null

        if (translations.paragraph(sentence.blockIndex).replacesTheOriginal) {
            return paragraph(sentence.blockIndex)
        }

        return covered(sentence, x, y, translations)
    }

    /** The paragraph here, when the gesture asks for one or one has been asked about. */
    private fun wholeParagraph(
        sentence: PageSentence,
        unit: PassageUnit,
        translations: SentenceTranslations,
    ): PassageSelection? = paragraph(sentence.blockIndex).takeIf {
        unit == PassageUnit.Paragraph || translations.paragraph(sentence.blockIndex) != null
    }

    /** Whichever of the page's own passages the gesture was set to ask about. */
    private fun chosen(
        sentence: PageSentence,
        x: Float,
        y: Float,
        unit: PassageUnit,
    ): PassageSelection? {
        if (unit != PassageUnit.Word) return passage(sentence, word = null)

        // A gesture set to the word that landed between two of them asks about
        // nothing: the punctuation between two words belongs to neither.
        return sentence.wordAt(x, y)?.let { passage(sentence, it) }
    }

    /** The sentence or the single word at this point that is covered by its translation. */
    private fun covered(
        sentence: PageSentence,
        x: Float,
        y: Float,
        translations: SentenceTranslations,
    ): PassageSelection? {
        if (translations[sentence.index].replacesTheOriginal) return passage(sentence, word = null)

        val word = sentence.wordAt(x, y) ?: return null

        return passage(sentence, word)
            .takeIf { translations.words(sentence.index)[word.index].replacesTheOriginal }
    }

    /** One whole paragraph of the page, which is its own context. */
    private fun paragraph(blockIndex: Int): PassageSelection? =
        blockText[blockIndex]?.let { paragraph ->
            PassageSelection(
                anchor = TextAnchor.Page(
                    pageIndex = pageIndex,
                    sentenceIndex = null,
                    blockIndex = blockIndex,
                ),
                passage = paragraph,
                // A paragraph is its own context - see rememberPageActions.
                surroundingText = paragraph,
                unit = PassageUnit.Paragraph,
            )
        }

    private fun passage(sentence: PageSentence, word: PageWord?) = PassageSelection(
        anchor = TextAnchor.Page(
            pageIndex = pageIndex,
            sentenceIndex = sentence.index,
            wordIndex = word?.index,
        ),
        passage = word?.text ?: sentence.text,
        surroundingText = pageText,
        unit = if (word == null) PassageUnit.Sentence else PassageUnit.Word,
    )
}
