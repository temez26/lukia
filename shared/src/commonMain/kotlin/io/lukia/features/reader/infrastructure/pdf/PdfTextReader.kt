package io.lukia.features.reader.infrastructure.pdf

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.warn
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.page.DocumentPages
import io.lukia.features.reader.domain.page.PageContent
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextExtraction
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.plainText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A PDF read as text the reader can re-set, rather than as pages it draws.
 *
 * This buys back everything a fixed layout costs: type at the size the reader
 * chose, lines that break where the window ends, and - the reason most people
 * ask - a translation set in its own paragraph rather than floating over the
 * page in a panel. A PDF converted here reaches the reader as exactly what an
 * EPUB does, so everything above treats the two the same.
 *
 * The page's own layout is gone: columns, tables and the placing of a footnote
 * were decisions about a page, and there is no page any more. What comes back is
 * the words in reading order, as paragraphs, headings and list items, with the
 * pictures where they were printed. A reader who wants the page as its maker set
 * it turns this off, which is why it is a setting and not a conversion.
 *
 * Null when the document has nothing to re-set - a scan, or pages that are each
 * one photograph. A book of pictures of words is not a book of words, and the
 * caller shows the pages instead.
 */
internal suspend fun readPdfAsText(
    pages: DocumentPages,
    /**
     * Further handles on the same file, opened so that the book can be read
     * through more than one of them at once. Empty reads it through [pages]
     * alone, which is what every caller that is not converting a whole book
     * does.
     */
    alongside: List<PageContent> = emptyList(),
    diagnostics: Diagnostics = Diagnostics.None,
    onProgress: (TextExtraction) -> Unit = {},
): ReflowableContent? {
    val pageCount = pages.pageCount

    // Counted and recorded once for the book rather than once per page. A file
    // that fails on one page usually fails on all of them, and an entry each
    // would push everything else out of the record while saying no more than
    // this does - a reader reporting blank pages needs to know how many.
    var unreadable = 0

    val read = readPages(
        readers = listOf(pages) + alongside,
        pageSizes = pages.pageSizes,
        pageCount = pageCount,
        onProgress = onProgress,
        onUnreadable = { unreadable++ },
    )

    if (unreadable > 0) {
        diagnostics.warn(
            area = DiagnosticArea.Reading,
            event = "pdf-pages-unreadable",
            detail = "$unreadable of $pageCount",
        )
    }

    val figures = PdfFigures()

    // In page order, and the only part of this that has to be: what a picture
    // is, and how many pages carry it, is a fact about the book rather than
    // about a page - see [PdfFigures.onPage].
    val sections = read.map { figures.onPage(it).toMutableList() }

    for (page in 1 until sections.size) sections.joinAcrossPageBreak(page)

    // Nothing but pictures is nothing to re-set: that is a scan, and the caller
    // shows its pages rather than a list of photographs of them.
    if (sections.none { blocks -> blocks.any { it !is TextBlock.Figure } }) return null

    sections.forEach { blocks ->
        blocks.removeAll { it is TextBlock.Figure && figures.isPageFurniture(it, pageCount) }
    }

    return ReflowableContent(
        // One section per page of the file, unnamed. Unnamed because a section
        // that names itself opens a page of its own, and a page break every
        // sheet of the original is the fixed layout coming back in through the
        // window it was just shown out of. One per page because that is what
        // makes the book's own table of contents still work: its entries point
        // at pages, the reader resolves them as sections, and the two only line
        // up while the count and the order match.
        sections = sections.map { TextSection(title = null, blocks = it.toList()) },
        outline = pages.outline,
    )
}

/**
 * Every page of the file read back as blocks, in order.
 *
 * The readers take a run of pages each, so a conversion finishes in about as
 * long as the slowest run rather than all of them. Runs rather than every nth
 * page because each handle keeps what it parses: a handle reading a quarter of
 * the book holds a quarter of its structure, where four handles reading every
 * fourth page would each end up holding the lot.
 *
 * Nothing here needs the book in order - inferring one page's blocks is a
 * question about that page - which is what makes it divisible. The caller does
 * need the order, and gets it: the runs are laid end to end.
 */
