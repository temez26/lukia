package io.lukia.core.domain.reading

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
 * How each book was last read.
 *
 * Keyed by the same location the library keys on, exactly as a reading position
 * is, and with the same lifetime: it belongs to a book somebody has opened, and
 * it goes when the book does. Beside [ReadingProgress] rather than in the reader
 * for that reason - the reader is what writes it, but the library is what has to
 * forget it, and the settings screen is what chooses the default.
 */
interface PdfLayoutRepository {

    /**
     * Null for a book nobody has chosen for, so that the caller can tell an
     * absent choice from a deliberate one and fall back to whatever new books
     * are set to open as.
     */
    suspend fun layoutFor(location: String): PdfLayout?

    suspend fun save(location: String, layout: PdfLayout)

    /** For a book leaving the library, whose choice nothing can reach any more. */
    suspend fun forget(location: String)
}
