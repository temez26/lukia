package io.lukia.features.import.infrastructure.cover

/**
 * Renders the first page of a PDF document as PNG image bytes, or returns
 * null if the PDF cannot be rendered (corrupt file, empty document, etc.).
 */
internal expect fun renderPdfCover(pdfBytes: ByteArray): ByteArray?
