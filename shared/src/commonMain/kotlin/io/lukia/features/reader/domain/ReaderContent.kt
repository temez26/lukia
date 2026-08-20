package io.lukia.features.reader.domain

/**
 * What the reader is showing, in the two shapes a document can take.
 *
 * The reader treats a book as a list of numbered pages it can scroll through and
 * come back to. For a PDF those are pages the platform draws, and the file says
 * how many there are. For an EPUB they are pages this app sets, out of text it
 * has re-typeset for the window - so how many there are is settled by the
 * layout rather than by the file, and is not asked of anything here.
 *
 * Everything above this - the scrubber, the remembered position, the table of
 * contents, the keyboard - needs only a count and an index, so all of it is
 * written once and works for both.
 */
sealed interface ReaderContent : AutoCloseable {

    /**
     * How many parts the document is made of, in its own terms.
     *
     * Pages for a fixed-layout document, which are also the pages the reader
     * turns. Divisions for a reflowable one, which are what the reader's pages
     * are cut from and not pages themselves - so this is deliberately not called
     * a page count, and the only thing outside the layout that may read it as
     * one is [PagedContent].
     *
     * What every reader of this can rely on is that zero means there is nothing
     * in the document to show.
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
 * These are what the pages are made from rather than the pages themselves: a
 * section is a chapter, of whatever length its author wrote, and the reader cuts
 * a run of them into screenfuls - see `Paginator`. That is why [partCount] is
 * not a page count and nothing outside the layout should read it as one.
 *
 * Held whole in memory, unlike [PagedContent] which keeps a file handle open.
 * Text is small - a long novel is a couple of megabytes of it - and having it
 * all present is what lets the reader re-set the entire book the instant someone
 * changes the type size.
 */
class ReflowableContent(
    val sections: List<TextSection>,
    override val outline: List<OutlineEntry>?,
) : ReaderContent {

    override val partCount: Int get() = sections.size

    /** Nothing to give back: there is no open handle behind this one. */
    override fun close() = Unit
}
