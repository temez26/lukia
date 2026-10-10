package io.lukia.features.reader.domain.page

/**
 * What is written and drawn on a document's pages, without the means to draw
 * them.
 *
 * Split out of [DocumentPages] because re-setting a PDF as text needs this half
 * and no more - and needs several at once. The conversion reads a book through
 * one handle per core, and such a handle should not also open a renderer,
 * measure every page and walk the contents: the document it was opened beside
 * did all three already, and repeating them per core spends the parallelism.
 *
 * Holds an open handle on the file and is [AutoCloseable] for that reason -
 * whoever opens one owns closing it.
 */
interface PageContent : AutoCloseable {

    /**
     * The characters on page [index] and where they sit on it, or null when
     * there is no text to be had.
     *
     * Null covers two things the reader treats alike: a page with no text layer
     * - a scan, a page that is one photograph - and a platform that cannot read
     * one. Neither is a failure worth reporting, and both mean the same to
     * somebody looking at the page: tapping the words does nothing.
     */
    suspend fun pageText(index: Int): List<PageGlyph>?

    /**
     * The pictures drawn on page [index], and where each of them sits.
     *
     * Only asked of a document being re-set as text - a page that is drawn
     * carries its own pictures already, in place, and reading them back out
     * would be work to arrive at what the reader can see.
     *
     * Empty rather than null for a platform that cannot read them, because
     * nothing above this can act on the difference: a page with no pictures and
     * a page whose pictures cannot be had both re-set as the words alone, and
     * there is no version of this the reader would want to be told about.
     */
    suspend fun pageImages(index: Int): List<PageImage>
}
