package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.domain.plainText

/**
 * How many blocks one section may hold before it is broken up.
 *
 * A section is one item in the reader's list, and an item is laid out in full
 * whenever it is drawn. A chapter of a few hundred paragraphs is nothing; a
 * document that is one unbroken run of ten thousand is a reader that stutters on
 * every scroll and a scrollbar that means nothing, because there is only one
 * thing to be anywhere within.
 *
 * Generously above any real chapter, so that a book which is properly divided is
 * divided exactly where its author divided it, and this only ever acts on the
 * documents that are not.
 */
private const val MAX_BLOCKS_PER_SECTION = 400

/** A document divided into what the reader lists, and the way into it. */
internal data class SectionedText(
    val sections: List<TextSection>,
    val outline: List<OutlineEntry>,
)

/**
 * Divides a flat run of blocks into sections at the document's own top-level
 * headings, and reads a table of contents off the same headings.
 *
 * Which level counts as top is whatever the document actually uses rather than a
 * fixed `<h1>`: plenty of documents start at level two, or number their headings
 * from wherever the template left off, and a book whose every heading is an
 * `<h2>` should still be divided at its chapters.
 *
 * Shared by every format whose text arrives as one continuous run - DOCX,
 * Markdown and plain text. EPUB does not come through here: that format states
 * its own divisions in the spine, and second-guessing them from the headings
 * would be overruling the person who made the book.
 */
internal fun sectionText(blocks: List<TextBlock>): SectionedText {
    val topLevel = blocks.filterIsInstance<TextBlock.Heading>().minOfOrNull { it.level }

    val sections = mutableListOf<TextSection>()
    var current = mutableListOf<TextBlock>()
    var title: String? = null

    fun flush() {
        if (current.isEmpty()) return

        sections += TextSection(title = title, blocks = current.toList())
        current = mutableListOf()
    }

    blocks.forEach { block ->
        val startsSection = block is TextBlock.Heading && block.level == topLevel

        if (startsSection || current.size >= MAX_BLOCKS_PER_SECTION) {
            flush()

            // A section broken up for length is a continuation of the one before
            // it and has no name of its own, which is more honest than repeating
            // the chapter title over every piece of it.
            title = (block as? TextBlock.Heading)
                ?.takeIf { startsSection }
                ?.text
                ?.plainText()
                ?.trim()
                ?.ifEmpty { null }
        }

        current += block
    }

    flush()

    return SectionedText(
        sections = sections,
        outline = outlineOf(sections, topLevel),
    )
}

/**
 * The table of contents these sections offer.
 *
 * Two levels deep: the headings that divide the document, and the headings one
 * step under them. Deeper than that is a document's own numbering scheme rather
 * than a way to get around it, and a contents panel listing every sub-sub-heading
 * is one nobody can find a chapter in.
 *
 * Sub-headings point at the section that contains them rather than at themselves,
 * because a section is the smallest thing the reader can scroll to. Landing on
 * the chapter and reading a few paragraphs to the heading you wanted is a far
 * better outcome than not offering it at all.
 */
private fun outlineOf(sections: List<TextSection>, topLevel: Int?): List<OutlineEntry> {
    if (topLevel == null) return emptyList()

    return sections.flatMapIndexed { index, section ->
        section.blocks
            .filterIsInstance<TextBlock.Heading>()
            .filter { it.level in topLevel..topLevel + 1 }
            .mapNotNull { heading ->
                val title = heading.text.plainText().trim()

                if (title.isEmpty()) {
                    null
                } else {
                    OutlineEntry(
                        title = title,
                        pageIndex = index,
                        level = heading.level - topLevel,
                    )
                }
            }
    }
}
