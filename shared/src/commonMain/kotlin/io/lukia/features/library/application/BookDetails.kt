package io.lukia.features.library.application

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.ReadingProgress

/**
 * Everything the detail screen shows about one book, from the two places it is
 * kept: the library entry - the file, its cover, what the document says about
 * itself - and how far through it the reader got.
 *
 * The two are separate stores on purpose (see `ReadingPosition.sq`), and joining
 * them is this screen's business rather than either store's.
 */
data class BookDetails(
    val document: DocumentInfo,
    /** Null when the book has never been opened, which is not the same as page one. */
    val progress: ReadingProgress? = null,
) {
    /**
     * How far in, as a percentage, or null for a book never opened. See
     * [ReadingProgress.percent], which is where the arithmetic lives so that
     * this screen and the bars on the library's cards cannot disagree.
     */
    val percentRead: Int?
        get() = progress?.percent

    /** True once the reader has reached the last page. */
    val isFinished: Boolean
        get() = progress?.isFinished == true

    /** True for a book that has been opened but not finished. */
    val isInProgress: Boolean
        get() = percentRead != null && !isFinished
}
