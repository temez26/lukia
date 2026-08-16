package io.lukia.features.reader.domain

/** What a stretch of words is marked as, over and above being words. */
enum class Emphasis {
    Bold,
    Italic,
    /** Set in a fixed-pitch face: `code` in Markdown, `<code>` in an EPUB. */
    Monospace
}

/**
 * A stretch of text carrying one set of marks.
 *
 * A paragraph is a list of these rather than a string, because emphasis is
 * inside a sentence and not around it - "the *Pequod* sailed" is three runs, and
 * flattening it to plain text is losing something the author wrote.
 */
data class TextRun(
    val text: String,
    val emphasis: Set<Emphasis> = emptySet()
)

/** The text of these runs with every mark dropped - for outlines and titles. */
fun List<TextRun>.plainText(): String = joinToString(separator = "") { it.text }

fun List<TextRun>.isBlank(): Boolean = all { it.text.isBlank() }

/**
 * One thing on the page of a reflowable book.
 *
 * A deliberately short list. Everything a book actually needs to read correctly
 * is here, and nothing is here because a format happens to be able to express it
 * - EPUBs carry arbitrary HTML and stylesheets, and honouring all of that would
 * be building a browser rather than a reader. What the reader guarantees instead
 * is that the words arrive in the right order, in the right structure, set the
 * way the person reading them asked for.
 */
sealed interface TextBlock {

    data class Heading(val text: List<TextRun>, val level: Int) : TextBlock

    data class Paragraph(val text: List<TextRun>) : TextBlock

    /**
     * [marker] is the bullet or number as it should be shown, decided by whoever
     * parsed the list - the reader draws what it is given rather than counting,
     * since a list that starts at 4 is a thing books do.
     */
    data class ListItem(val text: List<TextRun>, val marker: String) : TextBlock

    data class Quote(val text: List<TextRun>) : TextBlock

    /** Preformatted: whitespace and line breaks are content, not layout. */
    data class Code(val text: String) : TextBlock

    /**
     * An illustration, held as the bytes the book stores rather than as a decoded
     * image. A book can carry hundreds, and decoding every one when the book is
     * opened would cost more memory than the text ever does - so this stays
     * encoded until the reader actually scrolls to it.
     *
     * Identity equality, inherited from [Any]: two figures are the same figure
     * when they are the same figure, and comparing megabytes of image data to
     * answer that would be work done on every recomposition.
     */
    class Figure(val bytes: ByteArray, val description: String?) : TextBlock

    /** A scene break, drawn as a rule rather than as the characters a book used. */
    data object Separator : TextBlock
}

/**
 * One item of a reflowable book, as the reader lists it: a chapter, or a slice
 * of one long unbroken document.
 *
 * Sections are what a reflowable book has instead of pages, and the reader
 * addresses them exactly as it addresses a PDF's pages - the scrubber, the
 * remembered position and the table of contents all index into this list. That
 * is why a book is split into them at all: a single item holding a whole novel
 * would be laid out in full every time it was drawn, and neither scrolling nor
 * the position of the scrollbar would mean anything.
 *
 * [title] is what the section is called where it names itself, which is what
 * lets a book with no table of contents of its own still offer one.
 */
data class TextSection(
    val title: String?,
    val blocks: List<TextBlock>
)
