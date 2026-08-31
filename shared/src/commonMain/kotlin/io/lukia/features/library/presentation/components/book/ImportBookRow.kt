package io.lukia.features.library.presentation.components.book

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.library.presentation.components.toolbar.ImportMenuRows
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.controls.hoverHighlight
import io.lukia.shared.presentation.components.controls.rememberHoverFraction
import io.lukia.shared.presentation.components.icons.PlusGlyph
import io.lukia.shared.presentation.components.launch.LaunchReveal
import io.lukia.shared.presentation.components.launch.launchReveal
import io.lukia.shared.presentation.components.menu.AnchoredMenu
import io.lukia.shared.presentation.theme.HairlineWidth
import io.lukia.shared.presentation.theme.Space
import androidx.compose.ui.graphics.lerp as lerpColor

/** The thumbnail's size, matching the jacket a book's row carries. */
private val RowSlotWidth: Dp = 44.dp

private val RowSlotHeight: Dp = 64.dp

/**
 * The slot at the end of a library laid out as a list.
 *
 * The same thing as [ImportBookCard] and deliberately not the same shape. A card
 * is a blank cover among covers, which is what the end of a grid should look
 * like; drawn in a list, where a row is one column wide, that cover was as wide
 * as the window and half again as tall - one enormous empty rectangle under the
 * books, which is what this replaces.
 *
 * So it is a row among rows: a blank where the jacket goes, and beside it the
 * two words a book's title would be on. The cover does not hinge open here -
 * there is no spine to hinge on at this size, and a swing that small reads as a
 * flicker rather than as a book being opened.
 */
@Composable
internal fun ImportBookRow(
    menuExpanded: Boolean,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onImport: () -> Unit,
    onImportFolder: () -> Unit,
    reveal: LaunchReveal,
    revealIndex: Int,
    modifier: Modifier = Modifier,
    label: String = "Add Books",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hover = rememberHoverFraction(interactionSource)
    val shape = MaterialTheme.shapes.medium

    // Warms to the accent colour under a pointer, confirming the row is a
    // control rather than an empty slot - the same as the card's does.
    val glyphTint = lerpColor(
        start = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        stop = MaterialTheme.colorScheme.primary,
        fraction = hover,
    )

    Box(modifier = modifier.launchReveal(reveal, index = revealIndex)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .hoverHighlight(interactionSource, shape)
                .focusRing(interactionSource, shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Add books",
                    onClick = onMenuRequest,
                )
                // The same inset a book's row is drawn inside, so the two line
                // up down their left edge rather than by a few pixels.
                .padding(Space.sm),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(RowSlotWidth, RowSlotHeight)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = HairlineWidth,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    ),
            ) {
                PlusGlyph(tint = glyphTint, modifier = Modifier.size(18.dp))
            }

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.md),
            )
        }

        // Hung off the row itself, so it opens where the finger was - the same
        // way a book's menu does in this layout.
        AnchoredMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
            ImportMenuRows(
                onDismissRequest = onMenuDismiss,
                onImport = onImport,
                onImportFolder = onImportFolder,
            )
        }
    }
}
