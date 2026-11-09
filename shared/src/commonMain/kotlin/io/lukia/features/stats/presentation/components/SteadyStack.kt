package io.lukia.features.stats.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

/**
 * Lays its children over one another, as tall as the tallest of them, and never
 * shorter than it has been at the same width.
 *
 * For content that changes in place above other content: whatever is shown next
 * may be shorter, but what lies below it doesn't move. Only a new width, as when
 * a window is resized, lets it settle at a new height.
 */
@Composable
internal fun SteadyStack(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Read and written while measuring only, so it is plain rather than state:
    // nothing composes from it.
    val tallest = remember { TallestAtWidth() }

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0

        if (width != tallest.width) {
            tallest.width = width
            tallest.height = 0
        }
        tallest.height = maxOf(tallest.height, height)

        layout(width, tallest.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEach { it.place(0, 0) }
        }
    }
}

private class TallestAtWidth {
    var width: Int = -1
    var height: Int = 0
}
