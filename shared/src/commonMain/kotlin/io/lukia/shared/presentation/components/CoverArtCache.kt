package io.lukia.shared.presentation.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.lukia.core.domain.CoverImage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * How large a cover is kept once decoded, along its longer edge.
 *
 * The import holds a stored cover to this same edge, so most artwork arrives
 * here already this size. What this still catches is what was stored before it
 * did - a library imported by an earlier version can hold a publisher's artwork
 * at 1600x2400. A cell in the grid is around 150dp - four hundred and something
 * pixels on a phone - so one of those is a bitmap several times larger than
 * anything that will be drawn from it, and on a shelf of two hundred books that
 * difference is the difference between scrolling and stuttering.
 *
 * Sized for the largest a cover is ever drawn, which is the detail screen rather
 * than the grid.
 */
internal const val MaxCoverEdge = 800

/**
 * How much decoded artwork is kept at once, counted in pixels rather than in
 * covers so that one enormous cover cannot occupy the space of twenty ordinary
 * ones.
 *
 * Eight million pixels is around 32MB once each is four bytes, and rather more
 * than a screenful of grid - so scrolling down a shelf and back up again finds
 * its covers already decoded, while a library of any size still costs a fixed
 * and modest amount of memory.
 */
internal const val CoverPixelBudget = 8_000_000

/**
 * The covers already decoded, most recently used last.
 *
 * A cache rather than decoding per card, as this once did: decoding costs several
 * megabytes and a measurable pause, and a fast scroll through a large library was
 * paying it again for every cover that came back into view. Bounded rather than
 * unbounded, because a whole library's worth of decoded artwork is hundreds of
 * megabytes and the crash that follows is a worse answer than a stutter.
 *
 * Keyed on the artwork itself, so two entries for the same bytes cannot disagree
 * and a book re-read out of the database finds the cover it had before.
 * [CoverImage] caches its own hash, so a lookup does not re-read the bytes.
 */
internal object CoverArtCache {

    private val mutex = Mutex()
    private val entries = LinkedHashMap<CoverImage, ImageBitmap>()
    private var pixels = 0

    /**
     * What [entries] holds, in a form that can be read without suspending.
     *
     * Replaced wholesale rather than mutated, so a reader either sees the map as
     * it was before a cover was kept or as it was after, and never mid-edit.
     */
    @Volatile
    private var decoded: Map<CoverImage, ImageBitmap> = emptyMap()

    /**
     * The artwork for [cover] if it has already been decoded, answered without
     * suspending.
     *
     * This is what lets a cover the app already has in memory be drawn on the
     * frame its card is composed on. [artworkFor] cannot: it suspends, so even a
     * hit arrives a frame or more later, and until it does the card shows the
     * blank tile underneath - which is the flicker that runs through a shelf of
     * covers every time one comes back on screen.
     */
    fun peek(cover: CoverImage): ImageBitmap? = decoded[cover]

    /**
     * The artwork for [cover], decoding it with [decode] if it is not already
     * held. Null when it will not decode at all.
     *
     * [decode] is called outside the lock, so one slow cover does not hold up
     * every other card on screen. Two cards asking for the same cover at the
     * same moment may both decode it, which costs one wasted decode and is far
     * cheaper than serialising all of them behind one mutex.
     */
    suspend fun artworkFor(
        cover: CoverImage,
        decode: suspend (CoverImage) -> ImageBitmap?,
    ): ImageBitmap? {
        held(cover)?.let { return it }

        val artwork = decode(cover) ?: return null
        val scaled = artwork.scaledToFit(MaxCoverEdge)

        keep(cover, scaled)

        return scaled
    }

    private suspend fun held(cover: CoverImage): ImageBitmap? = mutex.withLock {
        // Removed and put back rather than read in place: this map keeps its
        // insertion order, so re-inserting is what moves a cover to the newest
        // end and out of the way of the next eviction.
        entries.remove(cover)?.also { entries[cover] = it }
    }

    private suspend fun keep(cover: CoverImage, artwork: ImageBitmap) = mutex.withLock {
        entries.remove(cover)?.let { pixels -= it.pixelCount }
        entries[cover] = artwork
        pixels += artwork.pixelCount

        // The last entry survives whatever its size: the cover being asked for
        // right now is the one thing that is certainly about to be drawn.
        while (pixels > CoverPixelBudget && entries.size > 1) {
            val oldest = entries.keys.first()
            entries.remove(oldest)?.let { pixels -= it.pixelCount }
        }

        decoded = entries.toMap()
    }

    /** For tests, which must not see what an earlier one left behind. */
    internal suspend fun clear() = mutex.withLock {
        entries.clear()
        pixels = 0
        decoded = emptyMap()
    }
}

private val ImageBitmap.pixelCount: Int get() = width * height

/**
 * What a cover of [width] by [height] is kept at, given a longest edge of
 * [maxEdge]. The proportions are preserved, and anything already small enough is
 * left exactly as it is.
 */
internal fun coverTargetSize(width: Int, height: Int, maxEdge: Int): IntSize {
    val longest = maxOf(width, height)

    if (longest <= maxEdge || longest <= 0) return IntSize(width, height)

    val scale = maxEdge.toFloat() / longest

    // At least one pixel each way: a cover so long and thin that its short edge
    // rounds to nothing is still a bitmap that has to have a size.
    return IntSize(
        width = (width * scale).toInt().coerceAtLeast(1),
        height = (height * scale).toInt().coerceAtLeast(1),
    )
}

/**
 * This bitmap redrawn no larger than [maxEdge] along its longer edge, or itself
 * when it is already that small.
 */
internal fun ImageBitmap.scaledToFit(maxEdge: Int): ImageBitmap {
    val target = coverTargetSize(width, height, maxEdge)

    if (target.width == width && target.height == height) return this

    val scaled = ImageBitmap(target.width, target.height)
    val canvas = Canvas(scaled)

    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(target.width.toFloat(), target.height.toFloat()),
    ) {
        drawImage(
            image = this@scaledToFit,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(this@scaledToFit.width, this@scaledToFit.height),
            dstOffset = IntOffset.Zero,
            dstSize = target,
            // Cheaper than the highest quality and indistinguishable at the size
            // a cover is actually drawn, which is smaller again than this.
            filterQuality = FilterQuality.Medium,
        )
    }

    return scaled
}
