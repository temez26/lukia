package io.lukia.features.reader.presentation.components.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.presentation.percentThrough
import io.lukia.resources.Res
import io.lukia.resources.reader_percent
import io.lukia.resources.reader_percent_read
import io.lukia.resources.reader_percent_start
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Thick enough to read as a bar rather than a rule, inside chrome of its own. */
private val TrackHeight: Dp = 4.dp

/** How much of the theme's ink the unread part of the book is drawn in. */
private const val TrackAlpha = 0.18f

/** Room for "100%", so the track does not shorten as the figure gains a digit. */
private val ShareWidth: Dp = 34.dp

/**
 * How far through the book the reader is, as a bar of its own along the bottom
 * of the window: where the book starts, how far along it they are, and the share
 * of it behind them.
 *
 * One bar rather than a figure floating over a hairline: the two were the same
 * fact drawn twice at opposite scales. Set end to end, the "0" and the
 * percentage are the ends of one scale and the track between them reads it - a
 * part-filled bar with bare ends only says "some".
 *
 * Wears the toolbar's glass capsule at the same measure and inset - see
 * [ReaderToolbar]; they are two halves of one piece of chrome. Not a control:
 * the scrubber for going somewhere is in the contents panel.
 *
 * The page is read here and nowhere else, so turning one redraws this bar rather
 * than the reader around it, and `derivedStateOf` keeps that to once per page
 * rather than once per scrolled pixel.
 */
@Composable
internal fun ReaderReadingStatus(
    listState: LazyListState,
    /** How long the book is; zero while it is still being laid out. */
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val position by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    val percent = percentThrough(position, pageCount)

    // Read before the modifier chain: `semantics` is not a composable scope.
    val spoken = stringResource(Res.string.reader_percent_read, percent)

    GlassSurface(
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md)
                // One statement rather than three. Read out separately, a bar
                // between "0" and "42%" is two numbers with nothing joining
                // them; announced as a whole it is the sentence they add up to.
                //
                // Deliberately not a live region: this changes on every page,
                // and a reader scrolling through a chapter does not want each
                // one read out. The pages themselves carry the position.
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                },
        ) {
            Marker(text = stringResource(Res.string.reader_percent_start), emphasised = false)

            ProgressTrack(
                // From the page rather than from the rounded figure beside it, so
                // the fill creeps a book's length in single pages instead of
                // stepping whenever the percentage gains a whole point.
                fraction = if (pageCount <= 0) 0f else position.toFloat() / pageCount,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.lg),
            )

            Marker(text = stringResource(Res.string.reader_percent, percent), emphasised = true)
        }
    }
}

/**
 * One end of the scale.
 *
 * The zero is the quieter of the two on purpose: it never changes and is there to
 * be measured against rather than read, while the figure opposite it is the thing
 * the reader actually came to the bar for.
 *
 * Both are held to the width of the longest figure either can hold, so the track
 * between them keeps one length for the whole book instead of shortening by a
 * character at 10% and again at 100%.
 */
@Composable
private fun Marker(text: String, emphasised: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
        color = if (emphasised) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = if (emphasised) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        modifier = Modifier.widthIn(min = ShareWidth),
    )
}

/**
 * The reading itself, between the two ends.
 *
 * Painted rather than laid out. The filled part is a fraction of the track's real
 * width, and expressing that through layout - a nested `fillMaxWidth(fraction)`,
 * a weighted row - measures the fraction against constraints a parent has already
 * fixed and lands the fill somewhere other than against the leading edge. The
 * same reason `ReadingProgressBar` is drawn.
 *
 * Rounded, unlike the rule this replaced: that one was welded to the window's own
 * edge and took its shape from it, and this one is a bar sitting inside a
 * capsule.
 */
@Composable
private fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha)
    val fill = MaterialTheme.colorScheme.primary
    val filled = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(TrackHeight)
            .clip(CircleShape)
            .drawBehind {
                drawRect(color = track)

                // Filled from the leading edge rather than from the canvas's
                // left - see `ReadingProgressBar`, which had the same bug.
                val start = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width * (1f - filled)
                } else {
                    0f
                }

                drawRect(
                    color = fill,
                    topLeft = Offset(x = start, y = 0f),
                    size = Size(size.width * filled, size.height),
                )
            },
    )
}
