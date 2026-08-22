package io.lukia.features.reader.presentation

import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.domain.PagedContent
import io.lukia.features.reader.domain.PdfLayout
import io.lukia.features.reader.domain.ReflowableContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Reading a PDF as text rather than as its own pages.
 *
 * What the conversion makes of a page is settled elsewhere - see
 * `PdfPageBlocksTest` and `PdfAsTextTest`. What is here is the choice: that it
 * is remembered per book, that changing it re-opens the book, and what happens
 * when it is asked of a file with no text in it.
 */
class ReaderLayoutTest : ReaderViewModelTestCase() {

    @Test
    fun aPdfOpensAsItsOwnPagesUntilSomebodySaysOtherwise() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 2, text = "Page"))

        assertIs<PagedContent>(viewModel.settled().dataOrNull?.content)
        assertEquals(PdfLayout.Original, viewModel.pdfLayout.value)
    }

    @Test
    fun aPdfChosenAsTextOpensAsAReflowableBook() = runTest {
        val document = pdfInLibrary(pageCount = 2, text = "Call me Ishmael. This is page")
        layouts.save(document.document.location, PdfLayout.Text)

        val viewModel = createViewModel(document)

        val content = assertIs<ReflowableContent>(viewModel.settled().dataOrNull?.content)
        assertEquals(2, content.sections.size, "a section per page of the file")
    }

    /** The choice is about this book, and is there the next time it is opened. */
    @Test
    fun choosingTextReReadsTheBookAndIsRemembered() = runTest {
        val document = pdfInLibrary(pageCount = 2, text = "Call me Ishmael. This is page")
        val viewModel = createViewModel(document)
        viewModel.settled()

        viewModel.setPdfLayout(PdfLayout.Text)

        assertIs<ReflowableContent>(viewModel.settled().dataOrNull?.content)
        assertEquals(PdfLayout.Text, layouts.stored(document.document.location))
    }

    /**
     * A scan is pages of pictures of words. There is nothing to re-set, and
     * taking the book away over a setting would be the wrong answer - so it
     * opens as pages, and says why.
     */
    @Test
    fun aPdfWithNoTextLayerFallsBackToItsPages() = runTest {
        val document = pdfInLibrary(pageCount = 2)
        val viewModel = createViewModel(document)
        viewModel.settled()

        viewModel.setPdfLayout(PdfLayout.Text)

        assertIs<PagedContent>(viewModel.settled().dataOrNull?.content)
        assertTrue(viewModel.noTextLayer.first { it }, "nobody was told")
        assertEquals(PdfLayout.Original, viewModel.pdfLayout.value)
        assertEquals(PdfLayout.Original, layouts.stored(document.document.location))
    }

    /**
     * Somebody who changes their mind while a long file is being read gets the
     * book they asked for last. The opening they abandoned must not arrive over
     * the top of it.
     */
    @Test
    fun changingTheChoiceAgainLeavesTheReaderWithTheLastOneAskedFor() = runTest {
        val document = pdfInLibrary(pageCount = 3, text = "Call me Ishmael. This is page")
        val viewModel = createViewModel(document)
        viewModel.settled()

        viewModel.setPdfLayout(PdfLayout.Text)
        viewModel.setPdfLayout(PdfLayout.Original)

        assertIs<PagedContent>(viewModel.settled().dataOrNull?.content)
        assertEquals(PdfLayout.Original, viewModel.pdfLayout.value)
    }

    /**
     * Reading the file is the one wait in the reader worth a number, and it is
     * over the moment the book is open - a progress report still standing over
     * an open book would be a reader waiting for something that already
     * happened.
     */
    @Test
    fun theProgressReportIsClearedOnceTheBookIsOpen() = runTest {
        val document = pdfInLibrary(pageCount = 3, text = "Call me Ishmael. This is page")
        layouts.save(document.document.location, PdfLayout.Text)

        val viewModel = createViewModel(document)
        viewModel.settled()

        // Cleared once the book is open: what it reports is work in progress.
        assertEquals(null, viewModel.textExtraction.value)
    }
}
