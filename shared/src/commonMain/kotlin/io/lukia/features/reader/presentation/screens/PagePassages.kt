package io.lukia.features.reader.presentation.screens

import io.lukia.features.translate.domain.PageSentence
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.sentenceAt
import io.lukia.features.translate.presentation.PassageSelection
import io.lukia.features.translate.presentation.SentenceTranslations

/**
 * What one page of a fixed-layout document can be asked about, and how a point
 * on it becomes a passage.
 *
 * Its own value rather than a handful of callbacks inside the page, for the same
 * reason [rememberPageActions] is one for set text: the page is a composable
 * that also draws a picture and answers a pinch, and where the reader's finger
 * landed in a book is not a drawing problem. Held here it can be reasoned about,
 * and tested, without composing anything.
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
     * Every sentence on the page, for when the reader asks for what is in front
     * of them rather than pointing at one thing.
     */
    fun all(): List<PassageSelection> = sentences.map { sentence ->
        PassageSelection(
            anchor = TextAnchor.Page(pageIndex = pageIndex, sentenceIndex = sentence.index),
            passage = sentence.text,
            surroundingText = pageText,
        )
    }

    /**
     * What a gesture at a point on the page asks about, or null when it asks
     * about nothing - which is a gesture that is turned off, one that landed in
     * a margin or on a figure, and one set to the word that landed on the
     * punctuation between two of them, since that belongs to neither.
     *
     * [translations] is read because a paragraph already translated has none of
     * its own sentences left on the page: a gesture inside one is a gesture on
     * the paragraph whatever it was set to, which is what puts the page's own
     * words back.
     */
    fun at(
        x: Float,
        y: Float,
        unit: PassageUnit?,
        translations: SentenceTranslations,
    ): PassageSelection? {
        if (unit == null) return null

        val sentence = sentences.sentenceAt(x, y) ?: return null
        val block = sentence.blockIndex

        if (unit == PassageUnit.Paragraph || translations.paragraph(block) != null) {
            return blockText[block]?.let { paragraph ->
                PassageSelection(
                    anchor = TextAnchor.Page(
                        pageIndex = pageIndex,
                        sentenceIndex = null,
                        blockIndex = block,
                    ),
                    passage = paragraph,
                    // A paragraph is its own context - see rememberPageActions.
                    surroundingText = paragraph,
                    unit = PassageUnit.Paragraph,
                )
            }
        }

        val word = if (unit == PassageUnit.Word) sentence.wordAt(x, y) else null
        if (unit == PassageUnit.Word && word == null) return null

        return PassageSelection(
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
}
