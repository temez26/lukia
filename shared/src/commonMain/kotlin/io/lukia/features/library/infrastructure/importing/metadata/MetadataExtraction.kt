package io.lukia.features.library.infrastructure.importing.metadata

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
 */
internal fun extractMetadata(type: DocumentType, documentBytes: ByteArray): BookMetadata? {
    val metadata = when (type) {
        DocumentType.EPUB -> runCatching { extractEpubMetadata(documentBytes) }.getOrNull()
        DocumentType.DOCX -> runCatching { extractDocxMetadata(documentBytes) }.getOrNull()
        DocumentType.PDF -> runCatching { extractPdfMetadata(documentBytes) }.getOrNull()
        DocumentType.TEXT, DocumentType.MARKDOWN, DocumentType.UNKNOWN -> null
    }

    // An empty result and no result are the same thing to everything above
    // this, and collapsing them here saves every caller the check.
    return metadata?.takeIf { !it.isEmpty }
}

private fun extractEpubMetadata(epubBytes: ByteArray): BookMetadata? =
    EpubPackage.open(epubBytes)?.metadata()
