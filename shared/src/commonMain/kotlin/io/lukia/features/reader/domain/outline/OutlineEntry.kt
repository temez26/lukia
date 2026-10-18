package io.lukia.features.reader.domain.outline

/**
 * One entry in a document's own table of contents.
 *
 * The tree a PDF stores is flattened into a list carrying [level], because that
 * is what a scrolling list of chapters needs: nesting becomes indentation, and
 * a chapter with forty sections costs one list rather than forty.
 */
data class OutlineEntry(
    val title: String,
    /** Zero-based, matching how pages are addressed everywhere else. */
    val pageIndex: Int,
    /** 0 for a top-level chapter, 1 for a section inside it, and so on. */
    val level: Int,
    /**
     * How far into that part the entry actually begins, for a document the
     * reader lays out itself.
     *
     * A reflowable book names chapters by file and sections by an anchor inside
     * one, so a chapter of forty sections is forty entries resolving to the same
     * file. Without this they would be one destination, and forty rows that do
     * the same thing is a panel that parsed the book and threw the answer away.
     *
     * Zero and ignored for a document with its own pages: a PDF's outline points
     * at a page, and there is nothing finer to say.
     */
    val blockIndex: Int = 0,
    /**
     * False while the reader is still laying out the part of the book this entry
     * is in. [pageIndex] is then only how far the layout has got: not a page
     * number to show, nor somewhere to count the reader as being.
     */
    val placed: Boolean = true,
)
