package io.lukia.shared.presentation.components

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How heavily the app's glyphs are stroked.
 *
 * Every mark in Lukia is drawn rather than imported, and every one of them lays
 * its geometry out as fractions of the box it is handed - so a glyph already
 * scales. Its stroke did not: each was written as a literal dp at the call site,
 * which is how one icon family came to be drawn at 1.6, 1.8 and 2.0dp, with two
 * of the comments claiming a match that the numbers did not make. `SearchGlyph`
 * said it was "drawn at the same stroke weight as the chevrons" at 1.8 against
 * their 2.0; `DisclosureChevron` said it was drawn at `BackChevron`'s weight at
 * 1.6 against the same 2.0.
 *
 * A fixed stroke is also a different mark at every size the app draws it. These
 * glyphs are asked for at 15dp under a pointer, 18 and 20dp under a thumb, 24dp
 * on the tab bar and larger again on an empty state - so a single 2.0dp line is
 * an eighth of the small one and a twelfth of the large, and the same icon reads
 * as bold in a menu and as hairline on a page. Optical size is the thing being
 * fixed here: the stroke is a fraction of what the glyph is actually drawn at,
 * so the family holds one weight across every slot it appears in.
 *
 * The fraction is clamped at both ends. Below the floor a mark starts
 * disappearing into the display's own pixel grid; above the ceiling a glyph the
 * size of an empty-state illustration turns into a woodcut.
 */
enum class GlyphWeight(internal val ratio: Float) {

    /**
     * The family weight, and what almost everything is drawn at. Set so the
     * common 20dp slot lands on the 2.0dp the set had already settled on.
     */
    Regular(RegularRatio),

    /**
     * A step lighter, for the two places where the family weight is genuinely
     * wrong rather than merely inherited.
     *
     * The tab bar is one: its marks repeat across the bottom of every screen and
     * at full weight three of them read as a row of blobs. A rule drawn *inside*
     * another glyph is the other - the untranslated line inside the translation
     * mark's page frame is lighter than the frame around it on purpose, and that
     * contrast is what the glyph is made of.
     */
    Light(LightRatio),
}

/** Set so the common 20dp slot lands on the 2.0dp the set had already settled on. */
private const val RegularRatio = 0.100f

private const val LightRatio = 0.085f

/** Thin enough to vanish into the pixel grid below this. */
private val MinStroke: Dp = 1.25.dp

/** ...and heavy enough to read as a woodcut above it. */
private val MaxStroke: Dp = 2.4.dp

/**
 * How heavily a glyph drawn at [glyphSize] is stroked.
 *
 * Separated from the drawing so the family's weight can be asserted on directly
 * rather than inferred from a rendered canvas.
 */
internal fun glyphStrokeFor(glyphSize: Dp, weight: GlyphWeight = GlyphWeight.Regular): Dp =
    (glyphSize * weight.ratio).coerceIn(MinStroke, MaxStroke)

/**
 * How wide to stroke a glyph being drawn into this scope, in pixels.
 *
 * For the calls that take a bare width - `drawLine`, `drawCircle` - where
 * [glyphStroke] would mean building a [Stroke] to read one field off it.
 */
internal fun DrawScope.glyphStrokeWidth(weight: GlyphWeight = GlyphWeight.Regular): Float =
    glyphStrokeFor(size.minDimension.toDp(), weight).toPx()

/**
 * The stroke a glyph is drawn with.
 *
 * Round throughout, which is the whole set's join: these are Apple-proportioned
 * marks rather than Material's, and a mitred corner on a 20dp glyph is a visible
 * thickening exactly where the eye reads the shape.
 */
internal fun DrawScope.glyphStroke(
    weight: GlyphWeight = GlyphWeight.Regular,
    cap: StrokeCap = StrokeCap.Round,
    join: StrokeJoin = StrokeJoin.Round,
): Stroke = Stroke(
    width = glyphStrokeWidth(weight),
    cap = cap,
    join = join,
)
