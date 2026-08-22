package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.DocumentType
import io.lukia.core.infrastructure.readFileBytes
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderContent
import io.lukia.features.reader.domain.TextExtraction
import io.lukia.features.reader.infrastructure.text.readDocx
import io.lukia.features.reader.infrastructure.text.readEpub
import io.lukia.features.reader.infrastructure.text.readMarkdown
import io.lukia.features.reader.infrastructure.text.readPdfAsText
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

/**
 * Opens the PDF at [path] as text the reader sets itself, or returns null when
 * the file has no text layer to set - a scan, or pages that are each one
 * photograph.
 *
 * The file is read once and given back: what comes out is the whole book's text,
 * held in memory like any other reflowable book, and nothing is drawn from the
 * file afterwards. That is also why this cannot be part of [openReaderContent],
 * which is not suspending: reading a page's text is, and there are hundreds of
 * pages here rather than one.
 */
internal suspend fun openPdfAsText(
    path: String,
    onProgress: (TextExtraction) -> Unit = {},
): ReaderContent? {
    val pages = openPdfPages(path)

    return try {
        readPdfAsText(pages, onProgress)
    } finally {
        pages.close()
    }
}
