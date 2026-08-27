package io.lukia.shared.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.LocalGlassStyle

/**
 * What a glass surface is being asked to hold, which is what decides how far its
 * translucency can be taken. See [io.lukia.shared.presentation.theme.GlassStyle].
 */
enum class GlassTone {
    /** A bar or a button: a glyph, a word, nothing to dwell on. */
    Chrome,

    /** A panel or a menu: rows of text, over content that is still there behind. */
    Panel,
}

/**
 * Translucent floating chrome. See [io.lukia.shared.presentation.theme.GlassStyle]
 * for why this is a hand-drawn approximation rather than a real backdrop blur.
 *
 * The look is built from three layers: a translucent fill, a specular highlight
 * that fades from the top edge, and a hairline border that picks up the outline
 * colour towards the bottom so the shape still reads on light backgrounds.
 */
@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.Chrome,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlassStyle.current
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline

    val fillAlpha = when (tone) {
        GlassTone.Chrome -> glass.chromeAlpha
        GlassTone.Panel -> glass.panelAlpha
    }

    // Remembered because these are rebuilt on every recomposition otherwise, and
    // this is the chrome the reader's page scrolls under - so "every
    // recomposition" is every frame of a scroll, for two allocations that only
    // change when the theme does.
    val fill = remember(surface, fillAlpha) {
        Brush.verticalGradient(
            listOf(
                surface.copy(alpha = fillAlpha),
                surface.copy(alpha = (fillAlpha + FillDepth).coerceAtMost(1f)),
            ),
        )
    }

    val edge = remember(outline, glass) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = glass.highlightAlpha),
            HighlightFalloff to outline.copy(alpha = glass.borderAlpha * BorderFade),
            1f to outline.copy(alpha = glass.borderAlpha),
        )
    }

    Box(
        modifier = modifier
            .shadow(elevation = glass.shadowElevation, shape = shape, clip = false)
            .clip(shape)
            .background(fill)
            .border(width = HairlineWidth, brush = edge, shape = shape),
        content = content,
    )
}

/** How much darker the bottom of the fill is than the top. */
private const val FillDepth = 0.08f

/** Where down the edge the specular highlight has given way to the outline. */
private const val HighlightFalloff = 0.55f

/** How far the outline is faded where the two meet. */
private const val BorderFade = 0.4f
