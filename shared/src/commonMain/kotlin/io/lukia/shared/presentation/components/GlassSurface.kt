package io.lukia.shared.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.theme.LocalGlassStyle

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
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalGlassStyle.current
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .shadow(elevation = glass.shadowElevation, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        surface.copy(alpha = glass.surfaceAlpha),
                        surface.copy(alpha = (glass.surfaceAlpha + 0.08f).coerceAtMost(1f))
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = glass.highlightAlpha),
                    0.55f to outline.copy(alpha = glass.borderAlpha * 0.4f),
                    1f to outline.copy(alpha = glass.borderAlpha)
                ),
                shape = shape
            ),
        content = content
    )
}
