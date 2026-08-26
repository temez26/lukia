package io.lukia.features.import.infrastructure.cover

import io.lukia.core.domain.DocumentType
import io.lukia.core.infrastructure.shrinkCoverImage

/**
 * How large a stored cover may be along its longer edge.
 *
 * The same 800 pixels the decoded cover cache holds artwork at, because that is
 * the largest a cover is ever drawn - the detail screen, rather than the grid.
 * Storing more than is ever drawn buys nothing and costs it in every row of the
 * library.
 */
internal const val MaxStoredCoverEdge = 800

/**
 * The most a cover may weigh, whatever it is a picture of.
 *
 * Android reads query results through a CursorWindow of two megabytes, and a row
 * too large for one does not come back short - the read of the whole table
 * throws, which takes the entire library off the screen and leaves a retry
 * button in its place. One book's cover art is never worth that, so anything
 * still this large after shrinking is stored as no cover at all.
 *
 * Well under the two megabytes so that the rest of the row - a description a
 * publisher wrote at length, a list of subjects - has room beside it. Shrinking
 * puts an ordinary cover an order of magnitude below this; what this catches is
 * an image no platform decoder recognised and so could not be shrunk.
 */
internal const val MaxStoredCoverBytes = 1_000_000

/**
 * Best-effort cover extraction: returns null (rather than throwing) whenever
 * a cover can't be produced, so a missing/unparsable cover never fails an
 * otherwise-successful import.
 *
 * Plain text and Markdown have nowhere to keep a cover - there is no container,
 * only the words - so they have no branch here rather than a branch that always
 * fails.
 */
internal fun extractCover(type: DocumentType, documentBytes: ByteArray): ByteArray? = when (type) {
    DocumentType.PDF -> runCatching { renderPdfCover(documentBytes) }.getOrNull()
    DocumentType.EPUB -> runCatching { extractEpubCover(documentBytes) }.getOrNull()
    DocumentType.DOCX -> runCatching { extractDocxCover(documentBytes) }.getOrNull()
    DocumentType.TEXT, DocumentType.MARKDOWN, DocumentType.UNKNOWN -> null
}?.let(::asStoredCover)

/**
 * A cover as the library keeps it: shrunk to [MaxStoredCoverEdge], and given up
 * on entirely when even that leaves it over [MaxStoredCoverBytes].
 *
 * A publisher's artwork is routinely 1600x2400, and a PDF page rendered at its
 * own size larger again - several megabytes of PNG for something drawn at a
 * couple of hundred pixels. Shrinking here rather than at the point it is drawn
 * is what keeps that out of the database, out of every read of the library, and
 * out of the row-size limit above.
 */
private fun asStoredCover(cover: ByteArray): ByteArray? {
    val stored = runCatching { shrinkCoverImage(cover, MaxStoredCoverEdge) }.getOrNull() ?: cover

    return stored.takeIf { it.size <= MaxStoredCoverBytes }
}
