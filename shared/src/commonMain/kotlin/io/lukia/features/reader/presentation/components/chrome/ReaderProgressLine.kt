package io.lukia.features.reader.presentation.components.chrome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LukiaMotion

/** Thick enough to see against a page, thin enough not to be a control. */
private val LineHeight: Dp = 3.dp

/** How much of the theme's ink the unread part of the book is drawn in. */
private const val TrackAlpha = 0.18f

/**
 * How far through the book the reader is, as a line along the bottom edge, for
 * the whole time the toolbar is away.
 *
 * A phone puts the chrome away on the first scroll - see
 * [io.lukia.features.reader.presentation.screens.HideChromeWhileScrolling] -
 * which is what a reading app should do and which also takes away the one thing
 * on screen that said where in the book this page was. Getting it back means two
 * taps, and reading it means covering the page with the bar that carries it.
 *
 * So the position stays, in the only form that costs the page nothing: three
 * pixels along an edge nothing is set against. It is not a control - there is a
 * scrubber in the contents panel for going somewhere, and it is reached from the
 * bar this appears in place of.
 *
 * Deliberately silent to assistive technology. It says exactly what the page
 * indicator says, and a screen reader announcing the same fact twice as it
 * changes on every page is noise over the words being read.
 *
 * The page is read inside this composable and nowhere else, so turning a page
 * redraws this line rather than the reader around it - and the `derivedStateOf`
 * keeps that to once per page rather than once per scrolled pixel.
 */
@Composable
internal fun ReaderProgressLine(
    listState: LazyListState,
    /** How long the book is; zero while it is still being laid out. */
    pageCount: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val fraction by remember(listState, pageCount) {
        derivedStateOf {
            if (pageCount <= 0) 0f else (listState.firstVisibleItemIndex + 1f) / pageCount
        }
    }

    // Faded rather than removed, so the line arriving as the bar leaves reads as
    // the same fact changing form rather than as something new appearing.
    val shown by animateFloatAsState(
        targetValue = if (visible && pageCount > 0) 1f else 0f,
        animationSpec = LukiaMotion.quick(),
        label = "readerProgressLine",
    )

    if (shown == 0f) return

    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha * shown)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = shown)
    val filled = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LineHeight)
            .drawBehind {
                drawRect(color = track)
                drawRect(color = fill, size = Size(size.width * filled, size.height))
            },
    )
}
