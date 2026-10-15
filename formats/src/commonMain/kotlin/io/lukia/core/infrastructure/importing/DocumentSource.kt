package io.lukia.core.infrastructure.importing

import io.lukia.core.infrastructure.ByteSource

/**
 * A document being imported, open for reading.
 *
 * The import wants a cover and a title out of a file, which together are a few
 * kilobytes of it - and every format here is built so that they can be found
 * without reading the rest. An EPUB and a DOCX keep a directory at the end
 * naming where each part lives; a PDF keeps a table of its objects. So this is
 * random access rather than a loaded [ByteArray]: the analyzer holds a handle on
 * the file and the readers take windows of it.
 *
 * It was bytes, and the bytes were the whole book. A hundred-megabyte PDF cost a
 * hundred megabytes to find out what it was called, which is a size Android's
 * heap does not have and iOS ends processes over - and the file people most want
 * on their shelf is exactly the scanned one that large.
 *
 * Holds an open handle on the file and is [AutoCloseable] for that reason, in
 * the manner of the reader's own `PageContent`.
 */
interface DocumentSource : ByteSource, AutoCloseable {

    /**
     * The first page drawn as an image, or null where it cannot be drawn.
     *
     * Only ever asked of a PDF, which is the one format here whose cover has to
     * be made rather than found: an EPUB and a DOCX each store a picture that
     * can simply be read out, and the rest have no cover at all.
     *
     * It lives on the source rather than in common code because each platform
     * draws a PDF with the renderer it already ships - PdfRenderer, PDFBox,
     * PDFKit - and every one of those wants the file itself rather than a window
     * onto it. Asking the thing that already holds the file is what lets all
     * three stop making a copy in memory first.
     */
    fun renderFirstPage(): ByteArray?
}
