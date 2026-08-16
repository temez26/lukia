package io.lukia.features.reader.domain

/**
 * What the reader is showing, in the two shapes a document can take.
 *
 * The reader treats a book as a list of numbered things it can scroll through
 * and come back to. For a PDF those are pages the platform draws; for an EPUB
 * they are sections the reader sets itself. Everything above this - the
 * scrubber, the remembered position, the table of contents, the keyboard - only
 * needs [unitCount] and an index, so all of it is written once and works for
 * both.
 */
sealed interface ReaderContent : AutoCloseable {

    /**
     * How many items the book has. Every position in the reader is an index into
     * this, whatever the items happen to be.
     */
    val unitCount: Int

    /**
     * The document's own table of contents, or null when this document cannot be
     * asked for one - the same three-state distinction [DocumentPages] draws,
     * and for the same reason.
     */
    val outline: List<OutlineEntry>?

    /** What one item is called, for the places that say so out loud. */
    val unit: ReadingUnit
}

/**
 * The noun the reader uses for where someone is.
 *
 * "Page 4 of 300" is right for a PDF and wrong for an EPUB, whose sections are
 * chapters of no fixed length - calling them pages would promise a
 * correspondence to a printed book that reflowed text does not have.
 */
enum class ReadingUnit(val singular: String, val plural: String) {
    Page("Page", "pages"),
    Section("Section", "sections")
}

/** A document that carries its own layout, drawn page by page. */
class PagedContent(val pages: DocumentPages) : ReaderContent {

    override val unitCount: Int get() = pages.pageCount

    override val outline: List<OutlineEntry>? get() = pages.outline

    override val unit: ReadingUnit = ReadingUnit.Page

    override fun close() {
        pages.close()
    }
}

/**
 * A document the reader sets itself, section by section.
 *
 * Held whole in memory, unlike [PagedContent] which keeps a file handle open.
 * Text is small - a long novel is a couple of megabytes of it - and having it
 * all present is what lets the reader re-set the entire book the instant someone
 * changes the type size.
 */
class ReflowableContent(
    val sections: List<TextSection>,
    override val outline: List<OutlineEntry>?
) : ReaderContent {

    override val unitCount: Int get() = sections.size

    override val unit: ReadingUnit = ReadingUnit.Section

    /** Nothing to give back: there is no open handle behind this one. */
    override fun close() = Unit
}
