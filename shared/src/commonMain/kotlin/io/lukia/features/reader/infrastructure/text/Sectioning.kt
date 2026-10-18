package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.plainText

/**
 * How many blocks one section may hold before it is broken up.
 *
 * A section is one item in the reader's list, laid out in full whenever drawn. A
 * chapter of a few hundred paragraphs is nothing; one unbroken run of ten
 * thousand stutters on every scroll and leaves a scrollbar that means nothing.
 *
 * Generously above any real chapter, so a properly divided book is divided where
 * its author divided it and this only acts on documents that are not.
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
 * Which level counts as top is whatever the document uses rather than a fixed
 * `<h1>`: plenty start at level two, or number from wherever the template left
 * off, and a book whose every heading is an `<h2>` should still be divided at
 * its chapters.
 *
 * Empty for an empty document, otherwise always starting at zero, so the result
 * addresses every block exactly once.
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
 * Markdown, plain text - each of which spelled it out for itself. How the blocks
 * come out of the file is what those readers are for; what happens afterwards is
 * one question asked three times.
 *
 * A book with nothing in it comes back empty rather than as no book: "there is
 * nothing to read in this" and "this is not a file I can read" are different
 * things to tell a reader.
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
            blocks = blocks.subList(start, starts.getOrElse(index + 1) { blocks.size }).withChapterOpening(),
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
 * Two levels deep: the headings that divide the document, and those one step
 * under. Deeper is a numbering scheme rather than a way to get around, and a
 * panel listing every sub-sub-heading is one nobody can find a chapter in.
 *
 * Sub-headings carry where in the section they sit as well as which section, so
 * the layout can send the reader to the heading's own page rather than the top
 * of the chapter.
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

/**
 * These blocks with the headings a chapter opens with marked as such - see
 * [TextBlock.Heading.chapter].
 *
 * The headings at the head of a section, before anything has been said: a
 * picture or a scene break may come first, a paragraph may not. A heading after
 * prose is one inside the chapter, whatever its level.
 *
 * Many books open a chapter with two: "CHAPTER 5:" and then its name. Short
 * headings ahead of the title are set as its label rather than as two titles;
 * the first that reads as a name is the title, and any after it are sections.
 */
internal fun List<TextBlock>.withChapterOpening(): List<TextBlock> {
    val firstProse = indexOfFirst { it.isProse() }.let { if (it == -1) size else it }
    val leading = (0 until firstProse).filter { this[it] is TextBlock.Heading }
    if (leading.isEmpty()) return toList()

    val titleAt = leading.firstOrNull { !(this[it] as TextBlock.Heading).readsAsLabel() } ?: leading.last()
    val labelled = leading.first() != titleAt

    return mapIndexed { index, block ->
        val heading = block as? TextBlock.Heading
        when {
            heading == null || index !in leading -> block
            index < titleAt -> heading.copy(chapter = TextBlock.ChapterRole.Label)
            index == titleAt -> heading.copy(
                chapter = if (labelled) TextBlock.ChapterRole.TitleUnderLabel else TextBlock.ChapterRole.Title,
            )
            else -> block
        }
    }
}

/** Longest a chapter label runs to: "Chapter Twenty-Three", "PART IV:". */
private const val LABEL_MAX_WORDS = 3
private const val LABEL_MAX_LENGTH = 24

/** A Roman numeral standing as a word of its own: "Part IV", "XII". */
private val RomanNumeral = Regex("""\b[IVXLC]+\b""")

/**
 * A "Chapter 5" or a "PART IV:" rather than a name: short, and numbered. Short
 * alone is not enough - "Introduction" followed by a heading of its own is a
 * title with a section under it, not a label over one.
 */
private fun TextBlock.Heading.readsAsLabel(): Boolean {
    val words = text.plainText().trim()
    val short = words.length <= LABEL_MAX_LENGTH && words.split(' ').size <= LABEL_MAX_WORDS

    return short && (words.any { it.isDigit() } || RomanNumeral.containsMatchIn(words))
}

private fun TextBlock.isProse(): Boolean =
    this is TextBlock.Paragraph || this is TextBlock.ListItem || this is TextBlock.Quote || this is TextBlock.Code
