package io.lukia.shared.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.AppPalette

// Each palette's corners. The same five steps as [LukiaShapes] in every one, so
// a component keeps its place in the family and only the curve changes.

/**
 * Paper's corners: soft and generous, so the app reads as something held rather
 * than operated. A field or a chip at [Shapes.small] comes out nearly a pill.
 */
private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Slate's corners: tight and even, the radii of a tool rather than a book. */
private val SlateShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

/**
 * Ledger's corners: square where the others are rounded, with just enough on
 * the larger surfaces that a floating panel does not read as a hole cut in the
 * page.
 */
private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

internal fun shapesFor(palette: AppPalette): Shapes = when (palette) {
    AppPalette.Crisp -> LukiaShapes
    AppPalette.Paper -> PaperShapes
    AppPalette.Slate -> SlateShapes
    AppPalette.Ledger -> LedgerShapes
}

/**
 * What an icon button is clipped to. A circle where the palette's corners are
 * soft; a rounded square in Slate and a square in Ledger, where a round back or
 * menu button was the one curve left on the page.
 *
 * A local rather than a slot in [Shapes], which has no "fully round" step.
 */
val LocalIconButtonShape = staticCompositionLocalOf<Shape> { CircleShape }

internal fun iconButtonShapeFor(palette: AppPalette): Shape = when (palette) {
    AppPalette.Crisp, AppPalette.Paper -> CircleShape
    AppPalette.Slate -> SlateShapes.large
    AppPalette.Ledger -> LedgerShapes.small
}
