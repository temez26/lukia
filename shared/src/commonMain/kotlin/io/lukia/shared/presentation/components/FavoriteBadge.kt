package io.lukia.shared.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The filled heart shown over a favourited book's cover.
 *
 * Decorative: the state is already announced by the menu row that toggles it, so
 * the badge stays out of the accessibility tree.
 */
@Composable
fun FavoriteBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    AnimatedVisibility(
        visible = visible,
        // The pop is the feedback for a toggle made from a menu that has already
        // closed over the cover.
        enter = fadeIn(tween(durationMillis = 160)) +
            scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                initialScale = 0.5f
            ),
        exit = fadeOut(tween(durationMillis = 120)) +
            scaleOut(tween(durationMillis = 120), targetScale = 0.5f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            HeartGlyph(
                tint = MaterialTheme.colorScheme.primary,
                filled = true,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
