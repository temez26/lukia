package io.lukia.features.reader.presentation.pagination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection

/**
 * The book laid out for the window it is being read in, beginning first.
 *
 * Re-run whenever anything the layout depends on moves - the measure, the height
 * of a page, the type size - and never on anything else: a translation arriving
 * or a page being scrolled past is not a reason to lay a book out again.
 *
 * The measuring happens off the main thread, on as many of them as the device
 * has to spare, and arrives in pieces - see [paginateProgressively]. What comes
 * back first is a true beginning of the book rather than the whole of it, which
 * is what lets somebody start reading a long book while its later chapters are
 * still being measured. The previous layout stays on screen until the new one
 * has reached the page being read, because blanking a page somebody is looking
 * at would be a worse answer than a page that is briefly a line too long.
 *
 * [openingPage] is the page the reader is being put on, and is how far the
 * layout has to have got before it is worth showing: a book that opened at its
 * first page while somebody was five chapters in would be showing them the wrong
 * book. It is null while the stored position is still being read, and a book
 * with nowhere settled to open at is not one to show a beginning of - so nothing
 * goes out until it is known, which is what it was before any of this was
 * measured a run at a time.
 */
@Composable
internal fun rememberPagination(
    sections: List<TextSection>,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    figureSize: FigureSize,
    openingPage: Int?,
): PaginatedBook {
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current

    val workerCount = remember { paginationWorkers() }

    // One measurer per worker rather than one for the book. A TextMeasurer keeps
    // a cache of what it has laid out and is not written to be shared between
    // threads, so measuring a book on four of them through one would be a race
    // over that cache rather than four times the speed.
    val measurers = remember(workerCount, fontFamilyResolver, density, layoutDirection) {
        List(workerCount) { TextMeasurer(fontFamilyResolver, density, layoutDirection) }
    }

    // Kept across re-layouts, because the shape of an illustration does not
    // depend on the type size and decoding a book's plates again on every pinch
    // would be the most expensive thing the reader does. One per worker for the
    // same reason there is one measurer per worker; each run of the book goes to
    // the same worker every time, so a plate is decoded by one of them and is
    // still decoded once.
    val figures = remember(workerCount, sections, density) {
        List(workerCount) { FigureShapes(density) }
    }

    // The style with its colour taken out, which is what the book is actually
    // measured against: no line is a different height for being a different
    // colour. It matters because the reading theme is a colour - so a book keyed
    // on the style as given was laid out again from end to end every time the
    // page went from light to dark, on a reader that otherwise switches theme
    // without re-rendering a thing. Two of these built from two themes compare
    // equal, which is what stops the producer below from restarting.
    val measured = remember(style) { style.copy(color = Color.Unspecified) }

    // Read rather than captured: where the book is opening is settled by a stored
    // position that may arrive after the measuring has started, and starting the
    // measuring again when it does would throw away the work it is waiting for.
    val opening by rememberUpdatedState(openingPage)

    val laid by produceState(
        initialValue = PaginatedBook.Empty,
        sections,
        measured,
        widthPx,
        heightPx,
        figureSize,
    ) {
        if (widthPx <= 0 || heightPx <= 0) return@produceState

        val sizes = measurers.mapIndexed { worker, measurer ->
            BlockMetrics(
                measurer = measurer,
                style = measured,
                widthPx = widthPx,
                density = density,
                figureHeight = { figure, width ->
                    figures[worker].heightOf(figure, width, figureSize)
                },
            )
        }

        paginateProgressively(
            sections = sections,
            pageHeightPx = heightPx,
            sizesPerWorker = sizes,
        ).collect { book ->
            // Nothing is shown until the page being opened at is on it. Before
            // that this is a book the reader has not asked to see the beginning
            // of, and putting it up would move them and then move them back.
            if (book.complete || opening?.let { it < book.pageCount } == true) value = book
        }
    }

    return laid
}

/**
 * How tall each of a book's illustrations comes out, remembered once.
 *
 * A figure is held as the bytes the book stores, and the only way to learn its
 * proportions is to decode it. That is done here rather than in the measurer so
 * that it is done once per book instead of once per layout - and only the size
 * is kept, because a book's plates decoded all at once are more memory than
 * every word in it.
 *
 * One of these belongs to one worker and is asked only from it, which is what
 * makes an ordinary map the right thing to keep the answers in.
 *
 * The answer is the height the page will actually give it, which is not always
 * the height of the measure: an illustration is drawn no larger than it is - see
 * `FigureBlock` - so a small one takes a small amount of the page, and a
 * measurement that assumed otherwise would leave a hole under every ornament.
 *
 * A plate that cannot be decoded is nothing tall, which is exactly what is drawn
 * for it: the reader shows no broken-image mark, so the page must not leave a
 * gap where one would have been.
 */
private class FigureShapes(private val density: Density) {

    /** Its own size in pixels, by figure. Figures compare by identity - see TextBlock.Figure. */
    private val sizes = mutableMapOf<TextBlock.Figure, IntSize>()

    fun heightOf(figure: TextBlock.Figure, widthPx: Int, size: FigureSize): Int {
        val shape = sizes.getOrPut(figure) {
            runCatching { figure.bytes.decodeToImageBitmap() }
                .map { IntSize(it.width, it.height) }
                .getOrDefault(IntSize.Zero)
        }

        if (shape.width <= 0) return 0

        // The same cap the drawing uses - a page cut against a full-width figure
        // that is then drawn small would leave a hole under every ornament.
        val asked = if (size.fillsMeasure) {
            widthPx
        } else {
            with(density) { (shape.width.dp * size.scale).roundToPx() }
        }

        return minOf(widthPx, asked) * shape.height / shape.width
    }
}
