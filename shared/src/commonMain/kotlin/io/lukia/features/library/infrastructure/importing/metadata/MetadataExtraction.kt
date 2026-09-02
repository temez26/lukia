package io.lukia.features.library.infrastructure.importing.metadata

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.warn
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.epub.EpubPackage

/**
 * Best-effort metadata extraction, mirroring how covers are pulled out of the
 * same bytes at the same moment: null rather than a throw whenever a document
 * cannot be read, so a book with unreadable metadata still imports.
 *
 * Plain text and Markdown have nowhere to keep any of this - there is no
 * container, only the words - so they have no branch here rather than a branch
 * that always fails.
 *
 * What the reader sees when this returns null is a file name where a title
 * should be, which is the whole of the evidence unless [diagnostics] is given
 * something to write to.
 */
internal fun extractMetadata(
    type: DocumentType,
    documentBytes: ByteArray,
    diagnostics: Diagnostics = Diagnostics.None,
): BookMetadata? {
    val read = when (type) {
        DocumentType.EPUB -> runCatching { extractEpubMetadata(documentBytes) }
        DocumentType.DOCX -> runCatching { extractDocxMetadata(documentBytes) }
        DocumentType.PDF -> runCatching { extractPdfMetadata(documentBytes) }
        DocumentType.TEXT, DocumentType.MARKDOWN, DocumentType.UNKNOWN -> return null
    }

    val metadata = read
        .onFailure {
            // The format and the throwable's type, and nothing out of the file:
            // what is in there is a title and an author, which is a record of
            // what somebody reads.
            diagnostics.warn(
                area = DiagnosticArea.Import,
                event = "metadata-extraction-failed",
                detail = type.name,
                cause = it,
            )
        }
        .getOrNull()

    // An empty result and no result are the same thing to everything above
    // this, and collapsing them here saves every caller the check.
    return metadata?.takeIf { !it.isEmpty }
}

private fun extractEpubMetadata(epubBytes: ByteArray): BookMetadata? =
    EpubPackage.open(epubBytes)?.metadata()
