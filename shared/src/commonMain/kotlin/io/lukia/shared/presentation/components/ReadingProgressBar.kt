package io.lukia.shared.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Thickness of the bar welded along the bottom edge of a cover. */
val ReadingProgressBarHeight: Dp = 4.dp

/**
 * How far through a book its reader is, drawn edge to edge along the bottom of
 * its cover.
 *
 * The same bar on a library card and on the detail screen, because they are the
 * same fact about the same book seen from two distances. Callers place it inside
 * whatever clips the cover, so it stops exactly where the rounded corners do.
 *
 * Painted rather than laid out. The filled part is a fraction of the bar's real
 * width, and every way of expressing that through layout - a nested
 * `fillMaxWidth(fraction)`, a weighted row - measures its fraction against
 * constraints a parent has already fixed, and lands the fill somewhere other
 * than against the leading edge.
 *
 * The track is drawn in the theme's surface rather than in a black wash: this
 * sits directly on artwork that may be any colour at all, and a dark track is
 * invisible over a dark cover just when the bar is most needed.
 */
@Composable
fun ReadingProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = ReadingProgressBarHeight
) {
    val trackColor = MaterialTheme.colorScheme.surface.copy(alpha = TrackAlpha)
    val fillColor = MaterialTheme.colorScheme.primary
    val filled = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                drawRect(color = trackColor)
                drawRect(
                    color = fillColor,
                    size = Size(width = size.width * filled, height = size.height)
                )
            }
    )
}

private const val TrackAlpha = 0.55f
