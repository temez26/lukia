package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.plainText

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
internal const val MAX_BLOCKS_PER_SECTION = 400

/** A document divided into what the reader lists, and the way into it. */
internal data class SectionedText(
    val sections: List<TextSection>,
    val outline: List<OutlineEntry>,
)

/**
 * The block each section begins at, dividing a flat run at the document's own
 * top-level headings and, wherever those run out, at [MAX_BLOCKS_PER_SECTION].
 *
 * Which level counts as top is whatever the document actually uses rather than a
 * fixed `<h1>`: plenty of documents start at level two, or number their headings
 * from wherever the template left off, and a book whose every heading is an
 * `<h2>` should still be divided at its chapters.
 *
 * Empty for an empty document, and otherwise always starting at zero, so the
 * result addresses every block exactly once.
 */
internal fun sectionStarts(blocks: List<TextBlock>): List<Int> {
    val topLevel = topHeadingLevel(blocks)

    val starts = mutableListOf<Int>()
    var blocksInSection = 0

    blocks.forEachIndexed { index, block ->
        val startsSection = block is TextBlock.Heading && block.level == topLevel

        if (starts.isEmpty() || startsSection || blocksInSection >= MAX_BLOCKS_PER_SECTION) {
            starts += index
            blocksInSection = 0
        }

        blocksInSection++
    }

    return starts
}

/**
 * A flat run of blocks as a book the reader can show.
 *
 * The last stage of every format whose text arrives in one piece - DOCX,
 * Markdown and plain text - which each spelled it out for themselves. How the
 * blocks come out of the file is what those readers are for; what happens to
 * them afterwards is one question asked three times.
 *
 * A book with nothing in it comes back as an empty book rather than as no book
 * at all. The two are different things to tell a reader - "there is nothing to
 * read in this" against "this is not a file I can read" - and which one it is
 * is settled above, by there being no sections in it.
 */
internal fun reflowableFrom(blocks: List<TextBlock>): ReflowableContent {
    val sectioned = sectionText(blocks)

    return ReflowableContent(sections = sectioned.sections, outline = sectioned.outline)
}

/**
 * Divides a flat run of blocks into sections, and reads a table of contents off
 * the same headings that divided it.
 *
 * Shared by every format whose text arrives as one continuous run - DOCX,
 * Markdown and plain text. An EPUB does not come through here whole: that format
 * states its own divisions in the spine, and second-guessing them from the
 * headings would be overruling the person who made the book.
 */
internal fun sectionText(blocks: List<TextBlock>): SectionedText {
    val topLevel = topHeadingLevel(blocks)
    val starts = sectionStarts(blocks)

    val sections = starts.mapIndexed { index, start ->
        TextSection(
            // A section broken up for length is a continuation of the one before
            // it and has no name of its own, which is more honest than repeating
            // the chapter title over every piece of it.
            title = (blocks[start] as? TextBlock.Heading)
                ?.takeIf { it.level == topLevel }
                ?.text
                ?.plainText()
                ?.trim()
                ?.ifEmpty { null },
            blocks = blocks.subList(start, starts.getOrElse(index + 1) { blocks.size }).toList(),
        )
    }

    return SectionedText(
        sections = sections,
        outline = outlineOf(sections, topLevel),
    )
}

private fun topHeadingLevel(blocks: List<TextBlock>): Int? =
    blocks.filterIsInstance<TextBlock.Heading>().minOfOrNull { it.level }

/**
 * The table of contents these sections offer.
 *
 * Two levels deep: the headings that divide the document, and the headings one
 * step under them. Deeper than that is a document's own numbering scheme rather
 * than a way to get around it, and a contents panel listing every sub-sub-heading
 * is one nobody can find a chapter in.
 *
 * Sub-headings carry where in the section they sit as well as which section it
 * is, so that the layout can send the reader to the page the heading is actually
 * on rather than to the top of the chapter containing it.
 */
private fun outlineOf(sections: List<TextSection>, topLevel: Int?): List<OutlineEntry> {
    if (topLevel == null) return emptyList()

    return sections.flatMapIndexed { index, section ->
        section.blocks.mapIndexedNotNull { blockIndex, block ->
            val heading = block as? TextBlock.Heading

            if (heading == null || heading.level !in topLevel..topLevel + 1) return@mapIndexedNotNull null

            val title = heading.text.plainText().trim()

            if (title.isEmpty()) {
                null
            } else {
                OutlineEntry(
                    title = title,
                    pageIndex = index,
                    level = heading.level - topLevel,
                    blockIndex = blockIndex,
                )
            }
        }
    }
}
