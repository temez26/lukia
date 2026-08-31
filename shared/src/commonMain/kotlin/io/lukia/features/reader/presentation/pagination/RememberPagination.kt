package io.lukia.features.reader.presentation.pagination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The book laid out for the window it is being read in.
 *
 * Re-run whenever anything the layout depends on moves - the measure, the height
 * of a page, the type size - and never on anything else: a translation arriving
 * or a page being scrolled past is not a reason to lay a book out again.
 *
 * The measuring happens off the main thread, and the previous layout stays on
 * screen until the new one is ready. A book of a few thousand paragraphs takes
 * long enough to measure that doing it in composition would drop the frame that
 * a pinch or a window resize ends on, and blanking the page while it happens
 * would be a worse answer than a page that is briefly a line too long.
 */
@Composable
internal fun rememberPagination(
    sections: List<TextSection>,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    figureSize: FigureSize,
): PaginatedBook {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Kept across re-layouts, because the shape of an illustration does not
    // depend on the type size and decoding a book's plates again on every pinch
    // would be the most expensive thing the reader does.
    val figures = remember(sections, density) { FigureShapes(density) }

    // The style with its colour taken out, which is what the book is actually
    // measured against: no line is a different height for being a different
    // colour. It matters because the reading theme is a colour - so a book keyed
    // on the style as given was laid out again from end to end every time the
    // page went from light to dark, on a reader that otherwise switches theme
    // without re-rendering a thing. Two of these built from two themes compare
    // equal, which is what stops the producer below from restarting.
    val measured = remember(style) { style.copy(color = Color.Unspecified) }

    val laid by produceState(
        initialValue = PaginatedBook.Empty,
        sections,
        measured,
        widthPx,
        heightPx,
        figureSize,
    ) {
        if (widthPx <= 0 || heightPx <= 0) return@produceState

        value = withContext(Dispatchers.Default) {
            paginate(
                sections = sections,
                sizes = BlockMetrics(
                    measurer = measurer,
                    style = measured,
                    widthPx = widthPx,
                    density = density,
                    figureHeight = { figure, width -> figures.heightOf(figure, width, figureSize) },
                ),
                pageHeightPx = heightPx,
            )
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
