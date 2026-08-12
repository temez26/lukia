package io.lukia.features.import.infrastructure.cover

import io.lukia.features.import.domain.DocumentType

/**
 * Best-effort cover extraction: returns null (rather than throwing) whenever
 * a cover can't be produced, so a missing/unparsable cover never fails an
 * otherwise-successful import.
 */
internal fun extractCover(type: DocumentType, documentBytes: ByteArray): ByteArray? {
    return when (type) {
        DocumentType.PDF -> runCatching { renderPdfCover(documentBytes) }.getOrNull()
        DocumentType.EPUB -> runCatching { EpubCoverExtractor.extract(documentBytes) }.getOrNull()
        DocumentType.UNKNOWN -> null
    }
}
