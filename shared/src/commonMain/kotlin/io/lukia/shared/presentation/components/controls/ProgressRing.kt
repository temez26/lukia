package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** How wide the ring is, in sp so it grows with the label inside it at a large system font size. */
private val DefaultRingSize: TextUnit = 52.sp

/** The ring's thickness against its width. */
private const val StrokeShare = 0.1f

/** Where the ring starts filling: twelve o'clock. */
private const val StartAngle = -90f

private const val FullTurn = 360f

/**
 * How far through something is, as a ring that fills clockwise from the top
 * with [label] - the percentage - set in the middle of it.
 *
 * The label is the ring's own text rather than a line beside it, so the figure
 * and the shape cannot be read apart. Sized in sp, like the label, so "100%"
 * still fits inside at the largest font size.
 */
@Composable
fun ProgressRing(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: TextUnit = DefaultRingSize,
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.primary
    val swept = fraction.coerceIn(0f, 1f) * FullTurn
    val diameter = with(LocalDensity.current) { size.toDp() }

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(diameter)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * StrokeShare
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = FullTurn,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = fill,
                startAngle = StartAngle,
                sweepAngle = swept,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
