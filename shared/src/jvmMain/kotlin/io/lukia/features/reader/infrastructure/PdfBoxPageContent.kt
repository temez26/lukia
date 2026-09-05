package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.page.PageContent
import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.TextQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File

/**
 * Opens the PDF at [path] to be read rather than drawn - see [PageContent].
 *
 * A handle of its own, holding nothing the reader needs: no renderer, no page
 * measurements and no table of contents. That is what makes it cheap enough to
 * open several of, which is what a conversion does.
 */
internal actual fun openPdfPageContent(path: String): PageContent =
    PdfBoxPageContent(Loader.loadPDF(File(path)), Mutex(), owned = true)

/**
 * What is on a PDF's pages, read through PDFBox.
 *
 * Takes the document rather than opening one, because it is used both ways: the
 * open book reads through the same document it draws from, and a conversion
 * opens documents of its own to read several pages at once. [owned] is which of
 * the two this is, and so whether closing it closes the document.
 *
 * [lock] is shared with whatever else touches the document. PDFBox keeps
 * mutable state inside one, so two pages read at once - or a page read while
 * another is drawn - corrupt each other's output.
 */
internal class PdfBoxPageContent(
    private val document: PDDocument,
    private val lock: Mutex,
    private val owned: Boolean,
) : PageContent {

    /**
     * The pictures this document has already given up, kept for as long as it
     * is open - see [PdfFigureBytes]. Held here because a handle is what a
     * repeated picture repeats within.
     */
    private val figures = PdfFigureBytes()

    override suspend fun pageText(index: Int): List<PageGlyph>? =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    val stripper = GlyphStripper().apply {
                        // PDFBox counts pages from one, and reads the range
                        // rather than the document when asked for text.
                        startPage = index + 1
                        endPage = index + 1
                    }

                    stripper.getText(document)

                    // Empty is not the same as unavailable, but it reaches the
                    // reader the same way: a page with nothing on it has no
                    // sentences to point at.
                    stripper.glyphs.takeIf { it.isNotEmpty() }
                }.getOrNull()
            }
        }

    /**
     * Walked page by page rather than read from the document, because a PDF
     * keeps no list of what is drawn where - see [pdfBoxPageImages].
     */
    override suspend fun pageImages(index: Int): List<PageImage> =
        withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching { pdfBoxPageImages(document.getPage(index), figures) }
                    .getOrDefault(emptyList())
            }
        }

    override fun close() {
        if (owned) document.close()
    }
}

/**
 * Collects every positioned character on a page instead of writing the page out
 * as a string.
 *
 * [PDFTextStripper] exists to produce text, and its own idea of where the words
 * and lines are is thrown away by the time it hands one over. What is wanted
 * here is the layer underneath that: the glyphs, and the boxes the file put them
 * in, which is what the reader needs to draw a translation exactly over the
 * words it stands for.
 *
 * The `Dir` accessors are the ones already turned the right way up - origin at
 * the top left, rotated pages accounted for - which is the space
 * [io.lukia.features.reader.domain.page.TextQuad] is defined in.
 */
private class GlyphStripper : PDFTextStripper() {

    val glyphs = mutableListOf<PageGlyph>()

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        textPositions.forEach { position ->
            val character = position.unicode ?: return@forEach
            if (character.isEmpty()) return@forEach

            glyphs += PageGlyph(
                text = character,
                bounds = TextQuad(
                    left = position.xDirAdj,
                    top = position.yDirAdj - position.heightDir,
                    right = position.xDirAdj + position.widthDirAdj,
                    bottom = position.yDirAdj,
                ),
            )
        }
    }
}
