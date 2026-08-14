package io.lukia.shared.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One entry in the floating action menu. [icon] is handed the colour it should
 * draw itself in, so glyphs stay consistent with the surrounding text.
 */
data class QuickAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: (@Composable (Color) -> Unit)? = null
)

/**
 * A floating menu that expands into a stack of labelled actions. The trigger is
 * deliberately generic (a plus that rotates into a close) so new actions can be
 * added without the button implying a single specific one.
 *
 * The dimming scrim is not drawn here - it has to sit behind this whole stack
 * but in front of page content, so the caller places it in the content layer.
 * See [io.lukia.features.library.presentation.screens.LibraryContent].
 */
@Composable
fun ExpandableActionMenu(
    actions: List<QuickAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val triggerRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 220)
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        actions.forEach { action ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(160)) +
                    slideInVertically(tween(200)) { height -> height / 2 } +
                    scaleIn(tween(200), initialScale = 0.85f),
                exit = fadeOut(tween(120)) +
                    slideOutVertically(tween(160)) { height -> height / 2 } +
                    scaleOut(tween(160), targetScale = 0.85f)
            ) {
                ActionPill(
                    action = action,
                    onClick = {
                        // Collapse first so the menu is never left open behind
                        // whatever the action opens (a file dialog, a screen).
                        onExpandedChange(false)
                        action.onClick()
                    }
                )
            }
        }

        GlassSurface(shape = CircleShape) {
            Box(
                modifier = Modifier
                    .clickable(
                        onClickLabel = if (expanded) "Close actions" else "Open actions",
                        onClick = { onExpandedChange(!expanded) }
                    )
                    .size(58.dp),
                contentAlignment = Alignment.Center
            ) {
                PlusGlyph(
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(triggerRotation)
                )
            }
        }
    }
}

@Composable
private fun ActionPill(
    action: QuickAction,
    onClick: () -> Unit
) {
    GlassSurface(shape = CircleShape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            action.icon?.let { icon ->
                icon(MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Tap-anywhere-to-dismiss backdrop. Uses no ripple so it reads as a surface
 * rather than a button.
 */
@Composable
fun ActionMenuScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(140)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss
                )
        )
    }
}
