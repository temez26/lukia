package io.lukia.shared.presentation.components.cover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.lukia.shared.presentation.theme.LukiaMotion

/**
 * A book's cover filling whatever box it is given, cropped rather than fitted:
 * for a card the cover is the background of, rather than a cover on a shelf.
 *
 * Cropped from the bottom up, keeping the top. A cover puts its title and its
 * picture high, and a card that lays anything over its artwork lays it over
 * the lower part. A book with no artwork fills the box with its generated tile
 * and letter instead, as [BookCover] does.
 *
 * Covers are kept decoded no larger than [MaxCoverEdge], so one stretched
 * across a wide card is drawn softer than the same cover on a shelf.
 */
@Composable
fun CoverBackground(
    cover: CoverArtSource?,
    title: String,
    modifier: Modifier = Modifier,
    monogramFontSize: TextUnit = 72.sp,
) {
    val art = rememberCoverArt(cover)
    val baseColor = remember(title) { bookCoverColor(title) }
    val artworkAlpha by animateFloatAsState(
        targetValue = if (art is CoverArt.Artwork) 1f else 0f,
        animationSpec = LukiaMotion.standard(),
        label = "coverBackground",
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.coverTile(baseColor)) {
        when (art) {
            is CoverArt.Artwork -> Image(
                bitmap = art.cover.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = artworkAlpha },
            )

            CoverArt.Absent -> CoverMonogram(title = title, fontSize = monogramFontSize)

            // Nothing but the tile, which is already drawn behind this.
            CoverArt.Pending -> Unit
        }
    }
}
