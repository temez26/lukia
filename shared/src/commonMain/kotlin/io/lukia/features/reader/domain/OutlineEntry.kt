package io.lukia.features.reader.domain

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
     * A reflowable book names its chapters by file and its sections by an anchor
     * inside one - so a chapter of forty sections is forty entries that all
     * resolve to the same file. Without this they would all be the same
     * destination, and a contents panel of forty rows that do the same thing is
     * a panel that has parsed the book and then thrown the answer away.
     *
     * Zero, and ignored, for a document that carries its own pages: a PDF's
     * outline points at a page, and there is nothing finer to say.
     */
    val blockIndex: Int = 0,
)
