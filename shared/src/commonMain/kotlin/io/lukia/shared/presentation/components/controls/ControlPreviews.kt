package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.icons.SearchGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.Space

/**
 * The controls, in both themes.
 *
 * Every variant of a control in one frame rather than one preview each: what
 * these are for is catching the variant that stopped matching its neighbours -
 * a ghost button in the wrong tint, a disabled state that reads as enabled -
 * and that is a comparison, not a picture.
 *
 * Grouped in a file of their own so a component's own file stays the component.
 */
@Composable
private fun ControlGallery(dark: Boolean) {
    LukiaTheme(darkTheme = dark, touch = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.lg),
                modifier = Modifier.padding(Space.xl).width(GalleryWidth),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    LukiaButton(label = "Read", onClick = {})
                    LukiaButton(
                        label = "Import",
                        onClick = {},
                        variant = ButtonVariant.Secondary,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    LukiaButton(label = "More", onClick = {}, variant = ButtonVariant.Ghost)
                    LukiaButton(
                        label = "Delete",
                        onClick = {},
                        variant = ButtonVariant.Destructive,
                        icon = { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) },
                    )
                    LukiaButton(label = "Off", onClick = {}, enabled = false)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    LukiaIconButton(label = "Search", onClick = {}) { tint, glyph ->
                        SearchGlyph(tint = tint, modifier = glyph)
                    }
                    LukiaIconButton(
                        label = "Delete",
                        onClick = {},
                        variant = IconButtonVariant.Filled,
                    ) { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) }
                    LukiaIconButton(
                        label = "Search",
                        onClick = {},
                        variant = IconButtonVariant.Glass,
                    ) { tint, glyph -> SearchGlyph(tint = tint, modifier = glyph) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    LukiaChip(label = "Fiction", selected = true, onClick = {})
                    LukiaChip(label = "Unread", selected = false, onClick = {})
                    LukiaChip(label = "Sort", selected = false, onClick = {}, chevron = true)
                }

                ChoiceControls()
            }
        }
    }
}

/**
 * The controls that stand for a choice rather than an action, and the two the
 * app draws over artwork.
 *
 * Split out of [ControlGallery] to keep it readable rather than because they
 * belong apart; they are drawn in the same frame.
 */
@Composable
private fun ChoiceControls() {
    SegmentedControl(
        options = listOf("Grid", "List"),
        selected = "Grid",
        label = { it },
        onSelect = {},
    )

    ChoiceList(
        options = listOf("Crisp", "Paper", "Slate"),
        selected = "Paper",
        label = { it },
        onSelect = {},
        detail = { if (it == "Paper") "Warm, for reading by lamp" else null },
    )

    ReadingProgressBar(fraction = 0.42f)

    ReadingProgressBadge(percent = 42)
}

@Preview
@Composable
private fun ControlGalleryLightPreview() = ControlGallery(dark = false)

@Preview
@Composable
private fun ControlGalleryDarkPreview() = ControlGallery(dark = true)

/** Wide enough for the segmented control to show both of its segments. */
private val GalleryWidth = 320.dp
