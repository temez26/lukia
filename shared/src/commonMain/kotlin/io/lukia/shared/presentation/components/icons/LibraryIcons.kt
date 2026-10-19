package io.lukia.shared.presentation.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer

// Glyphs for the things a library holds - books, folders, and what is done to them.

/**
 * A folder outline for the library-path action. Drawn as a single stroked path
 * so it sits at the same visual weight as [PlusGlyph] beside it.
 */
@Composable
fun FolderGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val tabTop = size.height * 0.22f
        val bodyTop = size.height * 0.36f
        val bottom = size.height * 0.80f

        val folder = Path().apply {
            moveTo(left, bodyTop)
            lineTo(left, tabTop)
            lineTo(left + size.width * 0.26f, tabTop)
            // The short diagonal is what reads as a folder tab rather than a
            // plain rectangle at this size.
            lineTo(left + size.width * 0.36f, bodyTop)
            lineTo(right, bodyTop)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }

        drawPath(path = folder, color = tint, style = glyphStroke())
    }
}

/**
 * The favourite mark. One glyph serves both states: stroked while the book is
 * not favourited, [filled] once it is, so the badge and the menu row can share a
 * single drawing.
 */
@Composable
fun HeartGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val heart = Path().apply {
            // Drawn from the bottom tip up each side, so both lobes stay
            // symmetrical about the vertical centre line.
            moveTo(w * 0.50f, h * 0.86f)
            cubicTo(w * 0.04f, h * 0.53f, w * 0.07f, h * 0.16f, w * 0.29f, h * 0.16f)
            cubicTo(w * 0.41f, h * 0.16f, w * 0.50f, h * 0.26f, w * 0.50f, h * 0.32f)
            cubicTo(w * 0.50f, h * 0.26f, w * 0.59f, h * 0.16f, w * 0.71f, h * 0.16f)
            cubicTo(w * 0.93f, h * 0.16f, w * 0.96f, h * 0.53f, w * 0.50f, h * 0.86f)
            close()
        }

        if (filled) {
            drawPath(path = heart, color = tint)
        } else {
            drawPath(path = heart, color = tint, style = glyphStroke())
        }
    }
}

/**
 * An open book, for the control that turns a cover into the way into it - and
 * for what is done to a book's reading, such as marking it unread.
 *
 * Drawn as the reading apps draw theirs: two pages meeting at a spine, their top
 * edges dipping into it as an open book's do, outlined at the family's weight so
 * it sits with the other marks. A play triangle would be the obvious mark and
 * the wrong one: it is the idiom of something that runs while you watch.
 */
@Composable
fun OpenBookGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val spine = w * 0.5f

        fun page(outer: Float): Path = Path().apply {
            // Up the spine, out along the top edge, down the outer edge, and back
            // along the foot - each edge sagging towards the spine, where the
            // pages are held.
            moveTo(spine, h * 0.30f)
            cubicTo(spine, h * 0.30f, (spine + outer) / 2, h * 0.16f, outer, h * 0.22f)
            lineTo(outer, h * 0.74f)
            cubicTo((spine + outer) / 2, h * 0.75f, spine, h * 0.80f, spine, h * 0.82f)
            close()
        }

        val stroke = glyphStroke(GlyphWeight.Regular)

        drawPath(path = page(outer = w * 0.08f), color = tint, style = stroke)
        drawPath(path = page(outer = w * 0.92f), color = tint, style = stroke)
    }
}

/**
 * A closed book wearing a dot: marking a book unread, in the mark the mail and
 * message apps use for "unread". The open book means reading it; this is a
 * different thing done to it, and borrowing that glyph said "open" for an
 * action that throws the reader's place away.
 */
@Composable
fun UnreadBookGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Drawn into a layer of its own, so a ring can be cut out around the dot
    // where it overlaps the book rather than painted in a guessed background.
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width
        val h = size.height
        val stroke = glyphStroke(GlyphWeight.Regular)

        val book = Path().apply {
            addRoundRect(
                RoundRect(
                    left = w * 0.20f,
                    top = h * 0.14f,
                    right = w * 0.74f,
                    bottom = h * 0.86f,
                    cornerRadius = CornerRadius(w * 0.06f),
                ),
            )
        }
        drawPath(path = book, color = tint, style = stroke)

        // The spine, a page's width in from the left edge.
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, h * 0.16f),
            end = Offset(w * 0.32f, h * 0.84f),
            strokeWidth = stroke.width,
        )

        val dot = Offset(w * 0.76f, h * 0.22f)
        drawCircle(color = tint, radius = w * 0.20f, center = dot, blendMode = BlendMode.Clear)
        drawCircle(color = tint, radius = w * 0.13f, center = dot)
    }
}

