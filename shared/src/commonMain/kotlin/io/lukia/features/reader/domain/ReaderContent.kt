package io.lukia.features.reader.domain

import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.page.DocumentPages
import io.lukia.features.reader.domain.text.TextSection

/**
 * What the reader is showing, in the two shapes a document can take.
 *
 * The reader treats a book as numbered pages it scrolls through and comes back
 * to. For a PDF those are pages the platform draws and the file counts; for an
 * EPUB they are pages this app sets out of re-typeset text, so the count is
 * settled by the layout rather than the file and is not asked of anything here.
 *
 * Everything above - the scrubber, the remembered position, the contents, the
 * keyboard - needs only a count and an index, so it is written once for both.
 */
sealed interface ReaderContent : AutoCloseable {

    /**
     * How many parts the document is made of, in its own terms.
     *
     * Pages for a fixed-layout document, which are also the pages the reader
     * turns. Divisions for a reflowable one, which the reader's pages are cut
     * from - so this is deliberately not called a page count, and [PagedContent]
     * is the only thing outside the layout that may read it as one.
     *
     * Zero always means there is nothing in the document to show.
     */
    val partCount: Int

    /**
     * The document's own table of contents, or null when this document cannot be
     * asked for one - the same three-state distinction [DocumentPages] draws,
     * and for the same reason.
     */
    val outline: List<OutlineEntry>?
}

/** A document that carries its own layout, drawn page by page. */
class PagedContent(val pages: DocumentPages) : ReaderContent {

    override val partCount: Int get() = pages.pageCount

    override val outline: List<OutlineEntry>? get() = pages.outline

    override fun close() {
        pages.close()
    }
}

/**
 * A document the reader sets itself, out of the divisions the file states.
 *
 * What the pages are made from rather than the pages themselves: a section is a
 * chapter of whatever length its author wrote, and the reader cuts a run of them
 * into screenfuls - see `Paginator`. Hence [partCount] is not a page count.
 *
 * Held whole in memory, unlike [PagedContent] which keeps a file handle open.
 * Text is small - a long novel is a couple of megabytes - and having it all
 * present is what lets the book be re-set the instant the type size changes.
 */
class ReflowableContent(
    val sections: List<TextSection>,
    override val outline: List<OutlineEntry>?,
) : ReaderContent {

    override val partCount: Int get() = sections.size

    /** Nothing to give back: there is no open handle behind this one. */
    override fun close() = Unit
}
