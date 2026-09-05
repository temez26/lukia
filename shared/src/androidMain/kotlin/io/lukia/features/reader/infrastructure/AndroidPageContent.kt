package io.lukia.features.reader.infrastructure

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.lukia.features.reader.domain.page.PageContent
import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.TextQuad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens the PDF at [path] to be read rather than drawn - see [PageContent].
 *
 * On Android this is what the reader was already doing behind
 * [android.graphics.pdf.PdfRenderer], so a handle opened only to read pages is
 * exactly this and nothing else: no renderer, no page measurements and no table
 * of contents.
 */
internal actual fun openPdfPageContent(path: String): PageContent =
    AndroidPageContent(File(path))

/**
 * What is on a page of a PDF on Android: the words, and the pictures.
 *
 * Android's own [android.graphics.pdf.PdfRenderer] draws pages and says nothing
 * about what is on them - it grew a text API only in API 35, which is far above
 * this app's minimum, and has never had one for images. So the file is opened a
 * second time, by a library that can read it, purely to answer "what does this
 * page say, what is drawn on it, and where".
 *
 * Opened lazily and only once: most books are never asked either question, and a
 * reader who asks one of them will ask it again. The handle is given back with
 * the document it belongs to.
 *
 * This is the same library the desktop uses, repackaged - which is why what
 * follows reads like its counterpart in `PdfDocumentPages.jvm.kt` and
 * `PdfBoxPageImages.kt` despite sharing no code with them. The two libraries
 * have different package names, different image types and different ways of
 * being opened; everything after the glyphs and the pictures come out is common
 * code.
 */
internal class AndroidPageContent(private val file: File) : PageContent {

    private var document: PDDocument? = null
    private var opened = false

    /**
     * The pictures this book has already given up, kept for as long as it is
     * open - see [AndroidFigureBytes]. Held beside the document because that is
     * the scope a repeated picture repeats over.
     */
    private val figures = AndroidFigureBytes()

    /**
     * PDFBox keeps mutable state inside a document, so two pages read at once
     * corrupt each other's output. Its own rather than the renderer's, because
     * a conversion opens several of these and each one is a separate document.
     */
    private val lock = Mutex()

    override suspend fun pageText(index: Int): List<PageGlyph>? =
        withContext(Dispatchers.IO) { lock.withLock { glyphs(index) } }

    override suspend fun pageImages(index: Int): List<PageImage> =
        withContext(Dispatchers.IO) { lock.withLock { images(index) } }

    private fun glyphs(pageIndex: Int): List<PageGlyph>? {
        val document = document() ?: return null

        return runCatching {
            val stripper = GlyphStripper().apply {
                // PDFBox counts pages from one, and reads the range it is given
                // rather than the whole document.
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }

            stripper.getText(document)
            stripper.glyphs.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun images(pageIndex: Int): List<PageImage> {
        val document = document() ?: return emptyList()

        return runCatching { androidPageImages(document.getPage(pageIndex), figures) }
            .getOrDefault(emptyList())
    }

    /**
     * A file that cannot be opened for reading is remembered as such, so a book
     * this library will not parse is not re-parsed on every tap.
     */
    private fun document(): PDDocument? {
        if (!opened) {
            opened = true
            document = runCatching { PDDocument.load(file) }.getOrNull()
        }

        return document
    }

    override fun close() {
        runCatching { document?.close() }
        document = null
    }
}

/**
 * Collects every positioned character on a page instead of writing the page out
 * as a string. See the desktop's own `GlyphStripper` for why the layer
 * underneath the text is the one wanted here.
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
