package io.lukia.features.reader.domain

/**
 * Which of the two ways of reading a PDF somebody has asked for.
 *
 * Only a PDF has the choice. Every other format the app reads is text already,
 * and a PDF is the only one that carries a layout worth keeping - so this is not
 * a setting on the reader as a whole but a fact about one book, which is also
 * why it is remembered per book: a scanned facsimile and a paper submitted to a
 * journal are read in different ways by the same person on the same evening.
 */
enum class PdfLayout {

    /** The pages as the file's maker set them, drawn by the platform. */
    Original,

    /**
     * The words re-set by the reader: the type at the size that was asked for,
     * lines that break where the window does, and translations in the paragraph
     * rather than over the page.
     */
    Text,
    ;

    val asText: Boolean get() = this == Text
}

/**
 * How far through reading a PDF's text the app has got.
 *
 * Worth reporting because it is the one thing in the reader that takes long
 * enough to watch: the text of every page has to be read out of the file before
 * the first of them can be laid out, and a long book is thousands of pages of
 * that. A spinner alone would leave somebody wondering whether a six-hundred
 * page book had hung.
 */
data class TextExtraction(val pagesRead: Int, val pageCount: Int)

/**
 * How each book was last read.
 *
 * Keyed by the same location the library keys on, exactly as a reading position
 * is, and with the same lifetime: it belongs to a book somebody has opened, and
 * it goes when the book does.
 */
interface PdfLayoutRepository {

    /** [PdfLayout.Original] for a book nobody has chosen for, which is the default. */
    suspend fun layoutFor(location: String): PdfLayout

    suspend fun save(location: String, layout: PdfLayout)

    /** For a book leaving the library, whose choice nothing can reach any more. */
    suspend fun forget(location: String)
}
