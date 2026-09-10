package io.lukia.features.reader.presentation.rendering

import androidx.compose.ui.graphics.ImageBitmap
import io.lukia.features.reader.domain.text.TextBlock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * How much decoded illustration is kept at once, counted in pixels rather than
 * in plates so that one full-page photograph cannot occupy the space of twenty
 * ornaments.
 *
 * Eight million pixels is around 32MB at four bytes each - the same order as the
 * covers, and half what the drawn PDF pages are allowed. A page of prose carries
 * one or two plates, so this is a spread and its neighbours several times over,
 * which is what makes reading back and forth over an illustration free.
 *
 * Sized against what is already held rather than against what a device has: a
 * book's plates are kept as their stored bytes for as long as it is open - see
 * [TextBlock.Figure] - so this budget sits on top of those and not instead of
 * them.
 */
private const val MAX_CACHED_PIXELS = 8_000_000

/**
 * The illustrations already decoded, most recently used last.
 *
 * A cache rather than decoding per appearance, as this once did: a plate is
 * decoded on the thread that is not drawing, but it is still megabytes and a
 * measurable pause, and a reader moving back and forth across a spread paid it
 * again every time the picture came into view. It was the one expensive thing
 * the reader did with nothing behind it - the covers have [CoverArtCache], the
 * drawn pages [RenderedPageCache], a page's parsed text [PageSentenceCache].
 *
 * Bounded, because a whole illustrated book decoded at once is hundreds of
 * megabytes and the crash that follows is worse than a pause.
 *
 * Keyed on the figure itself, which is one object handed down from the parse and
 * compares by identity - see [TextBlock.Figure]. That is also why the book being
 * put away has to say so: a key here holds its own stored bytes alive, so a
 * closed book's plates would sit in the budget until something pushed them out.
 * See [clear].
 *
 * One for the process rather than one per open book, as the covers are, because
 * what asks is a block deep inside the page and has nothing handed down to it.
 */
internal object FigureImageCache {

    /**
     * What is held, behind a reference that is replaced rather than emptied -
     * see [clear].
     *
     * Read once into a local wherever it is used, so that a swap part way
     * through leaves that use working on the map it started with rather than on
     * half of two.
     */
    @Volatile
    private var kept = KeptFigures()

    private val lock = Mutex()

    /**
     * The image for [figure] if it has already been decoded, answered without
     * suspending.
     *
     * What lets a plate already in memory be drawn on the frame its block is
     * composed on. [imageFor] cannot: it suspends, so even a hit arrives a frame
     * or more later, and until then the page holds a gap where the picture goes
     * - the flicker that runs down a chapter being scrolled back through.
     */
    fun peek(figure: TextBlock.Figure): ImageBitmap? = kept.decoded[figure]

    /**
     * The image for [figure], decoding it with [decode] if it is not already
     * held. Null when it will not decode at all, which is what a plate the
     * reader draws nothing for looks like.
     *
     * [decode] is called outside the lock, so one slow plate does not hold up
     * every other block on the page. Two blocks asking for the same figure at
     * the same moment may both decode it, which costs one wasted decode and is
     * far cheaper than serialising all of them behind one mutex - the trade
     * [CoverArtCache] makes, for the same reason and with the same rarity.
     */
    suspend fun imageFor(
        figure: TextBlock.Figure,
        decode: suspend () -> ImageBitmap?,
    ): ImageBitmap? {
        held(figure)?.let { return it }

        val image = decode() ?: return null

        keep(figure, image)

        return image
    }

    /**
     * Lets go of every decoded illustration.
     *
     * For the book being put away, and for the platform asking the app to give
     * memory back - see Android's `onTrimMemory`. Nothing is lost either way: a
     * plate dropped here is decoded again the next time a page asks for it,
     * which is the trade this whole cache exists to make in the other direction.
     *
     * Swapped rather than emptied, and for the reason [RenderedPageCache.clear]
     * is: this is called from the thread that draws, which cannot wait on the
     * lock, while a plate may be part way through being stored on another. That
     * plate finishes into the map it started with, and that map is by then
     * nobody's.
     */
    fun clear() {
        kept = KeptFigures()
    }

    /**
     * Removed and put back rather than read in place: this map keeps its
     * insertion order, so re-inserting is what moves a plate to the newest end
     * and out of the way of the next eviction.
     */
    private suspend fun held(figure: TextBlock.Figure): ImageBitmap? = lock.withLock {
        val images = kept.images

        images.remove(figure)?.also { images[figure] = it }
    }

    private suspend fun keep(figure: TextBlock.Figure, image: ImageBitmap) = lock.withLock {
        val held = kept

        held.images.remove(figure)?.let { held.pixels -= it.pixelCount }
        held.images[figure] = image
        held.pixels += image.pixelCount

        // The last entry survives whatever its size: the plate being asked for
        // right now is the one thing certainly about to be drawn, and a
        // photograph over the whole budget on its own is exactly the one that
        // must not be decoded again on the next scroll back.
        while (held.pixels > MAX_CACHED_PIXELS && held.images.size > 1) {
            val oldest = held.images.keys.first()

            held.images.remove(oldest)?.let { held.pixels -= it.pixelCount }
        }

        held.publish()
    }
}

/** One reading's decoded plates, and what they weigh. */
private class KeptFigures {

    val images = LinkedHashMap<TextBlock.Figure, ImageBitmap>()
    var pixels = 0

    /**
     * What [images] holds, in a form that can be read without suspending.
     *
     * Replaced wholesale rather than mutated, so a reader either sees the map as
     * it was before a plate was kept or as it was after, and never mid-edit.
     */
    @Volatile
    var decoded: Map<TextBlock.Figure, ImageBitmap> = emptyMap()
        private set

    fun publish() {
        decoded = images.toMap()
    }
}

private val ImageBitmap.pixelCount: Int get() = width * height
