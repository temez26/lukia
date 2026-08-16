package io.lukia.features.library.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.lukia.shared.presentation.components.AnchoredMenu
import io.lukia.shared.presentation.components.BOOK_COVER_ASPECT_RATIO
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.components.PlusGlyph
import io.lukia.shared.presentation.components.launchReveal
import io.lukia.shared.presentation.components.rememberHoverFraction
import io.lukia.shared.presentation.components.scaleOnPress
import io.lukia.shared.presentation.theme.LukiaMotion
import kotlinx.coroutines.launch

/** How far the cover swings open, in degrees about its spine. */
private const val OpenAngle = -34f

/**
 * The slot at the end of the library where a new book goes.
 *
 * A blank cover rather than a button: it sits in the grid among real covers, and
 * the thing it stands for is a book that is not there yet. Tapping it hinges it
 * open on its left edge - where a spine would be - and asks where the book should
 * come from, because there is more than one answer and picking for the reader
 * would be guessing.
 *
 * Menu state is owned by the screen rather than the card, so opening this closes
 * any book's menu and the bar's, exactly as those close each other.
 */
@Composable
internal fun ImportBookCard(
    menuExpanded: Boolean,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    reveal: LaunchReveal,
    revealIndex: Int,
    modifier: Modifier = Modifier,
    label: String? = "Add Books"
) {
    val scope = rememberCoroutineScope()
    val openAngle = remember { Animatable(0f) }

    val interactionSource = remember { MutableInteractionSource() }
    val hover = rememberHoverFraction(interactionSource)
    val shape = RoundedCornerShape(14.dp)

    // Warms to the accent colour under a pointer, confirming the card is a
    // control rather than an empty slot. Interpolated on the hover fraction so
    // the colour and the lift move together.
    val glyphTint = lerpColor(
        start = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        stop = MaterialTheme.colorScheme.primary,
        fraction = hover
    )

    Column(
        modifier = modifier
            .launchReveal(reveal, index = revealIndex)
            // The same inset a book card carries, so this slot's tile and caption
            // line up with its neighbours' rather than sitting a few pixels low.
            .padding(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .scaleOnPress(interactionSource, pressedScale = 0.95f)
                    .fillMaxWidth()
                    .aspectRatio(BOOK_COVER_ASPECT_RATIO)
                    .graphicsLayer {
                        rotationY = openAngle.value
                        val scale = 1f + 0.04f * hover
                        scaleX = scale
                        scaleY = scale
                        translationY = -8.dp.toPx() * hover
                        // Hinge on the left edge, where a book's spine would be.
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        // Without a wider camera distance the rotation reads as a
                        // horizontal squash instead of a swing.
                        cameraDistance = 16f * density
                    }
                    // Shadow grows in only on hover, so the resting state stays as
                    // flat as the placeholder it is.
                    .shadow(elevation = lerp(0.dp, 16.dp, hover), shape = shape, clip = false)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClickLabel = "Add books"
                    ) {
                        scope.launch {
                            openAngle.animateTo(OpenAngle, LukiaMotion.standard())
                            // Asked for mid-swing, so the cover is visibly open by
                            // the time the menu hangs off it.
                            onMenuRequest()
                            openAngle.animateTo(0f, LukiaMotion.bouncy())
                        }
                    }
            ) {
                PlusGlyph(tint = glyphTint, modifier = Modifier.size(28.dp))
            }

            // Anchored to the cover, so it hangs under this card the way a book's
            // menu hangs under that one.
            AnchoredMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss,
                minWidth = 196.dp
            ) {
                ImportMenuRows(
                    onDismissRequest = onMenuDismiss,
                    onImport = onImport,
                    onImportFolder = onImportFolder
                )
            }
        }

        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                // Matches the two lines a book's title reserves, so this card's
                // caption sits on the same line as its neighbours'.
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
