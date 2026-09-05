package io.lukia.shared.presentation.components.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.theme.LukiaTheme
import io.lukia.shared.presentation.theme.Space

/**
 * The surfaces a screen is assembled from, in both themes.
 *
 * [GroupSection] and [EmptyState] are here because they are the two the app
 * builds whole screens out of - settings, statistics, an empty shelf - and a
 * change to either one shows up in three features at once.
 */
@Composable
private fun SurfaceGallery(dark: Boolean) {
    LukiaTheme(darkTheme = dark, touch = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.xl),
                modifier = Modifier.padding(Space.xl).width(GalleryWidth),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    StatusBadge(text = "Missing")
                    StatusBadge(text = "EPUB", tone = BadgeTone.Neutral)
                    StatusBadge(text = "Finished", tone = BadgeTone.Accent)
                }

                GroupSection(
                    title = "Appearance",
                    footnote = "Applies to the app, not to the page you are reading.",
                ) {
                    Column {
                        Text(
                            text = "Palette",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        LukiaDivider(modifier = Modifier.padding(vertical = Space.md))

                        Text(
                            text = "Accent",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    EmptyState(
                        title = "No books yet",
                        message = "Add a file and it will show up here.",
                    ) {
                        LukiaButton(
                            label = "Import books",
                            onClick = {},
                            variant = ButtonVariant.Secondary,
                        )
                    }
                }

                GlassSurface(shape = MaterialTheme.shapes.medium, tone = GlassTone.Panel) {
                    Text(
                        text = "Chrome over a page",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Space.lg),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SurfaceGalleryLightPreview() = SurfaceGallery(dark = false)

@Preview
@Composable
private fun SurfaceGalleryDarkPreview() = SurfaceGallery(dark = true)

private val GalleryWidth = 360.dp
