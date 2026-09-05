package io.lukia.shared.presentation.components.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.components.surface.GlassTone
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.Space

/**
 * The rows a menu is built from, on the glass it is drawn on.
 *
 * [AnchoredMenu] itself is a `Popup` and has no anchor to hang from in a
 * preview, so what is previewed is its contents on the same surface - which is
 * where the things that go wrong are anyway: a tick that does not line up with
 * the glyphs beside it, a destructive row in the wrong red.
 */
@Composable
private fun MenuGallery(dark: Boolean) {
    LukiaTheme(darkTheme = dark, touch = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(Space.xl)) {
                GlassSurface(shape = MaterialTheme.shapes.medium, tone = GlassTone.Panel) {
                    Column(modifier = Modifier.width(MenuWidth).padding(vertical = Space.xs)) {
                        MenuRow(
                            label = "Details",
                            onClick = {},
                            icon = { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) },
                        )

                        MenuSeparator()

                        MenuRow(
                            label = "Favourite",
                            onClick = {},
                            selected = true,
                            icon = { tint, glyph -> HeartGlyph(tint = tint, modifier = glyph) },
                        )

                        MenuRow(
                            label = "Delete",
                            onClick = {},
                            tint = MaterialTheme.colorScheme.error,
                            icon = { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) },
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun MenuGalleryLightPreview() = MenuGallery(dark = false)

@Preview
@Composable
private fun MenuGalleryDarkPreview() = MenuGallery(dark = true)

private val MenuWidth = 224.dp
