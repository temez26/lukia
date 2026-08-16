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
    val level: Int
)
