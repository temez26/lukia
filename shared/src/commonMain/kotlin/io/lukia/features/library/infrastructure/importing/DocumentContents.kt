package io.lukia.features.library.infrastructure.importing

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentType
import io.lukia.features.library.infrastructure.importing.cover.extractCover
import io.lukia.features.library.infrastructure.importing.metadata.extractMetadata

/**
 * Everything read out of a document's own bytes at import time.
 *
 * The cover and the metadata are pulled from one read of the file rather than
 * two. Each platform's analyser reads those bytes differently - a path, a
 * content URI, an `NSURL` - but what happens to them afterwards is the same
 * everywhere, which is what this is.
 */
internal class DocumentContents(
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
internal fun extractContents(type: DocumentType, documentBytes: ByteArray): DocumentContents =
    DocumentContents(
        coverImage = extractCover(type, documentBytes)?.let(::CoverImage),
        metadata = extractMetadata(type, documentBytes),
    )
