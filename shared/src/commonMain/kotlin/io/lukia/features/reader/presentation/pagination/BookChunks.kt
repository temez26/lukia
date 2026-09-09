package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.text.TextSection

/**
 * Where a book can be cut into runs that lay out on their own, as the index of
 * the section each run begins at.
 *
 * The cut is only made at a section that names itself, the one place where
 * laying the rest out separately changes nothing: a named section opens a page
 * of its own - see [paginate] - so nothing either side of the cut could have
 * shared a page. A book cut here and reassembled is page for page the same as
 * one laid out end to end; `PaginatorTest` holds that. Which is what lets the
 * reader see a book before it has finished measuring.
 *
 * Runs are not made as small as the cuts allow. A chapter is often a page or
 * two, and a thousand of them is a thousand pieces of work for no more
 * parallelism than a few dozen gives - so a run grows to [MIN_BLOCKS_PER_CHUNK]
 * blocks and is cut at the first chapter after that. A book with no named
 * sections comes back as one run.
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
