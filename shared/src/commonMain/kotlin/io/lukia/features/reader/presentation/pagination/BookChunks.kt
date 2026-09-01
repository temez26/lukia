package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.text.TextSection

/**
 * Where a book can be cut into runs that lay out on their own, as the index of
 * the section each run begins at.
 *
 * The cut is only ever made at a section that names itself, because that is the
 * one place where laying the rest out separately changes nothing: a named
 * section opens a page of its own - see [paginate] - so the page before it was
 * already finished, and nothing on either side of the cut could have been on the
 * same page as anything on the other. A book cut here and reassembled is the
 * same book, page for page, as one laid out end to end. `PaginatorTest` holds
 * that to be true.
 *
 * Which is what lets the reader show a book before it has finished measuring
 * one: the runs are measured at the same time as each other, and each one that
 * arrives adds real pages to the end of what is already on screen.
 *
 * Runs are not made as small as the cuts allow. A chapter is often a page or
 * two, and a thousand of them would be a thousand pieces of work to hand around
 * for no more parallelism than a few dozen gives - so a run is grown until it
 * holds [MIN_BLOCKS_PER_CHUNK] blocks and cut at the first chapter after that.
 * A book with no named sections at all cannot be cut anywhere, and comes back as
 * one run, which is what it was before any of this.
 */
internal fun chunkStarts(sections: List<TextSection>): List<Int> {
    if (sections.isEmpty()) return emptyList()

    val starts = mutableListOf(0)
    var blocksInChunk = 0

    sections.forEachIndexed { index, section ->
        // Never before the first section: the book starting is not a cut, and
        // an empty first run would be a page count that begins at nothing.
        if (index > 0 && section.title != null && blocksInChunk >= MIN_BLOCKS_PER_CHUNK) {
            starts += index
            blocksInChunk = 0
        }

        blocksInChunk += section.blocks.size
    }

    return starts
}

/**
 * How much of a book is worth measuring as one piece.
 *
 * Low enough that the first run is measured while somebody is still watching the
 * screen change, and high enough that handing the work around costs less than
 * doing it. A few hundred blocks is a chapter or two of a novel.
 */
private const val MIN_BLOCKS_PER_CHUNK = 256
