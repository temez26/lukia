package io.lukia.features.translate.domain

/**
 * Where in a book a translated sentence lives.
 *
 * Held as indices rather than as text offsets or as the sentence itself, so
 * that it survives everything the reader does to a book while it is open: a
 * reflowable book re-set at a larger type size has the same sentence in the
 * same block, and a page redrawn at a different width has the same sentence on
 * the same page. An anchor built from offsets would be stale the moment
 * somebody pinched the screen.
 *
 * The two shapes mirror [io.lukia.features.reader.domain.ReaderContent]: a book
 * the reader sets itself is addressed by section, and one that carries its own
 * layout by page.
 */
sealed interface TextAnchor {

    /** Which sentence of the passage this points at. */
    val sentenceIndex: Int

    /** A sentence inside one block of one section of a reflowable book. */
    data class Section(
        val sectionIndex: Int,
        val blockIndex: Int,
        override val sentenceIndex: Int
    ) : TextAnchor

    /**
     * A sentence on one page of a fixed-layout document. There are no blocks
     * here: a PDF's text has no structure the reader can rely on, so its page
     * is read as one run of sentences in the order they were laid out.
     */
    data class Page(
        val pageIndex: Int,
        override val sentenceIndex: Int
    ) : TextAnchor
}
