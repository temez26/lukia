package io.lukia.features.reader.presentation

import io.lukia.features.reader.domain.ReaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drawing pages, and the cache in front of it.
 *
 * What is worth proving here is when a page is *not* drawn again: nearby widths
 * share a render, and changing the theme does not throw the drawn pages away.
 */
class ReaderPageRenderingTest : ReaderViewModelTestCase() {

    @Test
    fun rendersAPageAtTheWidthItIsAskedFor() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val page = viewModel.page(index = 0, widthPx = 384)

        assertNotNull(page)
        assertEquals(384, page.width)
    }

    /**
     * Pinching changes the page width continuously. Rounding to a render step is
     * what keeps that from asking for a new render on every frame - two widths a
     * few pixels apart have to come back as the same drawn page.
     */
    @Test
    fun nearbyWidthsShareOneRender() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val first = viewModel.page(index = 0, widthPx = 380)
        val second = viewModel.page(index = 0, widthPx = 396)

        assertTrue(first === second)
    }

    /** Scrolling back to a page must not redraw it. */
    @Test
    fun aPageAlreadyDrawnIsServedFromTheCache() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val first = viewModel.page(index = 0, widthPx = 384)
        val second = viewModel.page(index = 0, widthPx = 384)

        assertTrue(first === second)
    }

    /**
     * Theme is applied when a page is drawn, not when it is rendered. Changing
     * it must therefore cost nothing: the pages already rendered stay in the
     * cache, which is what keeps the change instant in a long book.
     */
    @Test
    fun changingTheThemeDoesNotDiscardRenderedPages() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val before = viewModel.page(index = 0, widthPx = 384)
        viewModel.updateSettings(viewModel.settings.value.copy(theme = ReaderTheme.Dark))
        val after = viewModel.page(index = 0, widthPx = 384)

        assertTrue(before === after)
    }

    /**
     * Reading an outline can mean scanning the whole file, so it is started in
     * the background as the book opens rather than when the panel asks - and
     * never on the thread drawing the pages.
     */
}
