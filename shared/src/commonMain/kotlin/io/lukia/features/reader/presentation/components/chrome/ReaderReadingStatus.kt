package io.lukia.features.reader.presentation.components.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.presentation.percentThrough
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.bottomChromeIn
import io.lukia.shared.presentation.theme.bottomChromeOut

/** Thick enough to see against a page, thin enough not to be a control. */
private val LineHeight: Dp = 3.dp

/** How much of the theme's ink the unread part of the book is drawn in. */
private const val TrackAlpha = 0.18f

/** Room for "100%" set in the label face, so nothing shuffles as the figure grows a digit. */
private val ShareWidth: Dp = 40.dp

/**
 * How far through the book the reader is: a line along the bottom edge, and the
 * share of the book behind them written above it.
 *
 * Shown with the toolbar and only with it. The page is what the reader came for,
 * and a book laid out edge to edge with nothing on it but words is the whole
 * point of putting the chrome away - a line and a figure left burning along the
 * bottom are two things still asking to be looked at. Both ends of the chrome
 * therefore arrive and leave together, which also means one gesture governs
 * everything drawn over the page rather than one thing appearing as another
 * goes.
 *
 * The percentage is the fact the toolbar does not carry. Its indicator says
 * "12 / 348", which answers where the reader is; a share answers how much is
 * left, and a reader deciding whether to finish a chapter tonight is asking the
 * second question. The two forms sit at opposite edges rather than in one
 * control because neither is worth crossing the page to read.
 *
 * At the end of the row rather than centred, where the notice that can appear
 * over the same edge sits - see [ReaderNotice].
 *
 * The line is not a control. There is a scrubber in the contents panel for going
 * somewhere, and it is reached from the toolbar this appears alongside.
 *
 * The page is read here and nowhere else, so turning one redraws this rather
 * than the reader around it, and the `derivedStateOf` keeps that to once per
 * page rather than once per scrolled pixel.
 */
@Composable
internal fun ReaderReadingStatus(
    listState: LazyListState,
    /** How long the book is; zero while it is still being laid out. */
    pageCount: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val position by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    AnimatedVisibility(
        visible = visible && pageCount > 0,
        // Along the bottom edge as the toolbar comes down the top one, so the
        // two read as one piece of chrome opening rather than as two things
        // that happen to appear at once.
        enter = bottomChromeIn(),
        exit = bottomChromeOut(),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ReadShare(
                percent = percentThrough(position, pageCount),
                modifier = Modifier.padding(
                    horizontal = LocalLukiaDimens.current.screenPadding,
                    vertical = Space.sm,
                ),
            )

            // From the page rather than from the rounded figure above it, so the
            // line still creeps a book's length in single pages instead of
            // stepping whenever the percentage gains a whole point.
            ProgressLine(fraction = position.toFloat() / pageCount)
        }
    }
}

/**
 * The share of the book behind the reader, as a figure.
 *
 * Wearing the same recessed fill as the toolbar's own page indicator, because it
 * is the same kind of thing said the other way round - and because a bare figure
 * laid straight over a paragraph is unreadable against half the pages in a book.
 *
 * Announced rather than hidden: unlike the line, this is a fact the toolbar does
 * not state anywhere. Not a live region, for the reason the page indicator is
 * not one either - it changes on every page, and a reader scrolling through a
 * chapter does not want each one read out.
 */
@Composable
private fun ReadShare(percent: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$percent%",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Centred in a width held for the longest figure, so the chip neither
        // grows nor leaves the digits adrift in it.
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LukiaAlpha.Fill))
            .padding(horizontal = Space.md, vertical = Space.xs)
            .widthIn(min = ShareWidth)
            .semantics { contentDescription = "$percent percent read" },
    )
}

/**
 * The same fact drawn, along the very bottom of the window.
 *
 * Drawn rather than laid out - `drawBehind` on one box, so a page turn repaints
 * three pixels and moves nothing.
 *
 * Silent to assistive technology: it says exactly what the figure above it says,
 * and once is enough.
 */
@Composable
private fun ProgressLine(fraction: Float) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha)
    val fill = MaterialTheme.colorScheme.primary
    val filled = fraction.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LineHeight)
            .drawBehind {
                drawRect(color = track)
                drawRect(color = fill, size = Size(size.width * filled, size.height))
            },
    )
}
