package io.lukia.core.infrastructure.importing

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.info
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.importing.cover.extractCover
import io.lukia.core.infrastructure.importing.metadata.extractMetadata

/**
 * Everything read out of a document's own bytes at import time.
 *
 * The cover and the metadata are pulled from one read of the file rather than
 * two. Each platform's analyser reads those bytes differently - a path, a
 * content URI, an `NSURL` - but what happens to them afterwards is the same
 * everywhere, which is what this is.
 */
class DocumentContents(
    val coverImage: CoverImage?,
    val metadata: BookMetadata?,
) {
    companion object {
        val Empty = DocumentContents(coverImage = null, metadata = null)
    }
}

/**
 * Reads what [documentBytes] has to offer, for a document of [type].
 *
 * Neither extraction can fail the import: both come back null for a file that
 * carries nothing, is not really the format its extension claims, or is damaged.
 * A book with no cover and no metadata is still a book, and refusing it would
 * help nobody.
 */
fun extractContents(
    type: DocumentType,
    document: DocumentSource,
    diagnostics: Diagnostics = Diagnostics.None,
): DocumentContents {
    val contents = DocumentContents(
        coverImage = extractCover(type, document, diagnostics)?.let(::CoverImage),
        metadata = extractMetadata(type, document, diagnostics),
    )

    // Recorded here rather than beside either extraction, because a file that
    // cannot be read mostly does not throw on the way - it comes back with
    // nothing, which is indistinguishable at each extraction from a book that
    // genuinely has no cover. What is worth writing down is the pair: a format
    // with somewhere to keep both that produced neither is the grey placeholder
    // and the file-name-for-a-title the reader is writing in to complain about.
    //
    // Only for the formats with a container. Plain text and Markdown have
    // nowhere to keep either, so "none" is not news about them.
    if (type.holdsMoreThanWords && contents.isIncomplete) {
        diagnostics.info(
            area = DiagnosticArea.Import,
            event = "import-read-partial",
            detail = "${type.name}, cover ${contents.coverImage.presence}, " +
                "metadata ${contents.metadata.presence}",
        )
    }

    return contents
}

private val DocumentContents.isIncomplete: Boolean
    get() = coverImage == null || metadata == null

private val Any?.presence: String
    get() = if (this == null) "none" else "read"

private val DocumentType.holdsMoreThanWords: Boolean
    get() = when (this) {
        DocumentType.PDF, DocumentType.EPUB, DocumentType.DOCX -> true
        DocumentType.TEXT, DocumentType.MARKDOWN, DocumentType.UNKNOWN -> false
    }
