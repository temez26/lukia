package io.lukia.features.reader.presentation

import androidx.compose.ui.graphics.ImageBitmap
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.FakePdfLayoutRepository
import io.lukia.core.domain.PdfLayout
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.WorkingCopy
import io.lukia.features.reader.domain.DocumentPages
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.ReaderDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The choice on its own, without a book being opened around it.
 *
 * What is here is the part that only shows up when two openings overlap. A book
 * is opened from a coroutine whose tail - settling what it opened as, restoring
 * the position, reading the outline - keeps running after the reader can see the
 * book. Ask for the other layout during that tail and there are briefly two
 * openings in flight, one of them about a book already abandoned.
 */
class ReaderLayoutChoiceTest {

    private val location = "/books/one.pdf"
    private val repository = FakePdfLayoutRepository()

    private fun choice(default: PdfLayout = PdfLayout.Original) =
        ReaderLayoutChoice(location, repository) { default }

    /**
     * An opening that has been superseded says nothing.
     *
     * The reader opened this book as pages, asked for text while that opening
     * was still finishing, and the abandoned opening then settled. It is
     * reporting on the paged document it opened, not on the text one being
     * opened now - so it must not conclude that this book has no text in it,
     * and must not put the choice back.
     */
    @Test
    fun anOpeningThatHasBeenSupersededDoesNotUndoTheNewChoice() = runTest {
        val choice = choice()
        choice.restore()

        choice.choose(PdfLayout.Text)
        choice.settle(pdfOpenedAsPages(), openedAs = PdfLayout.Original)

        assertEquals(PdfLayout.Text, choice.layout.value)
        assertEquals(PdfLayout.Text, repository.stored(location))
        assertFalse(choice.noTextLayer.value, "the reader was told about the wrong book")
    }

    /**
     * A scan still falls back, when it is the opening in hand that found one.
     * This is the case the guard above must not swallow.
     */
    @Test
    fun aPdfAskedForAsTextThatCameBackAsPagesFallsBack() = runTest {
        val choice = choice(default = PdfLayout.Text)
        choice.restore()

        choice.settle(pdfOpenedAsPages(), openedAs = PdfLayout.Text)

        assertEquals(PdfLayout.Original, choice.layout.value)
        assertEquals(PdfLayout.Original, repository.stored(location))
        assertEquals(true, choice.noTextLayer.value)
    }

    private fun pdfOpenedAsPages() = ReaderDocument(
        info = DocumentInfo(
            document = PickedDocument(
                fileName = "one.pdf",
                extension = "pdf",
                location = location,
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1,
        ),
        workingCopy = WorkingCopy(sourceLocation = location, path = location),
        content = PagedContent(TwoBlankPages),
    )

    private object TwoBlankPages : DocumentPages {
        override val outline: List<OutlineEntry> = emptyList()
        override val pageSizes: List<PageSize> =
            List(2) { PageSize(width = 600f, height = 800f) }

        override suspend fun renderPage(index: Int, targetWidthPx: Int): ImageBitmap? = null

        override fun close() = Unit
    }
}
