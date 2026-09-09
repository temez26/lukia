package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.lukia.resources.Res
import io.lukia.resources.reader_percent
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Thickness of the bar welded along the bottom edge of a cover.
 *
 * One thickness everywhere. A grid card is a third of the size of the detail
 * screen's cover, and the bar was once scaled down to match - which left three
 * pixels of it on a phone, over artwork of any colour, for the one thing on the
 * card that says the book is being read at all.
 */
val ReadingProgressBarHeight: Dp = 6.dp

/**
 * How far through a book its reader is, laid over the bottom of its cover: the
 * bar along the edge, the number in the corner.
 *
 * The two travel together because a cover showing one without the other says
 * less than it could - the bar reads at a glance and the number says which
 * glance it was. Callers place this inside whatever clips the cover, so both
 * stop exactly where the rounded corners do.
 */
@Composable
fun ReadingProgressOverlay(
    fraction: Float,
    modifier: Modifier = Modifier,
    /** Null only for a book whose length is unknown, which has no percentage. */
    percent: Int? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        ReadingProgressBar(
            fraction = fraction,
            modifier = Modifier.align(Alignment.BottomStart),
        )

        percent?.let {
            ReadingProgressBadge(
                percent = it,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // Sat clear of the bar rather than on top of it, so the two
                    // read as one mark rather than as a number with a stripe
                    // through it.
                    .padding(end = Space.sm, bottom = ReadingProgressBarHeight + Space.xs),
            )
        }
    }
}

/**
 * How far through a book its reader is, drawn edge to edge along the bottom of
 * its cover.
 *
 * The same bar on a library card and on the detail screen, because they are the
 * same fact about the same book seen from two distances. Callers place it inside
 * whatever clips the cover, so it stops exactly where the rounded corners do.
 *
 * Painted rather than laid out: the filled part is a fraction of the bar's real
 * width, and every layout way of saying that - a nested `fillMaxWidth(fraction)`,
 * a weighted row - measures against constraints a parent already fixed and lands
 * the fill somewhere other than the leading edge.
 *
 * The track is nearly opaque, in the theme's surface rather than a black wash:
 * this sits directly on artwork of any colour, and a translucent track picks up
 * whatever is behind it just when the bar most needs to be read against it.
 */
@Composable
fun ReadingProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surface.copy(alpha = LukiaAlpha.OverArtwork)
    val fillColor = MaterialTheme.colorScheme.primary
    val filled = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ReadingProgressBarHeight)
            .drawBehind {
                drawRect(color = trackColor)

                // Offset in a mirrored layout rather than drawn from the
                // canvas's left in both. A reader whose device is set to Arabic
                // reads the page numbers beside this from the right, and a bar
                // that went on filling the other way showed progress running
                // backwards. `DrawScope` knows the direction; nothing has to be
                // passed in for it.
                val start = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width * (1f - filled)
                } else {
                    0f
                }

                drawRect(
                    color = fillColor,
                    topLeft = Offset(x = start, y = 0f),
                    size = Size(width = size.width * filled, height = size.height),
                )
            },
    )
}

/**
 * The same progress as a number, for the corner of the cover the bar runs along.
 *
 * A bar says "part way" and nothing more precise; on a card the size of a
 * thumbnail it cannot say the difference between a book half read and one nearly
 * finished. The number is what makes it a status rather than a decoration.
 *
 * Ink on a frosted chip rather than anything coloured, and the same backing the
 * favourite heart sits on: a shelf is a wall of artwork, and a coloured chip on
 * every cover of it turns reading progress into decoration. The accent is spent
 * on the bar instead, where it means one thing and is read as a length.
 */
@Composable
fun ReadingProgressBadge(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = LukiaAlpha.OverArtwork))
            .padding(horizontal = Space.sm, vertical = Space.xxs),
    ) {
        Text(
            text = stringResource(Res.string.reader_percent, percent),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