/**
 * A plus: adding a book to the library, on the empty state and on the import
 * action. Two crossed strokes rather than a glyph of its own, "add" being the
 * one idea in the set every reader reads without being told.
 *
 * Turned 45 degrees it is also the app's close mark - see [OverlayPanel] - one
 * drawing serving "put this here" and "take this away" rather than two marks
 * kept in step by hand.
 */
@Composable
fun PlusGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = glyphStrokeWidth()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val arm = size.minDimension * 0.30f

        drawLine(
            color = tint,
            start = Offset(centerX - arm, centerY),
            end = Offset(centerX + arm, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(centerX, centerY - arm),
            end = Offset(centerX, centerY + arm),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * How the shelf is laid out, drawn as what it currently is: four panes for a
 * grid of covers, stacked lines for a list of rows.
 *
 * The icon shows the state rather than what pressing would change it to. A
 * control that draws its own opposite reads as a claim about the screen and is
 * wrong half the time somebody glances at it.
 */
@Composable
fun LayoutGlyph(
    tint: Color,
    list: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = glyphStroke()

        val path = Path().apply {
            if (list) {
                listOf(0.26f, 0.5f, 0.74f).forEach { y ->
                    moveTo(w * 0.18f, h * y)
                    lineTo(w * 0.82f, h * y)
                }
            } else {
                listOf(0.20f to 0.20f, 0.56f to 0.20f, 0.20f to 0.56f, 0.56f to 0.56f)
                    .forEach { (x, y) ->
                        addRect(
                            Rect(
                                offset = Offset(w * x, h * y),
                                size = Size(w * 0.24f, h * 0.24f),
                            ),
                        )
                    }
            }
        }

        drawPath(path = path, color = tint, style = stroke)
    }
}

/**
 * Two sheets, one behind the other, for the same book on the shelf twice.
 *
 * The back sheet is drawn only as the two edges that clear the front one -
 * a whole rectangle behind another reads as a frame at this size, and the
 * corner peeking out is what says "there is a second one of these".
 */
@Composable
fun CopyGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = glyphStroke()

        val behind = Path().apply {
            moveTo(w * 0.30f, h * 0.22f)
            lineTo(w * 0.74f, h * 0.22f)
            lineTo(w * 0.74f, h * 0.66f)
        }

        val front = Path().apply {
            moveTo(w * 0.26f, h * 0.34f)
            lineTo(w * 0.62f, h * 0.34f)
            lineTo(w * 0.62f, h * 0.78f)
            lineTo(w * 0.26f, h * 0.78f)
            close()
        }

        drawPath(path = behind, color = tint, style = stroke)
        drawPath(path = front, color = tint, style = stroke)
    }
}

/** A cloud: the book is in the sync server's shared library. */
@Composable
fun CloudGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val cloud = Path().apply {
            moveTo(w * 0.28f, h * 0.76f)
            arcTo(Rect(w * 0.08f, h * 0.46f, w * 0.38f, h * 0.76f), 90f, 180f, false)
            arcTo(Rect(w * 0.24f, h * 0.24f, w * 0.68f, h * 0.68f), 205f, 150f, false)
            arcTo(Rect(w * 0.56f, h * 0.42f, w * 0.90f, h * 0.76f), 290f, 160f, false)
            close()
        }

        drawPath(path = cloud, color = tint, style = glyphStroke())
    }
}

/**
 * A phone in outline: the book is on this device. Drawn at [CloudGlyph]'s weight
 * so the two read as a pair when a book is in both places.
 */
@Composable
fun DeviceGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = glyphStroke(cap = StrokeCap.Round)

        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.28f, h * 0.14f),
            size = Size(w * 0.44f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = stroke,
        )

        drawLine(
            color = tint,
            start = Offset(w * 0.44f, h * 0.74f),
            end = Offset(w * 0.56f, h * 0.74f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}
