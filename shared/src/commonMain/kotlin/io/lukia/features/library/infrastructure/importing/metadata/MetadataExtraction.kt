package io.lukia.features.library.infrastructure.importing.metadata

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.warn
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.ByteSource
import io.lukia.core.infrastructure.epub.EpubPackage

/**
 * Best-effort metadata extraction, mirroring how covers are pulled out of the
 * same bytes at the same moment: null rather than a throw whenever a document
 * cannot be read, so a book with unreadable metadata still imports.
 *
 * Plain text and Markdown have nowhere to keep any of this - no container, only
 * the words - so they have no branch rather than one that always fails.
 *
 * Null shows the reader a file name where a title should be, which is the whole
 * of the evidence unless [diagnostics] is given somewhere to write.
 */
internal fun extractMetadata(
    type: DocumentType,
    document: ByteSource,
    diagnostics: Diagnostics = Diagnostics.None,
): BookMetadata? {
    val read = when (type) {
        DocumentType.EPUB -> runCatching { extractEpubMetadata(document) }
        DocumentType.DOCX -> runCatching { extractDocxMetadata(document) }
        DocumentType.PDF -> runCatching { extractPdfMetadata(document) }
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

private fun extractEpubMetadata(epub: ByteSource): BookMetadata? =
    EpubPackage.open(epub)?.metadata()
