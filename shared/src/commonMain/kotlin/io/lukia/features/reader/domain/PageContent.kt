package io.lukia.features.reader.domain

/**
 * What is written and drawn on a document's pages, without the means to draw
 * them.
 *
 * Split out of [DocumentPages] because re-setting a PDF as text needs this half
 * of it and no more - and needs several at once. The conversion reads a book
 * through as many handles as there are cores to read it with, and a handle
 * opened for that should not also open a renderer, measure every page and walk
 * the table of contents: the document it was opened beside has done all three
 * already, and doing them again per core is the parallelism paying itself back.
 *
 * Holds an open handle on the file and is [AutoCloseable] for that reason -
 * whoever opens one owns closing it.
 */
interface PageContent : AutoCloseable {

    /**
     * The characters on page [index] and where they sit on it, or null when
     * there is no text to be had.
     *
     * Null covers two different things that the reader treats the same way: a
     * page with no text layer at all - a scan, a page that is one photograph -
     * and a platform that cannot read one. Neither is a failure worth reporting;
     * both mean the same thing to somebody looking at the page, which is that
     * tapping the words on it does nothing.
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
