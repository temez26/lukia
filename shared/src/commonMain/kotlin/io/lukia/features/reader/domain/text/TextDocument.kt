package io.lukia.features.reader.domain.text

/** What a stretch of words is marked as, over and above being words. */
enum class Emphasis {
    Bold,
    Italic,

    /** Set in a fixed-pitch face: `code` in Markdown, `<code>` in an EPUB. */
    Monospace,
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
    val emphasis: Set<Emphasis> = emptySet(),
)

/** The text of these runs with every mark dropped - for outlines and titles. */
fun List<TextRun>.plainText(): String = joinToString(separator = "") { it.text }

fun List<TextRun>.isBlank(): Boolean = all { it.text.isBlank() }

/**
 * How a book says one of its own paragraphs is set.
 *
 * The author's choice rather than the reader's, and about one paragraph rather
 * than the book: a dedication, an epigraph, a line of verse, a plate's caption.
 * The reader's own setting - see `TextAlignment` - decides everything a book
 * says nothing about, which is nearly all of it.
 *
 * Named after the markup rather than the writing direction, because that is what
 * it means: a book asking for `right` is asking for the right.
 */
enum class BlockAlignment {
    Left,
    Center,
    Right,
    Justify,
}

/**
 * One thing on the page of a reflowable book.
 *
 * A deliberately short list: everything a book needs to read correctly, and
 * nothing merely because a format can express it. EPUBs carry arbitrary HTML and
 * stylesheets, and honouring that is building a browser. What is guaranteed
 * instead is the words in the right order and structure, set the way the person
 * reading them asked for.
 */
sealed interface TextBlock {

    /**
     * How the book asked for this block to be set, or null where it said
     * nothing - which is when the reader's own setting decides. Only the blocks
     * made of prose can carry one: a rule and a plate have no lines to align,
     * and preformatted text is the one thing whose spacing is content.
     */
    val alignment: BlockAlignment? get() = null

    data class Heading(
        val text: List<TextRun>,
        val level: Int,
        override val alignment: BlockAlignment? = null,
    ) : TextBlock

    data class Paragraph(
        val text: List<TextRun>,
        override val alignment: BlockAlignment? = null,
    ) : TextBlock

    /**
     * [marker] is the bullet or number as it should be shown, decided by whoever
     * parsed the list - the reader draws what it is given rather than counting,
     * since a list that starts at 4 is a thing books do.
     */
    data class ListItem(
        val text: List<TextRun>,
        val marker: String,
        override val alignment: BlockAlignment? = null,
    ) : TextBlock

    data class Quote(
        val text: List<TextRun>,
        override val alignment: BlockAlignment? = null,
    ) : TextBlock

    /** Preformatted: whitespace and line breaks are content, not layout. */
    data class Code(val text: String) : TextBlock

    /**
     * An illustration, held as the bytes the book stores rather than a decoded
     * image: a book can carry hundreds, and decoding them all at open would cost
     * more memory than the text does, so this stays encoded until the reader
     * scrolls to it.
     *
     * Identity equality, inherited from [Any] - comparing megabytes of image
     * data would be work done on every recomposition.
     */
    class Figure(val bytes: ByteArray, val description: String?) : TextBlock

    /** A scene break, drawn as a rule rather than as the characters a book used. */
    data object Separator : TextBlock
}

/**
 * One division of a reflowable book: a chapter, or a slice of one long unbroken
 * document.
 *
 * What the book states about itself, not what the reader shows. Pages are cut
 * from this run of blocks to fit the window, so a section may be one page, or
 * twenty, or share one with the section after it. Nothing outside the layout
 * addresses a book by section - the scrubber, the remembered position and the
 * table of contents all count pages.
 *
 * A section is still where a chapter begins, which is why pages are never cut
 * across one that names itself - a chapter opens a page of its own.
 *
 * [title] is what the section is called where it names itself, which is what
 * lets a book with no table of contents of its own still offer one.
 */
data class TextSection(
    val title: String?,
    val blocks: List<TextBlock>,
)
