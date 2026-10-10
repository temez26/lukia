package io.lukia.features.library.presentation.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.controls.LukiaChip
import io.lukia.shared.presentation.components.controls.hoverHighlight
import io.lukia.shared.presentation.components.controls.scaleOnPress
import io.lukia.shared.presentation.components.icons.BackChevron
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.icons.FolderGlyph
import io.lukia.shared.presentation.components.icons.OpenBookGlyph
import io.lukia.shared.presentation.theme.Space

/** The mark at the head of a row, at the size a list wears rather than a button. */
private val RowGlyphSize: Dp = 18.dp

private val RowPadding: Dp = 10.dp

/** What stands between one folder of the path and the next. */
private const val PathSeparator = "\u203A"

/**
 * The places worth starting from, along the top of the browser.
 *
 * A row of chips rather than a sidebar: this panel is the width of a dialog, and
 * a column of shortcuts down one side of it would take a third of the room the
 * folder itself needs.
 */
@Composable
internal fun PlacesRow(
    places: List<BrowserEntry>,
    current: String,
    onGo: (BrowserEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        items(places, key = { it.path }) { place ->
            LukiaChip(
                label = place.name,
                selected = place.path == current,
                onClick = { onGo(place) },
            )
        }
    }
}

/**
 * Where the reader is, as the folders they came through.
 *
 * Each is a way back, which makes this a control rather than a caption: walking
 * out of four folders with a back button is four presses to undo one wrong turn.
 *
 * It scrolls sideways and is drawn from the deepest folder outwards, so the
 * folder being looked at is the one on screen when the path is longer than the
 * panel is wide.
 */
@Composable
internal fun PathBar(
    trail: List<BrowserEntry>,
    onGo: (BrowserEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = remember(trail) { trail.reversed() }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(steps, key = { it.path }) { step ->
            val last = step.path == trail.lastOrNull()?.path

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (last) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = !last, role = Role.Button) { onGo(step) }
                        .pointerHoverIcon(if (last) PointerIcon.Default else PointerIcon.Hand)
                        .padding(horizontal = Space.sm, vertical = Space.xs),
                )

                if (!last) {
                    // A written separator rather than a drawn chevron: the
                    // chevron this app has is the one a list row wears to say
                    // "there is more inside", and at the size of a word it reads
                    // as a caret pointing the wrong way.
                    Text(
                        text = PathSeparator,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One folder to go into, or one book to pick.
 *
 * A folder and a book are the same row with a different mark and a different
 * answer to being pressed, rather than two kinds of row: they sit in one list,
 * and a reader running down it should not have to read two layouts.
 */
@Composable
internal fun BrowserRow(
    entry: BrowserEntry,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = if (entry.isFolder) Role.Button else Role.Checkbox,
                onClick = onClick,
            )
            .hoverHighlight(interactionSource, MaterialTheme.shapes.medium)
            .scaleOnPress(interactionSource, pressedScale = 0.99f)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = Space.lg, vertical = RowPadding),
    ) {
        val tint = if (entry.isFolder) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        val glyph = Modifier.size(RowGlyphSize)

        if (entry.isFolder) FolderGlyph(tint, glyph) else OpenBookGlyph(tint, glyph)

        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // The tick for a book already chosen, and the chevron for a folder there
        // is more of. Only one of them can apply: a folder is not picked here,
        // it is opened.
        if (entry.isFolder) {
            // The back chevron turned round, which is what "further in" is. The
            // app's disclosure chevron points down, and down is where a row that
            // expands puts what it holds - this row goes somewhere instead.
            BackChevron(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(Space.xl)
                    .graphicsLayer { rotationZ = 180f },
            )
        } else if (picked) {
            CheckGlyph(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RowGlyphSize),
            )
        }
    }
}
