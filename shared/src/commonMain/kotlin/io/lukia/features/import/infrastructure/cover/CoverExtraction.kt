package io.lukia.features.import.infrastructure.cover

import io.lukia.core.domain.DocumentType

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
}
