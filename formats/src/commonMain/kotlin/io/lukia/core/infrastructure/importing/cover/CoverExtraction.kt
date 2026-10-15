package io.lukia.core.infrastructure.importing.cover

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.warn
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.importing.DocumentSource
import io.lukia.core.infrastructure.shrinkCoverImage

/**
 * How large a stored cover may be along its longer edge.
 *
 * The same 800 pixels the decoded cover cache holds artwork at, because that is
 * the largest a cover is ever drawn - the detail screen, rather than the grid.
 * Storing more than is ever drawn buys nothing and costs it in every row of the
 * library.
 */
const val MaxStoredCoverEdge = 800

/**
 * The most a cover may weigh, whatever it is a picture of.
 *
 * Android reads query results through a two-megabyte CursorWindow, and a row too
 * large does not come back short - the read of the whole table throws, taking
 * the entire library off screen and leaving a retry button. One book's cover is
 * never worth that, so anything still this large after shrinking is stored as no
 * cover at all.
 *
 * Well under two megabytes so the rest of the row - a long publisher description,
 * a list of subjects - has room beside it. Shrinking puts an ordinary cover an
 * order of magnitude below this; what this catches is an image no platform
 * decoder recognised and so could not shrink.
 */
const val MaxStoredCoverBytes = 1_000_000

/**
 * Best-effort cover extraction: returns null (rather than throwing) whenever
 * a cover can't be produced, so a missing/unparsable cover never fails an
 * otherwise-successful import.
 *
 * Plain text and Markdown have nowhere to keep a cover - there is no container,
 * only the words - so they have no branch here rather than a branch that always
 * fails.
 */
fun extractCover(
    type: DocumentType,
    document: DocumentSource,
    diagnostics: Diagnostics = Diagnostics.None,
): ByteArray? {
    val rendered = when (type) {
        // Drawn rather than read: a PDF stores no cover, only a first page - see
        // [DocumentSource.renderFirstPage].
        DocumentType.PDF -> runCatching { document.renderFirstPage() }
        DocumentType.EPUB -> runCatching { extractEpubCover(document) }
        DocumentType.DOCX -> runCatching { extractDocxCover(document) }
        DocumentType.TEXT, DocumentType.MARKDOWN, DocumentType.UNKNOWN -> return null
    }

    return rendered
        .onFailure {
            // The format and the throwable's type. A cover is the one part of a
            // book that is a picture of its title, so nothing out of it travels.
            diagnostics.warn(
                area = DiagnosticArea.Import,
                event = "cover-extraction-failed",
                detail = type.name,
                cause = it,
            )
        }
        .getOrNull()
        ?.let { asStoredCover(it, diagnostics) }
}

/**
 * A cover as the library keeps it: shrunk to [MaxStoredCoverEdge], and given up
 * on entirely when even that leaves it over [MaxStoredCoverBytes].
 *
 * A publisher's artwork is routinely 1600x2400 and a rendered PDF page larger
 * again - megabytes of PNG for something drawn at a couple of hundred pixels.
 * Shrinking here rather than where it is drawn keeps that out of the database,
 * out of every read of the library, and under the row-size limit above.
 */
private fun asStoredCover(cover: ByteArray, diagnostics: Diagnostics): ByteArray? {
    val stored = runCatching { shrinkCoverImage(cover, MaxStoredCoverEdge) }
        .onFailure {
            // A size in kilobytes says which of the two ways this ends: an image
            // no decoder recognised, or artwork large enough that failing to
            // shrink it means storing no cover at all.
            diagnostics.warn(
                area = DiagnosticArea.Import,
                event = "cover-shrink-failed",
                detail = "${cover.size / BytesPerKilobyte} KB",
                cause = it,
            )
        }
        .getOrNull() ?: cover

    return stored.takeIf { it.size <= MaxStoredCoverBytes }
}

private const val BytesPerKilobyte = 1024
