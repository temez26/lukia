package io.lukia.features.reader.infrastructure.pdf

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
 * What this is for is everything a fixed layout costs: type at the size the
 * reader chose rather than the size the file was made at, lines that break where
 * the window ends, and - the reason most people ask for it - a translation set
 * in the paragraph it belongs to instead of floating over the page in a panel.
 * A PDF converted here reaches the reader as exactly what an EPUB reaches it as,
 * so everything above this point treats the two the same and none of it needed
 * changing.
 *
 * The page's own layout is gone: columns, tables and the placing of a footnote
 * were all decisions about a page, and there is no page any more. What comes
 * back is the words in reading order, as paragraphs, headings and list items,
 * with the pictures among them where they were printed. A reader who wants the
 * page as its maker set it turns this off, which is why it is a setting and not
 * a conversion.
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
    onProgress: (TextExtraction) -> Unit = {},
): ReflowableContent? {
    val pageCount = pages.pageCount

    val read = readPages(
        readers = listOf(pages) + alongside,
        pageSizes = pages.pageSizes,
        pageCount = pageCount,
        onProgress = onProgress,
    )

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
 * The readers divide the book between them and take a run of pages each, so a
 * conversion finishes in about as long as the slowest run rather than as long
 * as all of them. Runs rather than every nth page because each handle parses
 * what it touches and keeps it: a handle that reads a quarter of the book holds
 * a quarter of the book's structure, where four handles reading every fourth
 * page would each end up holding the lot.
 *
 * Nothing here needs the book in order, which is what makes it divisible at
 * all - inferring one page's blocks is a question about that page. What does
 * need the order is the caller, and it gets it: each reader hands back its own
 * run, and the runs are laid end to end.
 */
private suspend fun readPages(
    readers: List<PageContent>,
    pageSizes: List<PageSize>,
    pageCount: Int,
    onProgress: (TextExtraction) -> Unit,
): List<List<TextBlock>> = coroutineScope {
    val counted = Mutex()
    var read = 0

    val run = (pageCount + readers.size - 1) / readers.size

    readers.mapIndexed { index, reader ->
        val from = (index * run).coerceAtMost(pageCount)
        val until = (from + run).coerceAtMost(pageCount)

        async {
            (from until until).map { page ->
                reader.blocksOn(page, pageSizes).also {
                    // Counted rather than reported by whoever reached it, since
                    // several readers are somewhere in the book at once and no
                    // one of them knows how far the rest have got.
                    counted.withLock { onProgress(TextExtraction(++read, pageCount)) }
                }
            }
        }
    }.awaitAll().flatten()
}

/** One page read back as the things written and drawn on it. */
private suspend fun PageContent.blocksOn(index: Int, pageSizes: List<PageSize>): List<TextBlock> {
    // A page that cannot be read costs the reader that page rather than the
    // book, exactly as a page that cannot be drawn does.
    val glyphs = runCatchingApp { pageText(index) }.getOrNull().orEmpty()
    val drawn = runCatchingApp { pageImages(index) }.getOrNull().orEmpty()
    val size = pageSizes.getOrElse(index) { PageSize(width = 0f, height = 0f) }

    return pdfPageBlocks(glyphs, drawn, size)
}

/**
 * Joins a paragraph that ran over the foot of one page onto the head of the
 * next.
 *
 * A page break in a PDF falls wherever the paper ended, which is very often in
 * the middle of a sentence. Left alone, each half arrives as a paragraph of its
 * own - so the re-set book is full of paragraphs that stop mid-thought and
 * others that begin with a lower-case word, which is the tell-tale of a badly
 * converted book.
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
 * Two signals, and both are needed: the first has to look unfinished, and the
 * second has to look like a continuation. Either alone is common enough
 * innocently - a heading carries no full stop, and a page can open on a
 * lower-case word that begins a genuine new paragraph in a language that sets
 * one that way - and joining two paragraphs that were never one is a mistake the
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
