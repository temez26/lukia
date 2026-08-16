package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.DocumentType
import io.lukia.core.infrastructure.readFileBytes
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderContent
import io.lukia.features.reader.infrastructure.text.readDocx
import io.lukia.features.reader.infrastructure.text.readEpub
import io.lukia.features.reader.infrastructure.text.readMarkdown
import io.lukia.features.reader.infrastructure.text.readPlainText

/**
 * Opens the document at [path] as something the reader can show, or returns null
 * when this app cannot read that format.
 *
 * The one place that decides which formats are readable, so adding another is a
 * branch here and a parser beside it rather than a change spread through the
 * reader. A format being importable and a format being readable are separate
 * questions - the library will hold a file this returns null for, and say so.
 *
 * [path] is always a working copy in app storage, never the file the user
 * imported.
 */
internal fun openReaderContent(type: DocumentType, path: String): ReaderContent? = when (type) {
    // Kept apart from the rest: a PDF is opened by the platform's own renderer
    // against the file on disk, and is the only format here never held whole in
    // memory.
    DocumentType.PDF -> PagedContent(openPdfPages(path))

    DocumentType.EPUB -> readEpub(readFileBytes(path))
    DocumentType.DOCX -> readDocx(readFileBytes(path))
    DocumentType.TEXT -> readPlainText(readFileBytes(path))
    DocumentType.MARKDOWN -> readMarkdown(readFileBytes(path))

    DocumentType.UNKNOWN -> null
}