private suspend fun readPages(
    readers: List<PageContent>,
    pageSizes: List<PageSize>,
    pageCount: Int,
    onProgress: (TextExtraction) -> Unit,
    onUnreadable: () -> Unit,
): List<List<TextBlock>> = coroutineScope {
    val counted = Mutex()
    var read = 0

    val run = (pageCount + readers.size - 1) / readers.size

    readers.mapIndexed { index, reader ->
        val from = (index * run).coerceAtMost(pageCount)
        val until = (from + run).coerceAtMost(pageCount)

        async {
            (from until until).map { page ->
                // Both of these are held under the same lock, and for the same
                // reason: several readers are somewhere in the book at once and
                // no one of them knows what the rest have reached or dropped.
                reader.blocksOn(
                    index = page,
                    pageSizes = pageSizes,
                    onUnreadable = { counted.withLock(action = onUnreadable) },
                ).also {
                    counted.withLock { onProgress(TextExtraction(++read, pageCount)) }
                }
            }
        }
    }.awaitAll().flatten()
}

/** One page read back as the things written and drawn on it. */
private suspend fun PageContent.blocksOn(
    index: Int,
    pageSizes: List<PageSize>,
    onUnreadable: suspend () -> Unit,
): List<TextBlock> {
    // A page that cannot be read costs the reader that page rather than the
    // book, exactly as a page that cannot be drawn does.
    val glyphs = runCatchingApp { pageText(index) }.getOrNull()
    val drawn = runCatchingApp { pageImages(index) }.getOrNull()

    if (glyphs == null || drawn == null) onUnreadable()

    val size = pageSizes.getOrElse(index) { PageSize(width = 0f, height = 0f) }

    return pdfPageBlocks(glyphs.orEmpty(), drawn.orEmpty(), size)
}

/**
 * Joins a paragraph that ran over the foot of one page onto the head of the
 * next.
 *
 * A page break falls wherever the paper ended, very often mid-sentence. Left
 * alone each half arrives as its own paragraph, so the re-set book is full of
 * paragraphs stopping mid-thought and others opening on a lower-case word - the
 * tell-tale of a badly converted book.
 *
 * The tail is joined onto the page it started on rather than the page it
 * finished on, so that every page of the file keeps its section - see the note
 * on the sections above. A page whose only paragraph was a continuation is left
 * empty, which is a section with nothing in it and costs the reader nothing.
 */
private fun List<MutableList<TextBlock>>.joinAcrossPageBreak(page: Int) {
    val previous = this[page - 1]
    val current = this[page]

    val tail = previous.lastOrNull() as? TextBlock.Paragraph ?: return
    val head = current.firstOrNull() as? TextBlock.Paragraph ?: return

    val before = tail.text.plainText()
    val after = head.text.plainText()

    if (!continuesAcrossPages(before, after)) return

    // A word broken across the page break is one word, joined without a space -
    // the same rule a line break inside a page follows.
    val joined = if (before.endsWithHyphen()) before + after else "$before $after"

    previous[previous.lastIndex] = TextBlock.Paragraph(text = listOf(TextRun(joined)))
    current.removeAt(0)
}

/** What a paragraph ends with when it has actually ended. */
private const val SENTENCE_ENDINGS = ".!?…:;\"”»)]"

/**
 * Whether these two paragraphs are one paragraph the page break came between.
 *
 * Both signals are needed: the first must look unfinished and the second like a
 * continuation. Either alone is innocently common - a heading carries no full
 * stop, and a page can open on a lower-case word that begins a genuine new
 * paragraph - and joining two paragraphs that were never one is a mistake the
 * reader cannot undo from the settings panel.
 */
private fun continuesAcrossPages(before: String, after: String): Boolean {
    if (before.isEmpty() || after.isEmpty()) return false
    if (before.endsWithHyphen()) return true

    return before.last() !in SENTENCE_ENDINGS && after.first().isLowerCase()
}

private fun String.endsWithHyphen(): Boolean {
    val last = lastOrNull() ?: return false

    return last == '-' || last == '‐' || last == '­'
}
