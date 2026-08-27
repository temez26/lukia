package io.lukia.features.reader.presentation

import io.lukia.core.presentation.dataOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Where the work behind a page runs.
 *
 * Not what it produces - that is [ReaderViewModelTest]'s - but which thread it
 * produces it on. The reader asks for a page's words from composition, as each
 * page arrives, and a page arrives while the screen carrying it is still sliding
 * in over the library. Anything that answers that question inline answers it on
 * the thread drawing that slide.
 */
class ReaderPageWorkTest : ReaderViewModelTestCase() {

    /** Counts what is handed to it, and then hands it straight on. */
    private class Counting(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {

        var dispatches = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    private val counting = Counting(Dispatchers.Default)

    override val sentenceDispatcher: CoroutineDispatcher get() = counting

    @Test
    fun `gathers a page's words away from the caller`() = runBlocking {
        val document = pdfInLibrary(pageCount = 2, text = "The clocks were striking thirteen")
        val viewModel = createViewModel(document)

        assertNotNull(viewModel.settled().dataOrNull)

        viewModel.sentencesOn(0)

        assertTrue(
            counting.dispatches > 0,
            "Reading a page's words ran on the thread that asked for it, which " +
                "for the reader is the thread drawing the page.",
        )
    }
}
