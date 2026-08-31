package io.lukia.shared.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

// Chevrons that point the way out of a screen or deeper into a row.

/**
 * Drawn rather than imported: this project does not depend on the Material icons
 * artifact, and a two-segment stroke matches Apple's thin chevron more closely
 * than the heavier Material back arrow anyway.
 */
@Composable
fun BackChevron(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val chevron = Path().apply {
            moveTo(size.width * 0.62f, size.height * 0.20f)
            lineTo(size.width * 0.34f, size.height * 0.50f)
            lineTo(size.width * 0.62f, size.height * 0.80f)
        }

        drawPath(path = chevron, color = tint, style = glyphStroke())
    }
}

/**
 * A downward chevron, marking a control that opens something beneath it. Drawn
 * at the same weight as [BackChevron] so the two read as one family - which for
 * as long as both were written as literals here they were not: this one was
 * 1.6dp against the other's 2.0.
 */
@Composable
fun DisclosureChevron(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val chevron = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.35f)
            lineTo(size.width * 0.50f, size.height * 0.68f)
            lineTo(size.width * 0.85f, size.height * 0.35f)
        }

        drawPath(path = chevron, color = tint, style = glyphStroke())
    }
}
