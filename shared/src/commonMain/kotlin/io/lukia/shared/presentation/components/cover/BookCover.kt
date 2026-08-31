package io.lukia.shared.presentation.components.cover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.shared.presentation.theme.LukiaElevation
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Standard trade-paperback proportions, used for every cover in the app. */
const val BOOK_COVER_ASPECT_RATIO = 0.68f

/**
 * How small the artwork is resampled to before being blown back up behind
 * itself. Small enough that nothing of the picture survives but its light, large
 * enough that the light still moves across the card. See [CoverArtwork].
 */
internal const val CoverBackdropEdge = 24

/** How far the backdrop is held behind the cover in front of it. */
private const val BackdropDim = 0.3f

/** What a book has to show for itself. */
@Immutable
internal sealed interface CoverArt {

    /** Artwork exists but is still being decoded; the tile stands in meanwhile. */
    data object Pending : CoverArt

    /**
     * Nothing usable to draw: the file carried no cover, or what it carried would
     * not decode. Both end in the monogram, because from the reader's side they
     * are the same thing.
     */
    data object Absent : CoverArt

    data class Artwork(val cover: DecodedCover) : CoverArt
}

/**
 * Decodes a cover, off the main thread and only once.
 *
 * A stored cover is around 800 pixels along its longer edge, and artwork stored
 * before covers were held to a size can be several times that - so decoding one
 * costs megabytes and a measurable pause. Doing that inside composition, as this
 * once did, meant every card scrolled into view stalled the frame it appeared
 * on, so a fast scroll through a large library stuttered on all three platforms.
 *
 * The bytes are read here too, one cover at a time, rather than travelling with
 * the book - see [CoverArtSource]. What comes back is kept in [CoverArtCache],
 * shrunk to the size it is actually drawn at and held to a fixed budget. That is
 * what makes scrolling back up a long shelf free rather than a second read of
 * everything on the way, without a library of any size costing more memory than
 * a library of twenty books.
 */
@Composable
internal fun rememberCoverArt(cover: CoverArtSource?): CoverArt {
    // Asked here rather than left to the producer below, which suspends and so
    // cannot answer before the frame this card is composed on. A cover already
    // in memory - every cover on a tab being returned to - would otherwise start
    // as [CoverArt.Pending] and fade in over its own blank tile, which is the
    // flicker that runs through a shelf each time it comes back on screen.
    val known = remember(cover) {
        cover?.let { CoverArtCache.peek(it.key) }?.let { CoverArt.Artwork(it) }
    }

    val art by produceState<CoverArt>(initialValue = known ?: CoverArt.Pending, cover) {
        // Reset explicitly: produceState keeps the previous value across a key
        // change, which would otherwise show one book's cover on another's card
        // for as long as the new one takes to decode.
        value = known ?: CoverArt.Pending

        // Run even when the cover was already known, so that asking the cache
        // for it is what marks it as recently used. The value that comes back is
        // the one already on screen, so nothing recomposes.
        value = if (cover == null) {
            CoverArt.Absent
        } else {
            withContext(Dispatchers.Default) {
                CoverArtCache.artworkFor(cover.key) {
                    // A cover that will not decode, and a book that has none
                    // stored, are the same thing from the reader's side - so
                    // neither is an error to report, and neither is worth trying
                    // again on every scroll.
                    runCatching { cover.load()?.decodeToImageBitmap() }.getOrNull()
                }
                    ?.let { CoverArt.Artwork(it) }
                    ?: CoverArt.Absent
            }
        }
    }

    return art
}

/**
 * Muted, book-spine-like hues. Deliberately desaturated so a wall of generated
 * covers stays calm rather than turning into a colour clash.
 */
private val bookCoverPalette = listOf(
    Color(0xFF4A5D7E),
    Color(0xFF6B4E71),
    Color(0xFF3F6C5F),
    Color(0xFF8C5A44),
    Color(0xFF54607A),
    Color(0xFF7A4F5A),
    Color(0xFF41665C),
    Color(0xFF6E5B8B),
)

internal fun bookCoverColor(seed: String): Color {
    val hash = if (seed.hashCode() == Int.MIN_VALUE) 0 else kotlin.math.abs(seed.hashCode())
    return bookCoverPalette[hash % bookCoverPalette.size]
}

/**
 * A book cover: the embedded artwork when the file had one, otherwise a generated
 * monogram tile. A monogram reads as intentional design in a way a repeated
 * generic icon does not.
 *
 * The tile is drawn first and the artwork fades in over it once decoded, so a
 * cover arriving is a settle rather than a pop - and a card that is scrolled past
 * before its artwork lands never flashes a monogram it was not going to keep.
 *
 * The artwork is fitted rather than cropped. Every shelf in the app draws its
 * covers at one set of proportions, and books do not come at one set of
 * proportions - so filling the tile means cutting the sides off a wide cover and
 * the title off a tall one. Fitted, the whole cover is there, and what fills the
 * space around it is the cover itself - see [CoverArtwork].
 */
@Composable
fun BookCover(
    cover: CoverArtSource?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: Dp = LukiaElevation.card,
    monogramFontSize: TextUnit = 30.sp,
) {
    val art = rememberCoverArt(cover)
    val baseColor = remember(title) { bookCoverColor(title) }

    val artworkAlpha by animateFloatAsState(
        targetValue = if (art is CoverArt.Artwork) 1f else 0f,
        animationSpec = LukiaMotion.standard(),
        label = "coverArtwork",
    )

    Box(
        modifier = modifier
            .aspectRatio(BOOK_COVER_ASPECT_RATIO)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(baseColor, Color.White, 0.12f),
                        lerp(baseColor, Color.Black, 0.22f),
                    ),
                ),
            )
            // A faint inner edge keeps light covers from bleeding into a light page.
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.08f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (art) {
            is CoverArt.Artwork -> CoverArtwork(cover = art.cover, alpha = artworkAlpha)

            CoverArt.Absent -> Text(
                text = title.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = monogramFontSize,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = monogramFontSize,
                    fontWeight = FontWeight.SemiBold,
                ),
            )

            // Nothing but the tile, which is already drawn behind this.
            CoverArt.Pending -> Unit
        }
    }
}

/**
 * A cover, whole, on a backdrop made of itself.
 *
 * A fitted cover leaves space wherever its proportions differ from the shelf's,
 * and a flat fill there reads as a coloured stripe above and below every book -
 * a grid of them looks like bunting rather than like a shelf. Filling it with a
 * blurred, dimmed blow-up of the same artwork is what makes the card read as one
 * object, which is the trick Apple Books and every music app have used for
 * years: the eye takes it as the cover's own light spilling out.
 *
 * The blur is the shrink. The backdrop is the artwork resampled down to a couple
 * of dozen pixels and then drawn back up across the whole card - bilinear
 * filtering turns that into a soft wash for nothing, and unlike a real blur it
 * costs the same on every platform and every Android version. A `Modifier.blur`
 * here would do nothing at all below Android 12.
 *
 * One layer for all three pieces, so the backdrop, its dimming and the cover
 * fade in together as one thing.
 */
@Composable
private fun CoverArtwork(cover: DecodedCover, alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
    ) {
        Image(
            bitmap = cover.backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Bilinear, which is what turns two dozen pixels blown up across a
            // card into a wash rather than into squares.
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize(),
        )

        // Held back from the cover in front of it, and by enough that a pale
        // backdrop cannot be mistaken for part of the artwork.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BackdropDim)),
        )

        Image(
            bitmap = cover.artwork,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
