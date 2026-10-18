package io.lukia.features.reader.presentation.components.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.resources.Res
import io.lukia.resources.reader_close_book
import io.lukia.resources.reader_close_the_book
import io.lukia.resources.shortcut_close_book_keys
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.controls.ShortcutTooltip
import io.lukia.shared.presentation.components.icons.BackChevron
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/**
 * How tall the toolbar is, and so how much room the page below it has to leave.
 *
 * A function rather than a constant because the controls in it are sized for
 * whatever is doing the pointing: a bar built for a thumb is half as tall again
 * as one built for a mouse, and the reader would otherwise reserve the wrong
 * amount of room for it on one of the two.
 */
@Composable
internal fun readerToolbarHeight(): Dp = LocalLukiaMetrics.current.iconButton

/**
 * The reader's chrome: leaving the book, where you are in it, and how it reads.
 *
 * A capsule that takes its own row rather than floating over the page: nothing
 * passes underneath it, so the page is never partly covered by the thing telling
 * you which page it is, and with no moving content behind it the translucency
 * costs nothing to draw.
 *
 * [GlassSurface] resolves per platform - translucent and specular on Apple
 * platforms, a flatter elevated surface elsewhere - so the bar looks native on
 * iOS without handing the other platforms an iOS look. See `GlassStyle` for why
 * it is a drawn approximation rather than a system material.
 *
 * [title] is drawn only where there is room. Three controls at arm's length is
 * the whole bar on a phone; a desktop window is wide enough that the same three
 * pushed to the extremes read as a bar with a hole in it - and wide enough to
 * say which book is open, which a desktop window is expected to do.
 */
@Composable
internal fun ReaderToolbar(
    onBack: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    /**
     * Whether the bar is the top row of a panel that has opened out of it - see
     * `TopSheet`. The panel's surface is the bar's then, and a capsule of its own
     * drawn on top would split the one block back into two.
     */
    merged: Boolean = false,
    /**
     * How far through the book the reader is, drawn as a line along the bar's
     * bottom edge; null for no line. Read when drawn, so turning a page redraws
     * the line rather than recomposing the bar.
     */
    progress: (() -> Float)? = null,
    centerControl: @Composable () -> Unit,
    endControl: @Composable () -> Unit,
) {
    val wide = isWideWindow

    // Wide and narrow are two different shapes of tree - three weighted tracks
    // against three controls in a row - and the same two slots are emitted from
    // both. Made movable so a window dragged across the threshold carries their
    // state with them rather than rebuilding them: the end control holds the
    // reader's open settings panel, and a resize would otherwise shut it.
    val center = remember(centerControl) { movableContentOf(centerControl) }
    val end = remember(endControl) { movableContentOf(endControl) }

    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = ProgressTrackAlpha)
    val fill = MaterialTheme.colorScheme.primary

    BarSurface(merged = merged, modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier.drawWithContent {
                drawContent()
                progress?.let { drawProgressLine(it(), track, fill) }
            },
        ) {
            // What the middle of the bar may take, whatever is written in it.
            //
            // A Row measures unweighted children first, in order, handing each
            // everything still unclaimed - so the page indicator, as wide as the
            // chapter name inside it, could take the whole bar and leave the
            // settings and translation controls measured at nothing. Not a bar
            // that overflows: a bar whose controls silently disappear, which is
            // what a long chapter title did.
            //
            // Half is enough for a chapter to be worth naming and leaves the
            // other half for the controls either side, which is where the eye
            // looks for them.
            val centerWidth = maxWidth * CENTER_SHARE

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (wide) Arrangement.Start else Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    // A floor, so the bar grows rather than clipping its own
                    // page number at the largest OS font scale. What the page
                    // below reserves for it stays [chromeHeight] - a bar that
                    // has grown overlaps the top of the first sheet by the
                    // difference, which is the better of the two failures.
                    .heightIn(min = readerToolbarHeight()),
            ) {
                if (wide) {
                    // Three tracks rather than three controls in a row: the outer
                    // two share what is left over evenly, which is what keeps the
                    // page indicator in the middle of the bar however long the
                    // title beside it runs.
                    TitleTrack(onBack = onBack, title = title)

                    Box(modifier = Modifier.widthIn(max = centerWidth)) { center() }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        end()
                    }
                } else {
                    CloseBookButton(onBack)
                    Box(modifier = Modifier.widthIn(max = centerWidth)) { center() }
                    end()
                }
            }
        }
    }
}

/**
 * A capsule, because this is the one piece of chrome in the app that genuinely
 * floats free of everything around it - unless it is [merged] into the panel it
 * opened, which draws the surface for both.
 */
@Composable
private fun BarSurface(
    merged: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (merged) {
        Box(modifier = modifier, content = content)
    } else {
        GlassSurface(shape = MaterialTheme.shapes.large, modifier = modifier, content = content)
    }
}

/**
 * The reading progress along the bar's foot, where the bottom bar used to state
 * it on its own: one bar for where the reader is and how far through.
 *
 * Inset from the capsule's ends and rounded: run edge to edge, its start was
 * hidden in the capsule's corner and a book a tenth read showed as a smudge.
 */
private fun DrawScope.drawProgressLine(fraction: Float, track: Color, fill: Color) {
    val height = ProgressLineHeight.toPx()
    val inset = ProgressLineInset.toPx()
    val top = size.height - height - ProgressLineLift.toPx()
    val width = size.width - inset * 2
    val radius = CornerRadius(height / 2)

    drawRoundRect(color = track, topLeft = Offset(inset, top), size = Size(width, height), cornerRadius = radius)
    drawRoundRect(
        color = fill,
        topLeft = Offset(inset, top),
        size = Size(width * fraction.coerceIn(0f, 1f), height),
        cornerRadius = radius,
    )
}

/** Thick enough to read at a glance, thin enough to stay the bar's edge. */
private val ProgressLineHeight: Dp = 3.dp

/** In from the capsule's rounded ends, so the line starts where it can be seen. */
private val ProgressLineInset: Dp = 18.dp

/** Up off the capsule's edge, so the line is inside the bar rather than its outline. */
private val ProgressLineLift: Dp = 3.dp

/** How much of the ink the unread part of the book is drawn in. */
private const val ProgressTrackAlpha = 0.12f

/** How much of the bar the page indicator may take before it starts ellipsising. */
private const val CENTER_SHARE = 0.5f

/** Leaving the book, which is the one control every window has room for. */
@Composable
private fun CloseBookButton(onBack: () -> Unit) {
    ShortcutTooltip(
        description = stringResource(Res.string.reader_close_the_book),
        shortcut = stringResource(Res.string.shortcut_close_book_keys),
    ) {
        LukiaIconButton(
            label = stringResource(Res.string.reader_close_book),
            onClick = onBack,
        ) { tint, glyph ->
            BackChevron(tint = tint, modifier = glyph)
        }
    }
}

/**
 * The way out and the name of what is open, which is the left-hand track of a
 * bar wide enough to have tracks.
 *
 * The title gives way before the button does - it is the part that can run to a
 * whole line, and the part a reader can do without.
 */
@Composable
private fun RowScope.TitleTrack(onBack: () -> Unit, title: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
    ) {
        CloseBookButton(onBack)

        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = Space.xs, end = Space.lg),
            )
        }
    }
}
