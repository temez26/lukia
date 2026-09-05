package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.availableProcessors
import io.lukia.core.infrastructure.readFileBytes
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderContent
import io.lukia.features.reader.domain.page.PageContent
import io.lukia.features.reader.domain.text.TextExtraction
import io.lukia.features.reader.infrastructure.pdf.readPdfAsText
import io.lukia.features.reader.infrastructure.text.epub.readEpub
import io.lukia.features.reader.infrastructure.text.readDocx
import io.lukia.features.reader.infrastructure.text.readMarkdown
import io.lukia.features.reader.infrastructure.text.readPlainText
import io.lukia.features.reader.infrastructure.text.reflow.storeReflow
import io.lukia.features.reader.infrastructure.text.reflow.storedReflow

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
internal suspend fun openReaderContent(
    type: DocumentType,
    path: String,
    diagnostics: Diagnostics = Diagnostics.None,
): ReaderContent? = when (type) {
    // Kept apart from the rest: a PDF is opened by the platform's own renderer
    // against the file on disk, and is the only format here never held whole in
    // memory. A file the renderer will not have is null exactly as an unreadable
    // EPUB is, rather than the exception PDF alone used to raise.
    DocumentType.PDF -> openPdfPages(path)?.let(::PagedContent)

    DocumentType.EPUB -> readEpub(readFileBytes(path))
    DocumentType.DOCX -> readDocx(readFileBytes(path))
    DocumentType.TEXT -> readPlainText(readFileBytes(path), diagnostics)
    DocumentType.MARKDOWN -> readMarkdown(readFileBytes(path), diagnostics)

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
 *
 * Hundreds of pages is also why the file is opened more than once - see
 * [readersFor]. Reading a page is the longest thing the app does and one
 * handle can only do it one page at a time, so the book is divided between as
 * many handles as the device has cores to read them with.
 *
 * Read once in a stronger sense as well: what comes out is kept beside the file
 * - see [storedReflow] - so that a reader who looks at the same book as pages
 * and then as text again is not made to wait through the whole conversion a
 * second time.
 *
 * A file with no text in it is not remembered as such. It costs a scan of the
 * file to find out again, but only if somebody asks for the same scan as text
 * twice, and writing down an absence is a thing to get wrong for the sake of a
 * case the reader has already been told about.
 */
internal suspend fun openPdfAsText(
    path: String,
    diagnostics: Diagnostics = Diagnostics.None,
    onProgress: (TextExtraction) -> Unit = {},
): ReaderContent? {
    storedReflow(path)?.let { return it }

    // A file the renderer will not have is not one to read the words out of
    // either. Null here is "not as text", and the caller then tries it as pages
    // and gets the same answer - which is the honest one, and the same one
    // every other format gives.
    val pages = openPdfPages(path) ?: return null
    val alongside = readersAlongside(path, readersFor(pages.pageCount) - 1)

    val content = try {
        readPdfAsText(pages, alongside, diagnostics, onProgress)
    } finally {
        alongside.forEach { runCatching { it.close() } }
        pages.close()
    }

    return content?.also { storeReflow(path, it, diagnostics) }
}

/**
 * How many handles on the file a conversion reads it through.
 *
 * One per core, up to a point. The point is [MAX_READERS], because the reading
 * is as much file and memory as it is arithmetic and a machine stops going
 * faster well before it runs out of cores - and because each handle parses and
 * holds its own share of the book, which is memory a phone has less of than it
 * has patience.
 *
 * One handle for a short document, whatever the machine. Opening a file four
 * times to read twelve pages costs more than reading them does, and a
 * conversion nobody waits for is not one worth dividing.
 */
private fun readersFor(pageCount: Int): Int {
    if (pageCount < MIN_PAGES_TO_DIVIDE) return 1

    return minOf(
        MAX_READERS,
        availableProcessors().coerceAtLeast(1),
        pageCount / MIN_PAGES_EACH,
    ).coerceAtLeast(1)
}

/**
 * [count] more ways into the file, or as many as could be opened.
 *
 * A handle that cannot be opened costs the conversion some of its speed and
 * nothing else, so it is not worth failing an open over: the book is divided
 * between whatever came back, and one handle is the way this always worked.
 */
private fun readersAlongside(path: String, count: Int): List<PageContent> = buildList {
    repeat(count) {
        val reader = runCatching { openPdfPageContent(path) }.getOrNull() ?: return@buildList

        add(reader)
    }
}

/** Below this a conversion is over before a second handle has finished opening. */
private const val MIN_PAGES_TO_DIVIDE = 24

/** And below this a handle would spend more time being opened than reading. */
private const val MIN_PAGES_EACH = 12

/** Past this the file, not the processor, is what the conversion is waiting on. */
private const val MAX_READERS = 4
