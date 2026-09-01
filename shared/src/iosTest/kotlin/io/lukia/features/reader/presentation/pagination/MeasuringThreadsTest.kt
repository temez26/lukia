package io.lukia.features.reader.presentation.pagination

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun
import io.lukia.features.reader.domain.text.TextSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A book is measured on one thread here, and laying one out does not end the
 * app.
 *
 * Compose lays text out through one cache of styles shared by the whole process,
 * and on this platform that cache is a map with no lock on it - see
 * [maxParallelMeasurers]. A book measured on four threads at once used to take
 * the app down with a ConcurrentModificationException from inside the layout, a
 * second after the reader had opened.
 */
class MeasuringThreadsTest {

    @Test
    fun a_book_is_measured_on_one_thread() {
        assertEquals(1, paginationWorkers())
    }

    /**
     * And that one thread is the thread the reader is drawn on. A background
     * thread measuring while the main one draws is the two-thread case all over
     * again - it is what still crashed on a device once the workers were down to
     * one.
     */
    @Test
    fun that_thread_is_the_one_the_reader_is_drawn_on() {
        assertSame(Dispatchers.Main, measuringContext())
    }

    @Test
    fun a_book_lays_out_through_the_reader_s_own_workers() = runBlocking {
        val density = Density(2f)
        val style = TextStyle(fontSize = 16.sp)
        val resolver = createFontFamilyResolver()

        val sizes = List(paginationWorkers()) {
            BlockMetrics(
                measurer = TextMeasurer(resolver, density, LayoutDirection.Ltr),
                style = style,
                widthPx = 1400,
                density = density,
                figureHeight = { _, _ -> 0 },
            )
        }

        val books = paginateProgressively(
            sections = book(chapters = 24, paragraphs = 30),
            pageHeightPx = 1600,
            sizesPerWorker = sizes,
            // On this thread rather than the reader's: the main one is held by
            // the test that is waiting for the book, so measuring there would
            // wait for itself.
            context = EmptyCoroutineContext,
        ).toList()

        // Still handed over a run at a time, which is what the reader waits on -
        // measuring on one thread is not measuring all of it before showing any.
        assertTrue(books.size > 1, "the book arrived in one piece")
        assertTrue(books.last().complete)
        assertTrue(books.last().pageCount > 0)
    }

    /** Long enough to be cut into several runs - see [chunkStarts]. */
    private fun book(chapters: Int, paragraphs: Int): List<TextSection> = List(chapters) { chapter ->
        TextSection(
            title = "Chapter $chapter",
            blocks = List(paragraphs) { index ->
                TextBlock.Paragraph(
                    listOf(TextRun("Paragraph $index of chapter $chapter. ".repeat(12))),
                )
            },
        )
    }
}
