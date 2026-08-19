package io.lukia.shared.presentation.components

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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.CoverImage
import io.lukia.shared.presentation.theme.LukiaElevation
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Standard trade-paperback proportions, used for every cover in the app. */
const val BOOK_COVER_ASPECT_RATIO = 0.68f

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

    data class Artwork(val bitmap: ImageBitmap) : CoverArt
}

/**
 * Decodes a cover, off the main thread and only once.
 *
 * Covers are stored as they were extracted - a PDF's first page rendered at 96
 * DPI is around 800x1100 - and decoding one costs several megabytes and a
 * measurable pause. Doing that inside composition, as this once did, meant every
 * card scrolled into view stalled the frame it appeared on, so a fast scroll
 * through a large library stuttered on all three platforms.
 *
 * What comes back is kept in [CoverArtCache], shrunk to the size it is actually
 * drawn at and held to a fixed budget. That is what makes scrolling back up a
 * long shelf free rather than a second decode of everything on the way, without
 * a library of any size costing more memory than a library of twenty books.
 */
@Composable
internal fun rememberCoverArt(coverImage: CoverImage?): CoverArt {
    val art by produceState<CoverArt>(initialValue = CoverArt.Pending, coverImage) {
        // Reset explicitly: produceState keeps the previous value across a key
        // change, which would otherwise show one book's cover on another's card
        // for as long as the new one takes to decode.
        value = CoverArt.Pending

        value = if (coverImage == null) {
            CoverArt.Absent
        } else {
            withContext(Dispatchers.Default) {
                CoverArtCache.artworkFor(coverImage) { cover ->
                    // A cover that will not decode is a cover the book does not
                    // have, from the reader's side - so it is not an error to
                    // report, and not something to try again on every scroll.
                    runCatching { cover.bytes.decodeToImageBitmap() }.getOrNull()
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
    Color(0xFF6E5B8B)
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
 */
@Composable
fun BookCover(
    coverImage: CoverImage?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: Dp = LukiaElevation.card,
    monogramFontSize: TextUnit = 30.sp
) {
    val art = rememberCoverArt(coverImage)
    val baseColor = remember(title) { bookCoverColor(title) }

    val artworkAlpha by animateFloatAsState(
        targetValue = if (art is CoverArt.Artwork) 1f else 0f,
        animationSpec = LukiaMotion.standard(),
        label = "coverArtwork"
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
                        lerp(baseColor, Color.Black, 0.22f)
                    )
                )
            )
            // A faint inner edge keeps light covers from bleeding into a light page.
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.08f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        when (art) {
            is CoverArt.Artwork -> Image(
                bitmap = art.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = artworkAlpha }
            )

            CoverArt.Absent -> Text(
                text = title.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = monogramFontSize,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = monogramFontSize,
                    fontWeight = FontWeight.SemiBold
                )
            )

            // Nothing but the tile, which is already drawn behind this.
            CoverArt.Pending -> Unit
        }
    }
}
