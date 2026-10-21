package io.lukia.shared.presentation.components.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap

// Glyphs for the facts about a book file - what it is, how big, in what
// language, from when - set small beside the words they stand for.

/** A page with its corner folded over: the kind of file a book is. */
@Composable
fun FileGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val left = size.width * 0.22f
        val right = size.width * 0.78f
        val top = size.height * 0.12f
        val bottom = size.height * 0.88f
        val fold = size.width * 0.22f

        val page = Path().apply {
            moveTo(left, top)
            lineTo(right - fold, top)
            lineTo(right, top + fold)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }
        val corner = Path().apply {
            moveTo(right - fold, top)
            lineTo(right - fold, top + fold)
            lineTo(right, top + fold)
        }

        drawPath(path = page, color = tint, style = glyphStroke())
        drawPath(path = corner, color = tint, style = glyphStroke())
    }
}

/** A stack of discs, as storage is drawn everywhere: how much room the file takes. */
@Composable
fun StorageGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val left = size.width * 0.18f
        val width = size.width * 0.64f
        val rim = size.height * 0.20f
        val top = size.height * 0.12f
        val bottom = size.height * 0.88f

        drawOval(color = tint, topLeft = Offset(left, top), size = Size(width, rim), style = glyphStroke())

        // The lower edge of each disc under the top one: half an ellipse, facing down.
        for (edge in listOf(0.5f, 1f)) {
            val discTop = top + (bottom - top - rim) * edge
            drawArc(
                color = tint,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left, discTop),
                size = Size(width, rim),
                style = glyphStroke(),
            )
        }

        drawLine(tint, Offset(left, top + rim / 2), Offset(left, bottom - rim / 2), glyphStrokeWidth(), StrokeCap.Round)
        drawLine(
            tint,
            Offset(left + width, top + rim / 2),
            Offset(left + width, bottom - rim / 2),
            glyphStrokeWidth(),
            StrokeCap.Round,
        )
    }
}

/** A globe: the language a book is written in. */
@Composable
fun GlobeGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.38f
        val middle = Offset(size.width / 2, size.height / 2)

        drawCircle(color = tint, radius = radius, center = middle, style = glyphStroke())
        drawOval(
            color = tint,
            topLeft = Offset(middle.x - radius * 0.45f, middle.y - radius),
            size = Size(radius * 0.9f, radius * 2),
            style = glyphStroke(GlyphWeight.Light),
        )
        drawLine(
            color = tint,
            start = Offset(middle.x - radius, middle.y),
            end = Offset(middle.x + radius, middle.y),
            strokeWidth = glyphStrokeWidth(GlyphWeight.Light),
        )
    }
}

/** A calendar page with its two rings: the year a book was published. */
@Composable
fun CalendarGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val left = size.width * 0.14f
        val top = size.height * 0.20f
        val pageWidth = size.width * 0.72f

        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(pageWidth, size.height * 0.66f),
            cornerRadius = CornerRadius(size.width * 0.10f),
            style = glyphStroke(),
        )
        drawLine(
            color = tint,
            start = Offset(left, size.height * 0.42f),
            end = Offset(left + pageWidth, size.height * 0.42f),
            strokeWidth = glyphStrokeWidth(GlyphWeight.Light),
        )

        for (ring in listOf(0.36f, 0.64f)) {
            drawLine(
                color = tint,
                start = Offset(size.width * ring, size.height * 0.10f),
                end = Offset(size.width * ring, size.height * 0.28f),
                strokeWidth = glyphStrokeWidth(),
                cap = StrokeCap.Round,
            )
        }
    }
}
