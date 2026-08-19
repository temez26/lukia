package io.lukia.features.reader.infrastructure

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import io.lukia.features.reader.domain.PageGlyph
import io.lukia.features.reader.domain.TextQuad
import java.io.File

/**
 * The text layer of a PDF on Android.
 *
 * Android's own [android.graphics.pdf.PdfRenderer] draws pages and says nothing
 * about what is written on them - it grew a text API only in API 35, which is
 * far above this app's minimum. So the file is opened a second time, by a
 * library that can read it, purely to answer "what does this page say and
 * where".
 *
 * Opened lazily and only once: most books are never asked this question, and a
 * reader who taps one page will tap several. The handle is given back with the
 * document it belongs to.
 *
 * This is the same library the desktop uses, repackaged - which is why the
 * extraction below reads like its counterpart in `PdfDocumentPages.jvm.kt`
 * despite sharing no code with it. The two libraries have different package
 * names and different ways of being opened; everything after the glyphs come
 * out is common code.
 */
internal class AndroidPageText(private val file: File) : AutoCloseable {

    private var document: PDDocument? = null
    private var opened = false

    fun glyphs(pageIndex: Int): List<PageGlyph>? {
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
