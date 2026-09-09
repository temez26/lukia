package io.lukia.features.reader.domain.text

/**
 * Where in a book a translated sentence lives.
 *
 * Held as indices rather than text offsets or the sentence itself, so it
 * survives everything the reader does to an open book: re-set at a larger type
 * size, the sentence is still in the same block; redrawn at a different width,
 * still on the same page. An anchor built from offsets would be stale the moment
 * somebody pinched the screen.
 *
 * The two shapes mirror [io.lukia.features.reader.domain.ReaderContent]: a book
 * the reader sets itself is addressed by section, and one that carries its own
 * layout by page.
 */
sealed interface TextAnchor {

    /**
     * Which sentence of the passage this points at, or null when the whole
     * block is what was asked about.
     *
     * Null rather than a fourth shape of anchor, because everything that files
     * these already asks "which sentence" and would otherwise need a second
     * question first. A paragraph is the block with nothing narrower named.
     */
    val sentenceIndex: Int?

    /**
     * Which word of that sentence, or null when the sentence itself is what was
     * asked about.
     *
     * A word is addressed through its sentence rather than through the passage,
     * because the passage is not stable: translating one sentence changes how
     * many words come before every sentence after it, while the fourth word of
     * the second sentence is still the fourth word of the second sentence.
     */
    val wordIndex: Int?

    /** A sentence inside one block of one section of a reflowable book. */
    data class Section(
        val sectionIndex: Int,
        val blockIndex: Int,
        override val sentenceIndex: Int?,
        override val wordIndex: Int? = null,
    ) : TextAnchor

    /**
     * A sentence on one page of a fixed-layout document, numbered across the
     * whole page rather than within its block.
     *
     * [blockIndex] names a paragraph of the page - inferred from the space left
     * between lines, since a PDF records none of its own. Only a paragraph asked
     * about whole carries it: a sentence is addressed by its number on the page
     * and nothing else, so that the same sentence tapped and asked for in bulk
     * is the same passage rather than two of them.
     */
    data class Page(
        val pageIndex: Int,
        override val sentenceIndex: Int?,
        override val wordIndex: Int? = null,
        val blockIndex: Int? = null,
    ) : TextAnchor
}
